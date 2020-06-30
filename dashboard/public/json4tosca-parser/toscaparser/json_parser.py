
import json
from toscaparser import functions

PACKAGE_MAPPING = { 
   "tosca.nodes.WebApplication"        : "wa", 
   "tosca.nodes.DBMS"                  : "dbms", 
   "tosca.nodes.WebServer"             : "ws", 
   "tosca.nodes.Database"              : "db",
   "tosca.nodes.SoftwareComponent"     : "sc",
   "tosca.nodes.LoadBalancer"          : "lb"
}


RESOURCE_MAPPING = { 
   "tosca.nodes.Compute"       : "vm",
   "tosca.nodes.ObjectStorage" : "obj_store", 
   "tosca.nodes.BlockStorage"  : "block_store"
}

# python tosca_parser.py --template-file=toscaparser/tests/data/tosca_container_wordpress.yaml

class ConfigFileUtility(object):

   CONTAINER_TYPES = ( RUNTIME, APPLICATION ) = "tosca.nodes.Container.Runtime", "tosca.nodes.Container.Application" 
   CONTAINER_REQUIREMENTS = ( HOST, VOLUME ) = "host", "volume"

   def __init__(self, *args, **kwargs):
      super().__init__(*args, **kwargs)

   def serialise_deployment_units(self, deployment_units, inputs):
      serialised_deployment_units = {}

      requirements_to_assign = dict()
      
      # For each deployment unit (runtime) create a serialised instance
      for deployment_name, deployment in deployment_units.items():
         # TODO : do we have any property for the runtime?
         serialised_deployment_units[deployment_name] = { "name" : deployment_name, "type": "du", "requirements":
         {
            "create" : [],
            "configure" : []
         }, "containers" : [], "properties" : {}}
         # 0 is always a Container.Runtime
         runtime_node = deployment.pop(0)
         # For each container in the deployment, create a container instance to append in the container section of the deployment
         for container in deployment:
            # print([x.name for x in container])
            # 0 is always a Container.Application
            container_node = container.pop(0)

            # Instantiate the node_dict dictionary with basics info
            node_dict = { 
               "name"         : container_node.name,
               "category"     : None,
               "image"        : None,
               "volumes"      : [],
               #"ext_requirements" : {},
               "properties"   : {},
               "configuration_script" : "no.configuration.script",  
            }

            # TODO: generalize 
            if "Container.WebApplication" in container_node.type:
               node_dict["category"] = "wa"
            elif "Container.Database" in container_node.type:
               node_dict["category"] = "db"
            else:
               node_dict["category"] = "unk"
               
            # Get interface inputs
            if(hasattr(container_node, 'interfaces')):
               for interface in container_node.interfaces:
                  for key, value in interface.inputs.items():
                     if isinstance(value, functions.GetInput):
                        node_dict["properties"][key] = value.result()
                     if isinstance(value, functions.GetProperty):
                        node_dict["properties"][key] = value.result()
                     if isinstance(value, str):
                        node_dict["properties"][key] = value

            if(hasattr(container_node, 'artifacts')):
               for artifact in container_node.artifacts:
                  # TODO: can we have multiple artifacts?
                  if len(container_node.artifacts) == 1:
                     node_dict["image"] = artifact["file"]

            for requirement in container_node.requirements:
               # Search dependencies
               for req_name, req_value in requirement.items():
                  # We skip it, since we are already inside the deployment object. We don't need extra information
                  if req_name == self.HOST:
                     continue
                  if req_name == self.VOLUME:
                     # Volume found -> Create the volume in the container
                     volume_dicts = self.serialise_node_templates(list(container_node._get_explicit_relationship(requirement, req_value).values()), inputs)
                     for key, volume_dict in volume_dicts.items():
                        volume_dict.pop("requirements")
                        volume_dict.pop("type")
                     
                        # Get Relationship properties (i.e. 'location')
                        if(hasattr(container_node, 'relationship_tpl')):
                           for req_key in container_node._get_explicit_relationship(requirement, req_value).keys():
                              for relationship in container_node.relationship_tpl:
                                 # requirement["volume"]["relationship"]["node"] == volume_dict["name"] and 
                                 if requirement["volume"]["relationship"] == relationship.name:
                                    if('properties' in relationship.entity_tpl):
                                       for key, value in relationship.entity_tpl["properties"].items():
                                          # Get inputs
                                          if("get_input" in value):
                                             for input in inputs:
                                                if(isinstance(value, dict) and value["get_input"] == input.name):
                                                   volume_dict["properties"][key] = input.schema.default 
                                          else:
                                                   volume_dict["properties"][key] = value 
                     node_dict["volumes"]= list(volume_dicts.values())

                  else:
                     # All the other requirements -> Ext Requirements of the container
                     for key, value in container_node._get_explicit_relationship(requirement, req_value).items():
                        # node_dict["ext_requirements"][req_name] = value.name
                        # Search DU and put in requirements
                        if deployment_name in requirements_to_assign: 
                           requirements_to_assign[deployment_name][req_name] = value.name
                        else:
                           requirements_to_assign[deployment_name] = {req_name: value.name}

                        if req_name not in  node_dict["properties"]:
                           node_dict["properties"][req_name] = value.name
                        # TODO: do we need informations other than these?

            # Analyzes node Properties from Capabilities
            capabilities   = container_node.get_capabilities()
            port_found = False
            for key, cap in capabilities.items(): 
               if "tosca.capabilities.Endpoint" in cap.definition.type:
                  duPort = cap.get_property_value('port')
                  if "port" in node_dict["properties"]:
                     port_found = True
                     node_dict["properties"]["port"] = str(duPort) + ":" + str(node_dict["properties"]["port"])
                  else:
                     node_dict["properties"]["port"] = str(duPort) + ":" + str(duPort)
            if port_found == False and "port" in node_dict["properties"]:
               port = node_dict["properties"]["port"]
               node_dict["properties"]["port"] = str(port) + ":" + str(port)
            
            serialised_deployment_units[deployment_name]["containers"].append(node_dict)

      for du_name, ext_requirements in requirements_to_assign.items():
         for req_name, value_name in ext_requirements.items():
            for deployment_name, deployment in serialised_deployment_units.items():
               for c in deployment["containers"]:
                  if c["name"] == value_name:
                     serialised_deployment_units[du_name]["requirements"]["create"].append(deployment_name + ".configure")
                     for container in serialised_deployment_units[du_name]["containers"]:
                        if req_name in container["properties"]:
                           container["properties"][req_name] = container["properties"][req_name].replace(value_name, deployment_name)

      return serialised_deployment_units


   def serialise_node_templates(self, node_templates, inputs):
      serialised_nodes = {}
      for node in node_templates:

         nodeType = nodeCategory = "unknown"

         # Check whether is a resource or a package node
         for resource, category in RESOURCE_MAPPING.items():
            if(resource in node.type):
               nodeType, nodeCategory = ["resource", category]
               break


         for package, category in PACKAGE_MAPPING.items():
            if(package in node.type):
               nodeType = "package"
               nodeCategory = category
               break

         # Instantiate the node_dict dictionary with basics info
         node_dict = { 
            "name"         : node.name,
            "type"         : nodeType,
            "category"     : nodeCategory,
            "requirements" : { 
                  "create"    : [], 
                  "configure" : [], 
                  "start"     : [] 
            },
            "properties"   : {}  
         }

         # Analyzes node Properties
         entity = node.entity_tpl   
         if ( 'properties' in entity ): 
            properties   = entity["properties"]
            for key, value in properties.items():
               # Get inputs
               if("get_input" in value):
                  for input in inputs:
                     if(isinstance(value, dict) and value["get_input"] == input.name):
                        node_dict["properties"][key.replace("_",".")] = input.schema.default 
               else:
                  node_dict["properties"][key.replace("_",".")] = value 
         
         # Analyzes node Properties from Capabilities
         if ( 'capabilities' in entity ): 
            capabilities   = entity["capabilities"]
            for key, propDict in capabilities.items(): 
               capabilityType = key
               # Cicling properties of the property item
               for kp, vp in propDict["properties"].items():
                  property_key = capabilityType + "." + kp
                  # Get inputs
                  if(isinstance(vp,dict) and "get_input" in vp):
                     for input in inputs:
                        if(vp["get_input"] == input.name):
                           # TODO: instead of reading default the user should input a value -> or make it clear that default is took
                           node_dict["properties"][property_key.replace("_",".")] = input.schema.default 
                  else: 
                     node_dict["properties"][property_key.replace("_",".")] = vp 

         # Fake property to ensure validation of JSON by Flowable
         if(node_dict["properties"] == {}):
            node_dict["properties"]["not.a.property"] = "not.a.property"               
         
         # Writes the node informationss
         serialised_nodes[node_dict["name"]] = node_dict
      return serialised_nodes


   def check_hierarchy_type(self, node_type, n_type):
      # Recursively checks the types' hierarchy
      if node_type == None:
         return False
      elif node_type.type == n_type:
         return True
      else:   
         return self.check_hierarchy_type(node_type.parent_type, n_type)

   def nodes_deployments_split(self, node_templates):
      filtered_nodes = node_templates.copy()
      deployment_units = {}

      # Find the runtimes -> Deployment units
      for node in node_templates:
         if node.type == self.RUNTIME or self.check_hierarchy_type(node.parent_type, self.RUNTIME):
            filtered_nodes = [ x for x in filtered_nodes if x.name is not node.name ]
            deployment = [node]      
            deployment_units[node.name] = deployment

      # Find all the Containers and append them to the correspondent Runtime list
      for node in node_templates:
         if node.type == self.APPLICATION or self.check_hierarchy_type(node.parent_type, self.APPLICATION):
            filtered_nodes = [ x for x in filtered_nodes if x.name is not node.name ]
            container = [node]      
            for requirement in node.requirements:
            # Search dependencies
               for req_name, req_value in requirement.items():
                  if req_name == self.HOST:
                     deployment_unit = req_value
                  elif req_name == self.VOLUME:
                     # Volume found -> Remove the node from the nodetemplates and add to the container
                     for key, n_node in node._get_explicit_relationship(requirement, req_value).items():
                        filtered_nodes = [ x for x in filtered_nodes if x.name != n_node.name ]
                        container.append(n_node)
                  else:
                     # All the other requirements -> Add the node to the container
                     for key, n_node in node._get_explicit_relationship(requirement, req_value).items():
                           container.append(n_node)
            # Add the container to the deployment units
            deployment_units[deployment_unit].append(container)                    
      return filtered_nodes, deployment_units


   def generate_json(self, tosca):

      graph_dict = {}

      if hasattr(tosca, 'inputs'):
         inputs = tosca.inputs


      # TODO: set everything to do with result() function and add case for default string

      if hasattr(tosca, 'nodetemplates'):
         node_templates, deployment_units = self.nodes_deployments_split(node_templates = tosca.nodetemplates)
         graph_dict["node_templates"] = self.serialise_node_templates(node_templates, inputs)
         graph_dict["deployment_units"] = self.serialise_deployment_units(deployment_units, inputs)
         
         # Analyzes node requirements
         if hasattr(tosca.graph, 'requirements'):
            # TODO: analyse them in the nodes
            # Custom property in tosca.graph
            requirements = tosca.graph.requirements

            # Removing duplicates from requirements, because the values about the node itself
            # are already present (i.e. the sequence create -> configure -> start)
            for source, targetsList in requirements.items():
               for target in targetsList:
                  if target.replace("_create","").replace("_configure","").replace("_start","") not in source:
                     ns = source.replace("_create","").replace("_configure","").replace("_start","")
                     plain_target = target.replace("_create","").replace("_configure","").replace("_start","")
                     if plain_target in graph_dict["node_templates"]:
                        target = target.replace("_create",".create").replace("_configure",".configure").replace("_start",".start")
                     elif plain_target in graph_dict["deployment_units"]:
                        target = target.replace("_create",".configure").replace("_configure",".configure").replace("_start",".configure")
                     if ns in graph_dict["node_templates"]:
                        graph_dict["node_templates"][ns]["requirements"][source.replace(ns+"_", "")].append(target)
                     elif ns in graph_dict["deployment_units"]:
                        graph_dict["deployment_units"][ns]["requirements"]["create"].append(target)


         # with open("output.json", "w") as write_file:
         #    json.dump([v for v in {**graph_dict["node_templates"], **graph_dict["deployment_units"]}.values()], write_file)
         print(json.dumps([v for v in {**graph_dict["node_templates"], **graph_dict["deployment_units"]}.values()]), end="")
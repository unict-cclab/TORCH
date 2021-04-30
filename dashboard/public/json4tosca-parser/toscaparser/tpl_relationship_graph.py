
class ToscaGraph(object):
    '''Graph of Tosca Node Templates.'''
    def __init__(self, nodetemplates):
        self.nodetemplates = nodetemplates
        self.vertices = {}
        self._requirements = {}
        self._basicrequirements = {}
        self._nodes = []
        self._create()

    def _create_vertex(self, node):
        if node not in self.vertices:
            self.vertices[node.name] = node

    def _create_edge(self, node1, node2, relationship):
        if node1 not in self.vertices:
            self._create_vertex(node1)
        self.vertices[node1.name]._add_next(node2,
                                            relationship)

    def vertex(self, node):
        if node in self.vertices:
            return self.vertices[node]

    def __iter__(self):
        return iter(self.vertices.values())

    def _create(self):
        for node in self.nodetemplates:
            relation = node.relationships
            if relation:
                for rel, nodetpls in relation.items():
                    for tpl in self.nodetemplates:
                        if tpl.name == nodetpls.name:
                            self._create_edge(node, tpl, rel)
            self._create_vertex(node)

    ############## Aggiunta proprieta' per la creazione del dizionario dei requisiti ######
    @property
    def basicrequirements(self):
        if not self._basicrequirements:
            vertices = self.vertices
             
            for vertex_key, vertex_value in vertices.items():
                nodeRequirementList = []
                for r_key, r_value in vertex_value.relationships.items():
                    nodeRequirementList.append((r_value.name, r_key.type))
                if nodeRequirementList:
                    self._basicrequirements[vertex_key] = nodeRequirementList

        return self._basicrequirements

    @property
    def requirements(self):
        if not self._requirements:
            for req in self.basicrequirements:
                # For single requirement
                if len(self._basicrequirements[req]) == 1:
                    # create
                    if self._basicrequirements[req][0][0] in self._basicrequirements.keys():
                        self._requirements[req + "_create"] = [self._basicrequirements[req][0][0] + "_start"]
                    else:
                        self._requirements[req + "_create"] = [self._basicrequirements[req][0][0] + "_create"]
                    # configure
                    if self._requirements.__contains__(req + "_configure"):
                        self._requirements[req + "_configure"].append(req + "_create")
                    else:
                        self._requirements[req + "_configure"] = [req + "_create"]
                    # start
                    self._requirements[req + "_start"] = [req + "_configure"]
                else:
                    # create
                    nodeRequirementList = self._basicrequirements[req]
                    for nodereq in nodeRequirementList:
                        required_from_List = []
                        nodecreate = ""
                        for node in self._basicrequirements:
                            for nd in self._basicrequirements[node]:
                                if nd[0] == nodereq[0]:
                                    required_from_List.append(node)
                        if req in required_from_List and "HostedOn" in nodereq[1]:
                            self._requirements[req + "_create"] = [nodereq[0] + "_start"]
                            nodecreate = nodereq[0]
                            break
                    # configure
                    reqlist = []
                    for nodereq in nodeRequirementList:
                        if nodereq[0] == nodecreate:
                            reqlist.append(req + "_create")
                        elif nodereq[0] not in self._basicrequirements.keys():
                            reqlist.append(nodereq[0] + "_create")
                        else:
                            reqlist.append(nodereq[0] + "_start")
                            if self._requirements.__contains__(req + "_configure"):
                                self._requirements[req + "_configure"].append(nodereq[0] + "_create")
                            else:
                                self._requirements[req + "_configure"] = [nodereq[0] + "_create"]

                    self._requirements[req + "_configure"] = reqlist
                    # start
                    self._requirements[req + "_start"] = [req + "_configure"]
            
        return self._requirements
        
    @property
    def nodes(self):
        if not self._nodes:
            for node in self.nodetemplates:
                if node.name not in self.basicrequirements:
                    self._nodes.append(node.name)
                else:
                    self._nodes.append(node.name + '_create')
                    self._nodes.append(node.name + '_configure')
                    self._nodes.append(node.name + '_start')

        return self._nodes

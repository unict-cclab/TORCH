<template>
    <div>
        <div id="toolbar">
            <!-- <form @submit.prevent="addNode">
                <input type="text" class="form-control" name="name" v-model="nodeForm__name" id="" placeholder="Node name" required>
                <select class="custom-select" v-model="nodeForm__type" name="type" id="" required>
                    <option value="tosca.nodes.Root">tosca.nodes.Root</option>
                    <option value="tosca.nodes.Compute">tosca.nodes.Compute</option>
                    <option value="tosca.nodes.SoftwareComponent">tosca.nodes.SoftwareComponent</option>
                    <option value="tosca.nodes.WebServer">tosca.nodes.WebServer</option>
                    <option value="tosca.nodes.WebApplication">tosca.nodes.WebApplication</option>
                    <option value="tosca.nodes.DBMS">tosca.nodes.DBMS</option>
                    <option value="tosca.nodes.Database">tosca.nodes.Database</option>
                    <option value="tosca.nodes.Storage.ObjectStorage">tosca.nodes.Storage.ObjectStorage</option>
                    <option value="tosca.nodes.Storage.BlockStorage">tosca.nodes.Storage.BlockStorage</option>
                    <option value="tosca.nodes.Container.Runtime">tosca.nodes.Container.Runtime</option>
                    <option value="tosca.nodes.Container.Application">tosca.nodes.Container.Application</option>
                    <option value="tosca.nodes.LoadBalancer">tosca.nodes.LoadBalancer</option>
                </select>
                <input class="btn btn-link" type="submit" value="Add node" />
            </form> -->
            <button @click="showNodeModal" class="btn btn-outline-primary">Add Node</button>
            <button @click="showRelationshipModal" class="btn btn-outline-success">Add Relationship</button>
            <button @click="generateYAML" class="btn btn-outline-dark">Generate YAML</button>
        </div>
        <div style="height: 80vh; border: 1px solid #777">
            <div ref="tosca-graph-modeler" style="width: 100%; height: 100%; display: block"></div>
        </div>
    
        <!-- Relationship Modal -->
        <div class="modal fade" id="relationship-modal" tabindex="-1" role="dialog" aria-labelledby="exampleModalLabel" aria-hidden="true">
        <div class="modal-dialog" role="document">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="exampleModalLabel">Add new relationship</h5>
                <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                <span aria-hidden="true">&times;</span>
                </button>
            </div>
            <div class="modal-body">
                <form @submit.prevent="addRelationship">
                    <input class="form-control" type="text" name="name" v-model="relForm__name" id="" placeholder="Relationship name" required>
                    <select class="custom-select" v-model="relForm__source" name="source" id="">
                        <option v-for="node in nodes" :key="node.data.id" :value="node.data.id">{{ node.data.id }}</option>
                    </select>
                    <br />
                    <select class="custom-select" v-model="relForm__type" name="type" id="">
                        <option value="tosca.relationships.Root">tosca.relationships.Root</option>
                        <option value="tosca.relationships.DependsOn">tosca.relationships.DependsOn</option>
                        <option value="tosca.relationships.HostedOn">tosca.relationships.HostedOn</option>
                        <option value="tosca.relationships.ConnectsTo">tosca.relationships.ConnectsTo</option>
                        <option value="tosca.relationships.AttachesTo">tosca.relationships.AttachesTo</option>
                        <option value="tosca.relationships.RoutesTo">tosca.relationships.RoutesTo</option>
                    </select>
                    <select class="custom-select" v-model="relForm__target" name="target" id="">
                        <option v-for="node in nodes" :key="node.data.id" :value="node.data.id">{{ node.data.id }}</option>
                    </select>
                    <input class="btn btn-primary" type="submit" value="Add relationship">
                </form>
            </div>
        </div>
        </div>
        </div>

        <!-- Node Modal -->
        <div class="modal fade" id="node-modal" tabindex="-1" role="dialog" aria-labelledby="exampleModalLabel" aria-hidden="true">
        <div class="modal-dialog" role="document">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="exampleModalLabel">Add new node</h5>
                <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                <span aria-hidden="true">&times;</span>
                </button>
            </div>
            <div class="modal-body">
                <form @submit.prevent="addNode">
                    <input type="text" class="form-control" name="name" v-model="nodeForm__name" id="" placeholder="Node name" required>
                    <select class="custom-select" v-model="nodeForm__type" name="type" id="" required>
                        <option value="tosca.nodes.Root">tosca.nodes.Root</option>
                        <option value="tosca.nodes.Compute">tosca.nodes.Compute</option>
                        <option value="tosca.nodes.SoftwareComponent">tosca.nodes.SoftwareComponent</option>
                        <option value="tosca.nodes.WebServer">tosca.nodes.WebServer</option>
                        <option value="tosca.nodes.WebApplication">tosca.nodes.WebApplication</option>
                        <option value="tosca.nodes.DBMS">tosca.nodes.DBMS</option>
                        <option value="tosca.nodes.Database">tosca.nodes.Database</option>
                        <option value="tosca.nodes.Storage.ObjectStorage">tosca.nodes.Storage.ObjectStorage</option>
                        <option value="tosca.nodes.Storage.BlockStorage">tosca.nodes.Storage.BlockStorage</option>
                        <option value="tosca.nodes.Container.Runtime">tosca.nodes.Container.Runtime</option>
                        <option value="tosca.nodes.Container.Application">tosca.nodes.Container.Application</option>
                        <option value="tosca.nodes.LoadBalancer">tosca.nodes.LoadBalancer</option>
                    </select>
                    <input class="btn btn-primary" type="submit" value="Add node" />
                </form>
            </div>
        </div>
        </div>
        </div>

        <!-- Node Edit Modal -->
        <div class="modal fade" id="node-edit-modal" tabindex="-1" role="dialog" aria-labelledby="exampleModalLabel" aria-hidden="true">
        <div class="modal-dialog" role="document">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="exampleModalLabel">Edit  node</h5>
                <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                <span aria-hidden="true">&times;</span>
                </button>
            </div>
            <div class="modal-body">    
                TODO
            </div>
        </div>
        </div>
        </div>

    </div>

    
</template>

<script>
import cytoscape from 'cytoscape';
import YAML from 'yaml';

let cy;

export default {
    data: () => 
    ({
        nodeForm__name: "",
        nodeForm__type: "",
        relForm__name: "",
        relForm__source: "",
        relForm__type: "",
        relForm__target: "",
        selected : "",
        nodes: [],
        edges: []
    }),
    mounted() {

        const container = this.$refs["tosca-graph-modeler"];
        
        let roots = [];
        const layout = {
            name: 'breadthfirst',
            fit: true, // whether to fit the viewport to the graph
            directed: false, // whether the tree is directed downwards (or edges can point in any direction if false)
            padding: 10, // padding on fit
            circle: false, // put depths in concentric circles if true, put depths top down if false
            grid: false, // whether to create an even grid into which the DAG is placed (circle:false only)
            spacingFactor: 1, // positive spacing factor, larger => more space between nodes (N.B. n/a if causes overlap)
            boundingBox: undefined, // constrain layout bounds; { x1, y1, x2, y2 } or { x1, y1, w, h }
            avoidOverlap: true, // prevents node overlap, may overflow boundingBox if not enough space
            nodeDimensionsIncludeLabels: false, // Excludes the label when calculating node bounding boxes for the layout algorithm
            roots: roots.join(","), // the roots of the trees
            maximal: true, // whether to shift nodes down their natural BFS depths in order to avoid upwards edges (DAGS only)
            animate: false, // whether to transition the node positions
            animationDuration: 500, // duration of animation in ms if enabled
            animationEasing: undefined, // easing of animation if enabled,
            animateFilter: function ( node, i ){ return true; }, // a function that determines whether the node should be animated.  All nodes animated by default on animate enabled.  Non-animated nodes are positioned immediately when the layout starts
            ready: undefined, // callback on layoutready
            stop: undefined, // callback on layoutstop
            transform: function (node, position ){ return position; } // transform a given node position. Useful for changing flow direction in discrete layouts
        };
        
        const style = [ // the stylesheet for the graph
            {
                selector: 'node',
                style: {
                'background-color': '#ffffff', 
                'border-width': 2, 
                'border-color': '#000000',
                'label': 'data(id)',
                'text-valign': 'center',
                'shape': 'ellipse',
                'width': 'label',
                'height': 'label',
                'padding': '60px',
                'background-width' : '30px',
                'background-height' : '30px',
                'background-position-x' : '15px',
                'font-size' : '1.5em'
                }
            },
            {
                selector: 'edge',
                style: {
                    'curve-style': 'taxi',
                    'taxi-direction' : 'vertical',
                    'line-style' : 'dashed',
                    'width': 7,
                    'line-color': '#777',
                    'target-arrow-color': '#777',
                    'target-arrow-shape': 'triangle'
                }
            },
            { selector: '.package', style: { 'background-color': '#03a9f4' } },
            { selector: '.resource', style: { 'background-color': '#4caf50' } },
            { selector: '.db', style: { 'background-image': '/img/database-512.png' } },
            { selector: '.dbms', style: { 'background-image': '/img/dbms-512.png' } },
            { selector: '.wa', style: { 'background-image': '/img/wa-512.png' } },
            { selector: '.sc', style: { 'background-image': '/img/sc.png' } },
            { selector: '.vm', style: { 'background-image': '/img/vm-512.png' } },
            { selector: '.ws', style: { 'background-image': '/img/server-512.png' } }
        ];

        cy = cytoscape({
            container, // container to render in
            style,
            layout,
            wheelSensitivity: 0.05,

        });
      
        cy.addNode = (node) =>
        {
            cy.add(node);
            this.nodes.push(node);
        } 

        cy.addEdge = (edge) => {
            cy.add(edge);
            this.edges.push(edge);
        } 

        cy.on('click', 'node', function(evt){
            $('#node-edit-modal').modal('show');
        });

        cy.on('cxttap', 'node', function(evt){
            this.selected = evt.target.id();
        });
    },
    methods:
    {
        addNode(evt)
        {
            const newNode = { group: 'nodes', data: { id: this.nodeForm__name, type: this.nodeForm__type  } };
            cy.addNode(newNode);
            cy.center();
        },
        showRelationshipModal()
        {
            $('#relationship-modal').modal('show');
        },
        showNodeModal()
        {
            $('#node-modal').modal('show');
        },
        addRelationship()
        {
            const newEdge = { group: 'edges', data: { id: this.relForm__name, source: this.relForm__source, target: this.relForm__target, type: this.relForm__type} };
            cy.addEdge(newEdge);
            cy.center();
        },
        generateYAML()
        {
            let toscaTemplate = 
            {
                tosca_definitions_version: "tosca_simple_yaml_1_0",
                description: "Nothing to say",
                topology_template:
                {
                    node_templates: {}
                }
            }

            for(let node of this.nodes)
            {
                toscaTemplate.topology_template.node_templates[node.data.id] = {
                    type: node.data.type,
                    // attributes:   {},
                    // capabilities: {},
                    requirements: [],
                    // interfaces:   {}
                }
            }

            for(let edge of this.edges)
            {
                toscaTemplate.topology_template.node_templates[edge.data.source].requirements.push(
                    {
                        dependency: edge.data.target
                    }
                ) 
            }
            
            console.log(YAML.stringify(toscaTemplate));
        }
    }
}
</script>

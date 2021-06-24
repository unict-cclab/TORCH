<template>
    <div id="tosca-graph" style="width: 100%; height: 100%; display: block"></div>
</template>

<script>
import cytoscape from 'cytoscape';

export default {
    props: {
        json_graph: {
            type: String,
            required: true
        }
    },
    mounted() {

        const json = JSON.parse(this.json_graph);
        const container = document.getElementById("tosca-graph");
        
        let nodes = [], edges = [], roots = [];
        for(let toscaNode of json)
        {
            let classList = [toscaNode.category, toscaNode.type]
            if(toscaNode.containers)
            {
                toscaNode.containers.forEach( c => {
                    classList.push(c.category)
                })
            }
            nodes.push({
                data: 
                {
                    id: toscaNode.name,
                },
                classes: classList
            });
            
            if(toscaNode.type.localeCompare("resource") === 0) roots.push("#"+toscaNode.name);
            
            for(let key in toscaNode.requirements)
            {
                for(let requirement of toscaNode.requirements[key])
                {
                    const source = requirement.replace(".create", "").replace(".configure", "").replace(".start", "");
                    const target = toscaNode.name;
                    edges.push({
                        data:{
                            id: source + "->" + target,
                            source,
                            target
                        }
                    });
                }
            }
        }
        
        const layout = {
            name: 'breadthfirst',
            fit: true, // whether to fit the viewport to the graph
            directed: true, // whether the tree is directed downwards (or edges can point in any direction if false)
            padding: 0, // padding on fit
            circle: false, // put depths in concentric circles if true, put depths top down if false
            grid: true, // whether to create an even grid into which the DAG is placed (circle:false only)
            spacingFactor: 1, // positive spacing factor, larger => more space between nodes (N.B. n/a if causes overlap)
            boundingBox: undefined, // constrain layout bounds; { x1, y1, x2, y2 } or { x1, y1, w, h }
            avoidOverlap: true, // prevents node overlap, may overflow boundingBox if not enough space
            nodeDimensionsIncludeLabels: false, // Excludes the label when calculating node bounding boxes for the layout algorithm
            roots: undefined, // the roots of the trees
            maximal: false, // whether to shift nodes down their natural BFS depths in order to avoid upwards edges (DAGS only)
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
                'background-color': '#bc3',
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
            { selector: '.du', style: { 'background-color': '#ffffff', 'border-width': 2, 'border-color': '#000000' } },
            { selector: '.resource', style: { 'background-color': '#4caf50' } },
            { selector: '.db', style: { 'background-image': '/img/database-512.png' } },
            { selector: '.dbms', style: { 'background-image': '/img/dbms-512.png' } },
            { selector: '.wa', style: { 'background-image': '/img/wa-512.png' } },
            { selector: '.sc', style: { 'background-image': '/img/sc.png' } },
            { selector: '.vm', style: { 'background-image': '/img/vm-512.png' } },
            { selector: '.ws', style: { 'background-image': '/img/server-512.png' } }
        ];

        const cy = cytoscape({
            container, // container to render in
            elements: { nodes, edges },
            style,
            layout,
            wheelSensitivity: 0.05,

        });
    }
}
</script>

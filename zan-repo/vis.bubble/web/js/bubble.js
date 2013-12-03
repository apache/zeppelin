function Bubble(data, config){
    this.data = data;
    this.config = config; //  { width:600, height:600, gravity:0.12, damper : 0.1, friction: 0.9, col : 3 }
    this.dataIndex = {
	name : 0,
	value : 1,
	group : 2,
	color : 3
    };
    this.maxValue = 0;
    this.groups = {};
    

    for(var i=0; i<this.data.length; i++){
	var d = this.data[i];
	this.maxValue = Math.max(this.maxValue, parseInt(d[this.dataIndex.value]));
	if(this.groups[d[this.dataIndex.group]]==undefined){
	    this.groups[d[this.dataIndex.group]]=0;
	}
	this.groups[d[this.dataIndex.group]]++;
    }

    this.display = function(target){
	// create svg
	var vis = d3.select(target).append("svg")
	                           .attr("width", config.width)
	                           .attr("height", config.height)
	                           .attr("id", "svg_vis")
	this.vis = vis;

	// create nodes
	var nodes = [];
	for(var i=0; i<data.length; i++){
	    var d = data[i];
	    nodes.push({
		id : i,
		radius : (d.length>this.dataIndex.value) ? this.getRadius(d[this.dataIndex.value]) : 10,
		group : (d.length>this.dataIndex.group) ? d[this.dataIndex.group] : "",
		color : (d.length>this.dataIndex.color) ? d[this.dataIndex.color] : "",
		x : Math.random() * this.config.width,
		y : Math.random() * this.config.height,
		value : (d.length>this.dataIndex.value) ? this.getRadius(d[this.dataIndex.value]) : 0
	    });
	}
	this.nodes = nodes;

	// create circles
	var circles = vis.selectAll("circle")
	                 .data(nodes, function(d){return d.id})

	var fillColor = this.fillColor;
	var tooltip = this.tooltip;
	circles.enter().append("circle")
            .attr("r", 0)
	    .attr("fill", function(d){ 
		return fillColor(d.color);
	    })
	    .attr("stroke", function(d){ return d3.rgb(fillColor(d.color)).darker();})
	    .attr("id", function(d){ return "bubble_"+d.id;})
	    .on("mouseover", function(d, i){
		var element = this;
		d3.select(element).attr("storke", "black");
		content = '<span class="name">Value : '+d.value+'</span>';
		tooltip.showTooltip(content, d3.event);	
	    })
	    .on("mouseout", function(d, i){
		var element = this;
		d3.select(element).attr("stroke", function(d){ return d3.rgb(fillColor(d.color)).darker();})
		tooltip.hideTooltip();
	    })

	var o = this;
	circles.transition().duration(2000).attr("r", function(d){ return d.radius; });
	this.circles = circles;

	// starts up the force layout
	this.force = d3.layout.force()
       	                      .nodes(nodes)
	                      .size([this.config.width, this.config.height]);
	
    }

    this.all = function(){
	var center = { x : this.config.width/2, y : this.config.height/2 };
	var damper = this.config.damper;
	var circles = this.circles;

	this.force.gravity(this.config.gravity)
	          .charge(function(d){ return -Math.pow(d.radius, 2.0) / 8 ;})
	          .friction(this.config.friction)
	          .on("tick", function(e){
		      var alpha = e.alpha;
		      circles.attr("cx", function(d){
			  return d.x + (center.x - d.x) * damper * alpha;
		      });
		      circles.attr("cy", function(d){
			  return d.y + (center.y - d.y) * damper * alpha;
		      });
		  })
	this.force.start();

	this.vis.selectAll(".groups").remove()
    }

    this.group = function(){
	var config = this.config;
	var circles = this.circles;
	var damper = this.config.damper;
	var groups = this.groups;
	var groupCenter = {};
	var i=0;
	var numGroup = Object.keys(groups).length;
	var numCol = (numGroup > this.config.col) ? this.config.col : numGroup;
	var numRow = parseInt(numGroup / numCol) + 1;
	numCol = Math.max(1, numCol);
	numRow = Math.max(1, numRow);

	for(var k in groups){
	    var len = Object.keys(groups).length
	    groupCenter[k] = {
		x : (i%numCol)*(this.config.width/numCol)+(this.config.width/numCol)/2,
		y : parseInt(i/numCol)*(this.config.width/numRow)+(this.config.width/numRow)/2
	    }

	    i++;
	}

	var groupTitle = this.vis.selectAll(".groups")
	                     .data(Object.keys(groups))

	groupTitle.enter().append("text")
	          .attr("class", "groups")
	          .attr("x", function(d){
		      return groupCenter[d].x;
		  })
	          .attr("y", function(d){
		      return groupCenter[d].y - (config.width/numRow)/2 + 10;
		  })
	          .attr("text-anchor", "middle")
	          .text(function(d){ return d; })
	          
	


	this.force.gravity(this.config.gravity)
	          .charge(function(d){ return -Math.pow(d.radius, 2.0) / 8 ;})
	          .friction(this.config.friction)
	          .on("tick", function(e){
		      var alpha = e.alpha;
		      circles.each(function(alpha){
			  return function(d){
			      d.x = d.x + (groupCenter[d.group].x - d.x) * damper * alpha * 1.1;
			      d.y = d.y + (groupCenter[d.group].y - d.y) * damper * alpha * 1.1;
			  }
		      }(e.alpha))
			  .attr("cx", function(d){ return d.x; })
			  .attr("cy", function(d){ return d.y; })
		  })
	this.force.start();	
    }
    
    this.getRadius = function(n){
	var area = this.config.width*this.config.height;
	var areaForOne = area / this.data.length;
	var minSize = 2;
	var maxSize = Math.max(minSize, Math.sqrt(areaForOne) / 1.2);
	var maxSize = Math.min(maxSize, this.config.width/10);
	var r = d3.scale.pow().exponent(0.5).domain([0, this.maxValue]).range([minSize, maxSize]);
	return Math.abs(r(n));
    }

    this.fillColor = d3.scale.ordinal()
	.domain(["high", "medium", "low" ])
	.range(["#7aa25c", "#beccae", "#d84b2a" ])

    this.tooltip = CustomTooltip("bubble_tooltip", 240)

}
d3.select('#vis').append("svg")
 

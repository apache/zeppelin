
function Analyze(id){
	this.id = id;
	

	if(id==undefined){
		// create new analysis server
	} else {
		// load analysis from server
	}

	this.render = function(target){
		target.html("HELLO");
	}
	
	/**
	 * save analysis
	 */
	this.save = function(){
		
	}
	
	/**
	 * remove this analysis
	 */
	this.remove = function(){
		
	}
	
}

$(document).ready(function() {
	var analyze = new Analyze();
	analyze.render($("#newAnalyze"));
	zeppelin.log("Hey zeppelin")
});
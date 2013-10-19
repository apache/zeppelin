
var loaderObj = {
	templates : [
		'index.html',
		'analyze.html'
	],
	scripts : [
	]
};

function loadScripts(scrs) {
	$(scrs).each(function() {
		loadScript("app/"+scrs);
	});
}
function loadScript(src, listener){
	jQuery.getScript(src, listener);
}

function loadTemplates(templates) {
    $(templates).each(function() {
        var tempObj = $('<script>');
        tempObj.attr('type', 'text/x-handlebars');
        var dataTemplateName = this.substring(0, this.indexOf('.'));
        if(dataTemplateName!="index"){
        	tempObj.attr('data-template-name', dataTemplateName);
        }
        
        $.ajax({
            async: false,
            type: 'GET',
            url: 'app/templates/' + this,
            success: function(resp) {
                tempObj.html(resp);
                $('body').append(tempObj);                
            }
        });
    });
}

$(document).ready(function(){
	// load all ember templates
	loadTemplates(loaderObj.templates);

	
	window.App = Ember.Application.create();
	
	App.Router.map(function(){
	    this.route("analyze", { path:"/analyze"});
	});
	
	App.ApplicationController = Ember.Controller.extend({
	    
	});

	// Analyze --------------------------------------

	App.AnalyzeController = Ember.Controller.extend({
	    zqlLink : "http://nflabs.github.io/zeppelin/#/zql",

	});
	
	App.AnalyzeView = Ember.View.extend({
	    didInsertElement : function(){
			var editor = ace.edit("zqlEditor");
		    editor.setTheme("ace/theme/monokai");
		    editor.getSession().setMode("ace/mode/sql");
		    editor.focus();
		    
			$('#zqlRunButton').on('click', function(w){
				console.log("run zql = %o", editor.getValue());
				zeppelin.zql(editor.getValue(), function(c, d){
					console.log("zql resp=%o %o", c,d);
				}, this);
			});
	    }		
	});
	
});

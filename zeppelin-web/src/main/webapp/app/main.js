
var loaderObj = {
	templates : [
		'index.html',
		'analyze.html',
	        'analyze/edit.html'
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
	    this.resource('analyze', function(){
		this.route('edit', {path:'/:sessionid'});
            });
	});
	
	App.ApplicationController = Ember.Controller.extend({
	    zqlLink : "http://nflabs.github.io/zeppelin/#/zql",	    
	});

	// Analyze --------------------------------------
        App.AnalyzeRoute = Ember.Route.extend({
            model : function(params){
		var running = Ember.$.getJSON('/cxf/zeppelin/analyze/getAllRunning');
		console.log("running=%o", running);
		return running;
            }
        });

        App.AnalyzeEditRoute = App.AnalyzeRoute.extend({
            model : function(params){
		if(params.sessionid!=undefined){
		    var currentSession = Ember.$.getJSON('/cxf/zeppelin/analyze/get/'+params.sessionid);
		    console.log("current=%o", currentSession);
		    return currentSession;
                } else {
		    return null;
		}
            }
        });

	App.AnalyzeController = App.ApplicationController.extend({
	    actions : {
		newSession : function(){
		    controller = this;
		    Ember.$.getJSON('/cxf/zeppelin/analyze/new').then(function(d){
			controller.transitionToRoute('analyze.edit', {sessionid: d.body.id, body:d.body})
		    });

                },
		openSession : function(sessionId){
		    controller = this;
		    Ember.$.getJSON('/cxf/zeppelin/analyze/get/'+sessionId).then(function(d){
			controller.transitionToRoute('analyze.edit', {sessionid: d.body.id, body:d.body})
                    });
		}
	    }
	});

	App.AnalyzeEditController = App.ApplicationController.extend({
	    actions : {
            }
	});
	
	App.AnalyzeEditView = Ember.View.extend({
	    editor : null,
	    currentSession : null,

	    sessionChanged : function(m, o, d){      // called when model is changed
		var session = this.get('controller.model').body

		var editor = this.get('editor');
		if(editor==null) return;

		editor.setValue(session.id)
		if(session.status=="READY"){
		    editor.setReadOnly(false);
		} else {
		    editor.setReadOnly(true);
                }
            }.observes('controller.model'),


	    didInsertElement : function(){            // when it is first time of loading this view, sessionChanged can not be observed
		var session = this.get('controller.model').body;

		var editor = ace.edit("zqlEditor");
		this.set('editor', editor);
		editor.setTheme("ace/theme/monokai");
		editor.getSession().setMode("ace/mode/sql");
		editor.focus();

		editor.setValue(session.id);
		if(session.status=="READY"){
		    editor.setReadOnly(false);
		} else {
		    editor.setReadOnly(true);
                }
		editor.getSession().on('change', function(e){
                });
		
		
		$('#zqlRunButton').on('click', function(w){
		    console.log("run zql = %o", editor.getValue());
		    
		    zeppelin.analyze.run("123", function(c, d){
			if(c==404){
			    zeppelin.alert("Error: Invalid Session", "#analyzeAlert");
			} else {
			    console.log("zql resp=%o %o", c,d);
                        }
		    }, this);
		});
	    },
            willClearRender: function(){
		console.log("Clear editor view");
	    }
	});
	
});

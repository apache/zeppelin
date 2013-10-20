
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
		return running;
            }
        });

        App.AnalyzeEditRoute = App.AnalyzeRoute.extend({
            model : function(params){
		if(params.sessionid!=undefined){
		    var currentSession = Ember.$.getJSON('/cxf/zeppelin/analyze/get/'+params.sessionid);
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
			controller.transitionToRoute('analyze.edit', {sessionid : d.body.id, body:d.body})
		    });

                },
		openSession : function(sessionId){
		    controller = this;
		    Ember.$.getJSON('/cxf/zeppelin/analyze/get/'+sessionId).then(function(d){
			controller.transitionToRoute('analyze.edit', {sessionid : d.body.id, body:d.body})
                    });
		}

	    }
	});

	App.AnalyzeEditController = App.ApplicationController.extend({
	    dirty : false,

	    actions : {
		runSession : function(sessionId){
		    var zql = $('#zqlEditorArea').val();
		    zeppelin.analyze.set(sessionId, "", zql, function(c, d){
			if(c==404){
			    zeppelin.alert("Error: Invalid Session", "#alert");
			} else {
			    zeppelin.analyze.run(sessionId, function(c, d){
				console.log("Analyzed %o %o", c,d);
			    });
                        }
		    }, this);
		},
		beforeChangeSession : function(model, editor){
		    if(model==null) return;

		    // save session before change
		    if(model.status=="READY"){
			if(this.get('dirty')){
			    zeppelin.analyze.set(model.id, "", editor.getValue(), function(c, d){
				if(c==200){
				    console.log("session %o saved", model.id)
				} else {
				    // TODO : handle error
				}
			    });			    
			}
		    }
		},

		afterChangeSession : function(model, editor){
		    this.set('dirty', false);
		},

		zqlChanged : function(zql){
		    this.set('dirty', true);
		},

		loop : function(model, editor){
		    if(model.status=="READY"){
			// auto save every 10 sec
			if(new Date().getSeconds() % 10 == 0 && this.get('dirty')){
			    // TODO display saving... -> saved message
			    zeppelin.analyze.set(model.id, "", editor.getValue(), function(c, d){
				if(c==200){
				    this.set('dirty', false);
				    console.log("autosave completed %o %o", c, d);
				}
				
			    }, this);
			}
		    }
		}
            }
	});
	
	App.AnalyzeEditView = Ember.View.extend({
	    editor : null,
	    currentModel : null,

	    sessionChanged : function(){      // called when model is changed
		var model = this.get('controller.model').body
		var controller = this.get("controller");
		var editor = this.get('editor');

		controller.send("beforeChangeSession", this.get('currentModel'), editor);
		this.set('currentModel', model);
	
		if(editor==null) return;

		editor.setValue(model.zql)
		if(model.status=="READY"){
		    editor.setReadOnly(false);
		} else {
		    editor.setReadOnly(true);
                }

		controller.send("afterChangeSession", model, editor);
            }.observes('controller.model'),


	    didInsertElement : function(){            // when it is first time of loading this view, sessionChanged can not be observed
		var model = this.get('controller.model').body;
		var controller = this.get("controller");
		var view = this;
		this.set('currentModel', model);

		var editor = ace.edit("zqlEditor");
		var editorArea = $('#zqlEditorArea');
		this.set('editor', editor);
		editor.setTheme("ace/theme/monokai");
		editor.getSession().setMode("ace/mode/sql");
		editor.focus();

		editor.setValue(model.zql);
		editorArea.val(model.zql);
		controller.send("zqlChanged", model.zql);

		if(model.status=="READY"){
		    editor.setReadOnly(false);
		} else {
		    editor.setReadOnly(true);
                }

		editor.getSession().on('change', function(e){
		    var zql = editor.getSession().getValue();
		    editorArea.val(zql);
		    controller.send("zqlChanged", zql);
                });


		var editorLoop = function(){
		    setTimeout(function(){
			editorLoop();
		    }, 1000);

		    var model = view.get('currentModel');
		    controller.send("loop", model, editor);
		};
		editorLoop();

		
/*		
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
*/
	    },
            willClearRender: function(){
		console.log("Clear editor view");
	    }
	});
	
});

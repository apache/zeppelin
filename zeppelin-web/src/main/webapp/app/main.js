
var loaderObj = {
	templates : [
		'index.html',
		'zql.html',
	        'zql/edit.html'
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
	    this.resource('zql', function(){
		this.route('edit', {path:'/:sessionid'});
            });
	});
	
	App.ApplicationController = Ember.Controller.extend({
	    zqlLink : "http://nflabs.github.io/zeppelin/#/zql",	    
	});

	// Zql  --------------------------------------
        App.ZqlRoute = Ember.Route.extend({
            model : function(params){
		return params;
            },
	    setupController : function(controller, model){
		zeppelin.zql.find(function(c, resp){
		    if(c==200){
			controller.set('runningSessions', resp);
		    }
		});
	    }
	});

	App.ZqlController = App.ApplicationController.extend({
	    actions : {
		newSession : function(){
		    controller = this;
		    Ember.$.getJSON('/cxf/zeppelin/zql/new').then(function(d){
			// update runnning
			zeppelin.zql.find(function(c, resp){
			    if(c==200){
				controller.set('runningSessions', resp);
			    }
			});

			controller.transitionToRoute('zql.edit', {sessionid : d.body.id, body:d.body})
		    });

                },
		openSession : function(sessionId){
		    controller = this;
		    Ember.$.getJSON('/cxf/zeppelin/zql/get/'+sessionId).then(function(d){
			controller.transitionToRoute('zql.edit', {sessionid : d.body.id, body:d.body})
                    });
		}

	    }
	});

        // ZqlEidt ---------------------------------------
        App.ZqlEditRoute = App.ZqlRoute.extend({
            model : function(params){
		return params;
            },
	    setupController : function(controller, model){
		zeppelin.zql.get(model.sessionid, function(c, d){
		    controller.set('currentSession', d);
		}, this);
	    }
        });


	App.ZqlEditController = App.ApplicationController.extend({
	    dirty : false,

	    actions : {
		runSession : function(sessionId){
		    var zql = $('#zqlEditorArea').val();
		    zeppelin.zql.set(sessionId, "", zql, function(c, d){
			if(c==404){
			    zeppelin.alert("Error: Invalid Session", "#alert");
			} else {
			    zeppelin.zql.run(sessionId, function(c, d){
				console.log("Zql %o %o", c,d);
			    });
                        }
		    }, this);
		},
		beforeChangeSession : function(model, editor){
		    if(model==null) return;

		    // save session before change
		    if(model.status=="READY"){
			if(this.get('dirty')){
			    zeppelin.zql.set(model.id, "", editor.getValue(), function(c, d){
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
		    if(model==null) return;
		    if(model.status=="READY"){
			// auto save every 10 sec
			if(new Date().getSeconds() % 10 == 0 && this.get('dirty')){
			    // TODO display saving... -> saved message
			    zeppelin.zql.set(model.id, "", editor.getValue(), function(c, d){
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
	
	App.ZqlEditView = Ember.View.extend({
	    editor : null,
	    currentModel : null,

	    modelChanged : function(){      // called when model is changed
		var controller = this.get("controller");
		var model = controller.get('currentSession');
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
            }.observes('controller.currentSession'),


	    didInsertElement : function(){            // when it is first time of loading this view, sessionChanged can not be observed
		var controller = this.get("controller");
		var model = controller.get('currentSession');
		var view = this;
		this.set('currentModel', model);

		var editor = ace.edit("zqlEditor");
		var editorArea = $('#zqlEditorArea');
		this.set('editor', editor);
		editor.setTheme("ace/theme/monokai");
		editor.getSession().setMode("ace/mode/sql");
		editor.focus();
		
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
	    },
            willClearRender: function(){
		var controller = this.get("controller");
		var model = controller.get("currentSession");
		var view = this;
		var editor = ace.edit("zqlEditor");
		controller.send('beforeChangeSession', model, editor);
		this.set('currentModel', null);
	    }
	});
	
});

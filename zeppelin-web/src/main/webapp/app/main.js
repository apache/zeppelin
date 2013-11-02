
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
		zeppelin.zql.find((new Date().getTime())-(1000*60*60*24*30), new Date().getTime(), 10, function(c, resp){
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
			zeppelin.zql.find((new Date().getTime())-(1000*60*60*24*30), new Date().getTime(), 10, function(c, resp){
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
	    currentSession : undefined,
	    zql : undefined,

	    actions : {
		runSession : function(){
		    var controller = this;
		    var session = this.get('currentSession');
		    if(session==undefined) return;

		    var sessionId = session.id;

		    var zql = this.get('zql');
		    zeppelin.zql.set(sessionId, "", zql, function(c, d){
			if(c!=200){
			    zeppelin.alert("Error: Invalid Session", "#alert");
			} else {
			    this.set('dirty', false);
			    zeppelin.zql.run(sessionId, function(c, d){
				if(c==200){
				    controller.send('loadSession', sessionId);
				}		 
			    });
                        }
		    }, this);
		},
		loadSession : function(sessionId){
		    var controller = this;
		    zeppelin.zql.get(sessionId, function(c, d){
			controller.set('currentSession', d);
		    });
		},

		// called from view
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

		// called from view
		afterChangeSession : function(model, editor){
		    this.set('dirty', false);
		},

		zqlChanged : function(zql){
		    this.set('dirty', true);
		    this.set('zql', zql);
		},

		// called from view
		loop : function(editor){
		    var controller = this;
		    var session = this.get('currentSession');
		    if(session==null) return;
		    if(session.status=="READY"){
			// auto save every 10 sec
			if(new Date().getSeconds() % 10 == 0 && this.get('dirty')){
			    // TODO display saving... -> saved message
			    zeppelin.zql.set(session.id, "", editor.getValue(), function(c, d){
				if(c==200){
				    this.set('dirty', false);
				    console.log("autosave completed %o %o", c, d);
				}
				
			    }, this);
			}
		    } else if(session.status=="RUNNING"){
			if(new Date().getSeconds() % 1 == 0){ // refreshing every 1 sec
			    controller.send('loadSession', session.id);
			}
		    } else if(session.status=="FINISHED"){
			// change
		    } else if(session.status=="ERROR"){
		    } else if(session.status=="ABORT"){
		    }
		} 
            }
	});
	
	App.ZqlEditView = Ember.View.extend({
	    editor : null,
	    currentModel : undefined,

	    modelChanged : function(){      // called when model is changed
		var controller = this.get("controller");
		var model = controller.get('currentSession');
		var editor = this.get('editor');

		controller.send("beforeChangeSession", this.get('currentModel'), editor);
		this.set('currentModel', model);
		console.log("Current session=%o", model);
		if(editor==null) return;

		editor.setValue(model.zql)

		// clear visualizations
		$('#visualizationContainer iframe').remove();
		$('#msgBox div').remove();

		if(model.status=="READY"){
		    editor.setReadOnly(false);
		    $('#zqlRunButton').text("Run");
		    //$('#zqlRunButton').removeClass('disabled');		    
		} else if(model.status=="RUNNING"){
		    $('#zqlRunButton').text("Running ...");
		    //$('#zqlRunButton').addClass('disabled');
		    //$('#zqlRunButton').prop('disabled', true);
		    editor.setReadOnly(true);
                } else if(model.status=="FINISHED"){
		    $('#zqlRunButton').text("Run");
		    //$('#zqlRunButton').addClass('disabled');
		    //$('#zqlRunButton').prop('disabled', true);
		    editor.setReadOnly(false);

		    // draw visualization if there's some
		    if(model.zqlPlans){			
			for(var i=0; i<model.zqlPlans.length; i++){
			    var plan = model.zqlPlans[i];
			    console.log("visualize plan=%o", plan);

			    if(!plan || !plan.webEnabled) continue;
			    
			    $('<iframe />', {				
				name : plan.id,
				id : plan.id,
				src : zeppelin.getWebResourceURL(model.id, plan.id),
				scrolling : 'no'
				/*,
										     
				frameborder : "0",
				height : "100%",
				width : "100%"*/
			    }).appendTo('#visualizationContainer');

			    $('#'+plan.id).load(function(c,d){
				console.log("iframe %o %o", c,d);
			    });
			}

			jQuery('iframe').iframeAutoHeight();
		    }


		} else if(model.status=="ERROR"){
		    $('#zqlRunButton').text("Run");
		    for(var i=0; i< model.error.rows.length; i++){
			$('#msgBox').append("<div>"+model.error.rows[0][3]+"</div>");
		    }
		    //$('#zqlRunButton').removeClass('disabled');
		    editor.setReadOnly(false);
		} else if(model.status=="ABORT"){
		    $('#zqlRunButton').text("Run");
		    //$('#zqlRunButton').removeClass('disabled');
		    editor.setReadOnly(false);
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

		    controller.send("loop", editor);
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

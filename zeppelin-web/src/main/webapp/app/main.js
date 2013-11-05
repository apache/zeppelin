
var loaderObj = {
	templates : [
		'index.html',
		'zql.html',
	        'zql/edit.html',
	        'zql/param.html'
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
	    zqlLink : "http://zeppelin-project.org/#/zql",	    
	});

	// Zql  --------------------------------------
        App.ZqlRoute = Ember.Route.extend({
            model : function(params){
		return params;
            },
	    setupController : function(controller, model){
		zeppelin.zql.list(function(c, resp){
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
			zeppelin.zql.list(function(c, resp){
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
		},
		updateSession : function(){
		    controller = this;
		    zeppelin.zql.list(function(c, resp){
			if(c==200){
			    controller.set('runningSessions', resp);
			}
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
	    dryrun : true,
	    currentSession : undefined,
	    zql : undefined,
	    sessionName : undefined,
	    params : undefined,
	    

	    actions : {
		runSession : function(){
		    var controller = this;
		    var session = this.get('currentSession');
		    if(session==undefined) return;

		    var sessionId = session.id;

		    var zql = this.get('zql');
		    var sessionName = this.get('sessionName');
		    if(this.get('dryrun')==true && false){
			zeppelin.zql.set(sessionId, sessionName, zql, undefined, function(c, d){
			    if(c!=200){
				zeppelin.alert("Error: Invalid Session", "#alert");
			    } else {
				zeppelin.zql.dryRun(sessionId, function(c, d){
				    if(c==200){
					controller.set('dryrun', false);
					controller.send('loadSession', sessionId);
				    }		 
				});
                            }
			}, this);
		    } else {
			zeppelin.zql.set(sessionId, sessionName, zql, undefined, function(c, d){
			    if(c!=200){
				zeppelin.alert("Error: Invalid Session", "#alert");
			    } else {
				controller.set('dirty', false);
				zeppelin.zql.run(sessionId, function(c, d){
				    if(c==200){
					controller.send('loadSession', sessionId);
				    }		 
				});				
                            }
			}, this);
		    }
		},

		loadSession : function(sessionId){
		    var controller = this;
		    zeppelin.zql.get(sessionId, function(c, d){
			controller.set('currentSession', d);
		    });
		},

		// called from view
		beforeChangeSession : function(model, sessionNameEditor, editor){
		    if(model==null) return;
		    // save session before change
		    if(model.status=="READY"){
			if(this.get('dirty')){
			    zeppelin.zql.set(model.id, sessionNameEditor.val(), editor.getValue(), undefined, function(c, d){
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
		afterChangeSession : function(model, sessionNameEditor, editor){
		    this.set('dirty', false);
		    this.set("zql", editor.getValue());
		    this.set("sessionName", sessionNameEditor.val());
		},

		zqlChanged : function(zql){
		    //console.log("Zql changed from %o to %o", this.get('zql'), zql);
		    this.set('dirty', true);
		    this.set('dryrun', true);
		    this.set('zql', zql);
		},

		zqlSessionNameChanged : function(sessionName){
		    //console.log("Session name changed %o", sessionName);
		    this.set('dirty', true);
		    this.set('sessionName', sessionName);
		},

		// called from view
		loop : function(sessionNameEditor, editor){
		    var controller = this;
		    var session = this.get('currentSession');
		    if(session==null) return;
		    if(session.status=="READY" || session.status=="FINISHED" || session.status=="ERROR" || session.status=="ABORT"){
			// auto save every 10 sec
			if(new Date().getSeconds() % 10 == 0 && this.get('dirty')){
			    // TODO display saving... -> saved message
			    zeppelin.zql.set(session.id, sessionNameEditor.val(), editor.getValue(), undefined, function(c, d){
				if(c==200){
				    this.set('dirty', false);
				    console.log("autosave completed %o %o", c, d);

				    // send zqlcontroller to refresh session list. (session name may change by this save)
				    this.controllerFor('zql').send('updateSession');
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
	    sessionNameEditor : undefined,
	    editor : undefined,
	    currentModel : undefined,

	    modelChanged : function(){      // called when model is changed
		var controller = this.get("controller");
		var model = controller.get('currentSession');
		var editor = this.get('editor');
		var sessionNameEditor = this.get('sessionNameEditor');

		controller.send("beforeChangeSession", this.get('currentModel'), sessionNameEditor, editor);
		this.set('currentModel', model);
		console.log("Current session=%o", model);
		if(editor==null) return;
		if(editor.getValue()!=model.zql){
		    editor.setValue(model.zql)
		}

		if(model.jobName && model.jobName!=""){
		    if(sessionNameEditor.val()!=model.jobName){
			sessionNameEditor.val(model.jobName);
		    }
		} else {
		    sessionNameEditor.val(model.id);
		}

		// clear visualizations
		$('#visualizationContainer iframe').remove();
		$('#visualizationContainer div').remove();
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
			    var planModel = model.zqlPlans[i];
			    if(!planModel) continue;

			    var planStack = [];
			    for(var p = planModel; p!=undefined; p = p.prev){
				planStack.unshift(p);
			    }
			    
			    for(var j=0; j<planStack.length; j++){
				var plan = planStack[j];

				if(!plan || !plan.webEnabled) continue;
				console.log("displaying %o", plan);
				var planInfo = (plan.libName) ? plan.libName : plan.query;
				$('#visualizationContainer').append('<div class="visTitle">'+planInfo+"</div>");
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
				    //console.log("iframe %o %o", c,d);
				});
			    }
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

		controller.send("afterChangeSession", model, sessionNameEditor, editor);
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

		var sessionNameEditor = $('#zqlSessionName');
		sessionNameEditor.on('change', function(e){
		    controller.send("zqlSessionNameChanged", sessionNameEditor.val());
		});
		this.set('sessionNameEditor', sessionNameEditor);

		var editorLoop = function(){
		    setTimeout(function(){
			editorLoop();
		    }, 1000);

		    controller.send("loop", sessionNameEditor, editor);
		};
		editorLoop();
	    },
            willClearRender: function(){
		var controller = this.get("controller");
		var model = controller.get("currentSession");
		var view = this;
		var editor = ace.edit("zqlEditor");
		var sessionNameEditor = this.get('sessionNameEditor');
		controller.send('beforeChangeSession', model, sessionNameEditor, editor);
		this.set('currentModel', null);
	    },

	    zqlParamView : Ember.View.extend({
		templateName : 'zql/param',
		paramInfo : null,

		modelChanged : function(){
		    var controller = this.get("controller");
		    var session = controller.get('currentSession');
		    console.log("model changes %o", session);
		    var planStack = [];

		    if(session.zqlPlans){
			for(var i=0; i<session.zqlPlans.length; i++){
			    var planModel = session.zqlPlans[i];
			    if(!planModel) continue;
			    
			    for(var p = planModel; p!=undefined; p = p.prev){
				//console.log("P=%o", p.paramInfos);
				planStack.unshift(p);
			    }
			}
		    }
		    

		    //console.log("paramInfo=%o", planStack);
		    this.set('paramInfo', planStack);
		}.observes('controller.currentSession'),

		didInsertElement : function(){
  		    var controller = this.get("controller");
		    var model = controller.get("currentSession");
		    var view = this;
		    
		},
		willClearRender: function(){
  		    var controller = this.get("controller");
		    var model = controller.get("currentSession");
		    var view = this;
		    this.set('paramInfo', null);
		}
	    })
	});
});


Handlebars.registerHelper('eachMap', function(context, options) {
    var ret = "";
    for(var prop in context)
    {
        ret = ret + options.fn({key:prop,value:context[prop]});
    }
    return ret;
});

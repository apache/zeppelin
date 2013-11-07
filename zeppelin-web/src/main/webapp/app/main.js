
var loaderObj = {
	templates : [
		'index.html',
	        'default_layout.html',
	        'report_layout.html',
		'zql.html',
	        'zql/edit.html',
	        'zql/param.html',
	        'report.html',
	        'report/link.html'
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
        $.fn.editable.defaults.mode = 'inline';

	// load all ember templates
	loadTemplates(loaderObj.templates);

	
	window.App = Ember.Application.create();
	
	App.Router.map(function(){
	    this.resource('zql', function(){
		this.route('edit', {path:'/:sessionid'});
            });
	    this.resource('report', function(){
		this.route('link', {path:'/:sessionid'});
	    });
	});
	
	App.ApplicationController = Ember.Controller.extend({
	    zqlLink : "http://zeppelin-project.org/#/zql",
	});

        App.IndexView = Ember.View.extend({
	    layoutName: 'default_layout',
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
	    dirty : 0,
	    dirtyFlag : {
		CLEAN : 0,
		ZQL : 1,
		NAME : 2,
		PARAMS : 4,
		CRON : 8
	    },
	    dryrun : true,
	    currentSession : undefined,
	    zql : undefined,
	    sessionName : undefined,
	    sessionCron : undefined,
	    params : undefined,
	    
	    actions : {
		runSession : function(){
		    var controller = this;
		    var session = this.get('currentSession');
		    if(session==undefined) return;

		    var sessionId = session.id;

		    var zql = this.get('zql');
		    var sessionName = this.get('sessionName');
		    var sessionCron = this.get('sessionCron');
		    if(this.get('dryrun')==true && false){
			zeppelin.zql.set(sessionId, sessionName, zql, undefined, sessionCron, function(c, d){
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
			zeppelin.zql.set(sessionId, sessionName, zql, undefined, sessionCron, function(c, d){
			    if(c!=200){
				zeppelin.alert("Error: Invalid Session", "#alert");
			    } else {
				controller.set('dirty', 0);
				zeppelin.zql.run(sessionId, function(c, d){
				    if(c==200){
					controller.send('loadSession', sessionId);
				    }		 
				});				
                            }
			}, this);
		    }
		},
		shareSession : function(){
		    var controller = this;
		    var session = this.get('currentSession');
		    if(session==undefined) return;
		    
		    
		},
		deleteSession : function(){
		    var controller = this;
		    var session = this.get('currentSession');
		    if(session==undefined) return;

		    console.log("Delete session %o", session.id);

		    zeppelin.zql.del(session.id, function(c, d){
			if(c==200){
			    controller.controllerFor('zql').send('updateSession');
			    controller.transitionToRoute('zql');			    
			} else {
			    // handle error
			}
		    });
		},

		loadSession : function(sessionId){
		    var controller = this;
		    zeppelin.zql.get(sessionId, function(c, d){
			if(c==200){
			    controller.set('currentSession', d);
			}
		    });
		},

		// called from view
		beforeChangeSession : function(model, sessionNameEditor, editor, sessionCronEditor){
		    if(model==null) return;
		    // save session before change
		    if(model.status=="READY"){
			if(this.get('dirty')){
			    zeppelin.zql.set(model.id, sessionNameEditor.val(), editor.getValue(), undefined, sessionCronEditor.getValue(), function(c, d){
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
		afterChangeSession : function(model, sessionNameEditor, editor, sessionCronEditor){
		    this.set('dirty', 0);
		    this.set("zql", editor.getValue());
		    this.set("sessionName", sessionNameEditor.editable('getValue', true));
		    this.set("sessionCron", sessionCronEditor.editable('getValue', true));

		    durationToString = function(duration){
			var took = "";
			if(duration.weeks>0){
			    took += duration.weeks+" weeks, ";
			}
			if(duration.days>0){
			    took += duration.days+" days, ";
			}
			if(duration.hours>0){
			    took += duration.hours+" hours, ";
			}
			if(duration.minutes>0){
			    took += duration.minutes+" minutes, ";
			}
			if(duration.seconds>0){
			    took += duration.seconds+"."+duration.millis+" seconds";
			}
			else
			    took += "0."+duration.millis+" seconds";
			return took;
		    };

		    var getDuration = function(timeMillis){
			if(isNaN(timeMillis) || timeMillis <0) timeMillis = 0;
			var units = [
			    {label:"millis",    mod:1000,},
			    {label:"seconds",   mod:60,},
			    {label:"minutes",   mod:60,},
			    {label:"hours",     mod:24,},
			    {label:"days",      mod:7,},
			    {label:"weeks",     mod:52,},
			];
			var duration = new Object();
			var x = timeMillis;
			for (i = 0; i < units.length; i++){
			    var tmp = x % units[i].mod;
			    duration[units[i].label] = tmp;
			    x = (x - tmp) / units[i].mod
			}
			return duration;
		    };
		    this.set("sessionTook", durationToString(getDuration(new Date(model.dateFinished).getTime() - new Date(model.dateStarted).getTime())));
		},

		zqlChanged : function(zql){
		    //console.log("Zql changed from %o to %o", this.get('zql'), zql);
		    this.set('dirty', this.get('dirty') | this.get('dirtyFlag').ZQL);
		    this.set('dryrun', true);
		    this.set('zql', zql);
		},

		zqlSessionNameChanged : function(sessionName){
		    //console.log("Session name changed %o", sessionName);
		    this.set('dirty', this.get('dirty') | this.get('dirtyFlag').NAME);
		    this.set('sessionName', sessionName);
		},

		zqlSessionCronChanged : function(sessionCron){
		    //console.log("Session name changed %o", sessionName);
		    this.set('dirty', this.get('dirty') | this.get('dirtyFlag').CRON);
		    this.set('sessionCron', sessionCron);
		},

		// called from view
		loop : function(sessionNameEditor, editor, sessionCronEditor){
		    var controller = this;
		    var session = this.get('currentSession');
		    if(session==null) return;
		    if(session.status=="READY" || session.status=="FINISHED" || session.status=="ERROR" || session.status=="ABORT"){
			// auto save every 10 sec
			if(new Date().getSeconds() % 10 == 0 && (this.get('dirty') & this.get('dirtyFlag').ZQL)){
			    
			    // TODO display saving... -> saved message
			    zeppelin.zql.setZql(session.id, editor.getValue(), function(c, d){
				if(c==200){
				    this.set('dirty', (this.get('dirty') & ~this.get('dirtyFlag').ZQL));
				    console.log("autosave zql completed %o %o", c, d);

				    // send zqlcontroller to refresh session list. (session name may change by this save)
				    this.controllerFor('zql').send('updateSession');
				}
				
			    }, this);
			}

			if(new Date().getSeconds() % 10 == 0 && (this.get('dirty') & this.get('dirtyFlag').NAME)){
			    
			    // TODO display saving... -> saved message
			    zeppelin.zql.setName(session.id, sessionNameEditor.editable('getValue', true), function(c, d){
				if(c==200){
				    this.set('dirty', (this.get('dirty') & ~this.get('dirtyFlag').NAME));
				    console.log("autosave name completed %o %o", c, d);
				}
				
			    }, this);
			}

			if(new Date().getSeconds() % 10 == 0 && (this.get('dirty') & this.get('dirtyFlag').CRON)){
			    
			    // TODO display saving... -> saved message
			    zeppelin.zql.setCron(session.id, sessionCronEditor.editable('getValue', true), function(c, d){
				if(c==200){
				    this.set('dirty', (this.get('dirty') & ~this.get('dirtyFlag').CRON));
				    console.log("autosave cron completed %o %o", c, d);
				}
				
			    }, this);
			}


			if(new Date().getSeconds() % 60 == 0){ // check if it is running by scheduler every 1m
			    zeppelin.zql.get(session.id, function(c, d){
				if(c==200){
				    if(d.status=="RUNNING" || d.dateFinished!=session.dateFinished){
					if(controller.get('dirty')==0){ // auto refresh in only clean state
					    controller.set("currentSession", d);
					}
				    }
				}
			    });
			    this.controllerFor('zql').send('updateSession');
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

	App.ZqlView = Ember.View.extend({
	    layoutName: 'default_layout',
	});

	App.ZqlEditView = Ember.View.extend({
	    layoutName: 'default_layout',
	    sessionNameEditor : undefined,
	    sessionCronEditor : undefined,
	    editor : undefined,
	    currentModel : undefined,


	    modelChanged : function(){      // called when model is changed
		var controller = this.get("controller");
		var model = controller.get('currentSession');
		var editor = this.get('editor');
		var sessionNameEditor = this.get('sessionNameEditor');
		var sessionCronEditor = this.get('sessionCronEditor');

		controller.send("beforeChangeSession", this.get('currentModel'), sessionNameEditor, editor, sessionCronEditor);
		this.set('currentModel', model);
		console.log("Current session=%o", model);
		if(editor==null) return;
		if(editor.getValue()!=model.zql){
		    editor.setValue(model.zql)
		}

		if(model.jobName && model.jobName!=""){
		    if(sessionNameEditor.editable('getValue', true)!=model.jobName){
			sessionNameEditor.editable('setValue', model.jobName);
		    }
		} else {
		    sessionNameEditor.editable('setValue', model.id);
		}


		if(model.cron && model.cron!=""){
		    if(sessionCronEditor.editable('getValue', true)!=model.cron){
			sessionCronEditor.editable('setValue', model.cron);
		    }
		} else {
		    sessionCronEditor.editable('setValue', "");
		}


		// clear visualizations
		$('#visualizationContainer iframe').remove();
		$('#visualizationContainer div').remove();
		$('#msgBox div').remove();

		if(model.status=="READY"){
		    editor.setReadOnly(false);
		    sessionNameEditor.editable('enable');
		    sessionCronEditor.editable('enable');
		    $('#zqlRunButton').text("Run");
		    //$('#zqlRunButton').removeClass('disabled');		    
		} else if(model.status=="RUNNING"){
		    $('#zqlRunButton').text("Running ...");
		    //$('#zqlRunButton').addClass('disabled');
		    //$('#zqlRunButton').prop('disabled', true);
		    editor.setReadOnly(true);
		    sessionNameEditor.editable('disable');
		    sessionCronEditor.editable('disable');
                } else if(model.status=="FINISHED"){
		    $('#zqlRunButton').text("Run");
		    //$('#zqlRunButton').addClass('disabled');
		    //$('#zqlRunButton').prop('disabled', true);
		    editor.setReadOnly(false);
		    sessionNameEditor.editable('enable');
		    sessionCronEditor.editable('enable');

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
				if(planInfo.length>1 && planInfo[0]=='!'){
				    planInfo = planInfo.substring(1);
				}
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
		    sessionNameEditor.editable('enable');
		    sessionCronEditor.editable('enable');
		} else if(model.status=="ABORT"){
		    $('#zqlRunButton').text("Run");
		    //$('#zqlRunButton').removeClass('disabled');
		    editor.setReadOnly(false);
		    sessionNameEditor.editable('enable');
		    sessionCronEditor.editable('enable');
		}

		controller.send("afterChangeSession", model, sessionNameEditor, editor, sessionCronEditor);
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

		var sessionNameEditor = $('#zqlSessionName')
		$('#zqlSessionName').editable({});
		sessionNameEditor.on('save', function(e, params) {
		    controller.send("zqlSessionNameChanged", params.newValue);	
		});		
		this.set('sessionNameEditor', sessionNameEditor);

		var sessionCronEditor = $('#zqlSessionCron')
		$('#zqlSessionCron').editable({
		    value : "",
		    source : [
			{ value : "", text: 'None' },
			{ value : "0 0/1 * * * ?", text: '1m' },
			{ value : "0 0 0/1 * * ?", text: '1h' },
			{ value : "0 0 0/3 * * ?", text: '3h' },
			{ value : "0 0 0/6 * * ?", text: '6h' },
			{ value : "0 0 0/12 * * ?", text: '12h' },
			{ value : "0 0 0 * * ?", text: '24h' },
		    ]
		});
		sessionCronEditor.on('save', function(e, params) {
		    controller.send("zqlSessionCronChanged", params.newValue);	
		});		
		this.set('sessionCronEditor', sessionCronEditor);

		var editorLoop = function(){
		    setTimeout(function(){
			editorLoop();
		    }, 1000);

		    controller.send("loop", sessionNameEditor, editor, sessionCronEditor);
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


	// Report  --------------------------------------
        App.ReportRoute = Ember.Route.extend({
            model : function(params){
		return params;
            },
	    setupController : function(controller, model){
	    }
	});

        // ZqlEidt ---------------------------------------
        App.ReportLinkRoute = App.ReportRoute.extend({
            model : function(params){
		return params;
            },
	    setupController : function(controller, model){
		zeppelin.zql.get(model.sessionid, function(c, d){
		    controller.set('currentSession', d);
		}, this);
	    }
        });


	App.ReportController = App.ApplicationController.extend({
	});

	App.ReportLinkController = App.ApplicationController.extend({
	    currentSession : undefined
	});


	App.ReportLinkView = Ember.View.extend({
	    layoutName: 'report_layout',

	    modelChanged : function(){      // called when model is changed
		var controller = this.get("controller");
		var model = controller.get('currentSession');
		if(!model){
		    return null;
		}
		console.log("report %o", model);

		var zqlSessionName = $('#zqlSessionName');
		if(model.jobName && model.jobName!=""){
		    if(zqlSessionName.text()!=model.jobName){
			zqlSessionName.html(model.jobName);
		    }
		} else {
		    zqlSessionName.html(model.id);
		}

		// clear visualizations
		$('#visualizationContainer iframe').remove();
		$('#visualizationContainer div').remove();
		$('#msgBox div').remove();

		if(model.status=="READY"){
		} else if(model.status=="RUNNING"){
                } else if(model.status=="FINISHED"){
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
				if(planInfo.length>1 && planInfo[0]=='!'){
				    planInfo = planInfo.substring(1);
				}
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
		    for(var i=0; i< model.error.rows.length; i++){
			$('#msgBox').append("<div>"+model.error.rows[0][3]+"</div>");
		    }
		} else if(model.status=="ABORT"){
		}
		
		
            }.observes('controller.currentSession'),
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

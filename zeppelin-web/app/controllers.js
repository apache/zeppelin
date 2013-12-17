
App.ApplicationController = Ember.Controller.extend({
  zqlLink : "http://zeppelin-project.org/#/zql",
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
  historyId : undefined,
  zql : undefined,
  sessionName : undefined,
  sessionCron : undefined,
  params : undefined,
  needs: ['zql'],
	    
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
              if(session.status=="RUNNING"){
			    return;
			  }
			  zeppelin.zql.set(sessionId, sessionName, zql, undefined, sessionCron, function(c, d){
			    if(c!=200){
				  zeppelin.alert("Error: Invalid Session", "#alert");
			    } else {
				  controller.set('dirty', 0);
				  zeppelin.zql.run(sessionId, function(c, d){
				    if (c==200) {
					  controller.send('loadSession', sessionId);
				    }
				  });			
                }
			  }, this);
		    }
	},

	abortSession : function(){
	  var controller = this;
	  var session = this.get('currentSession');
	  if (session == undefined) { return; }
	  zeppelin.zql.abort(session.id, function(c,d){
		if (c == 200){
		  console.log("session %o aborted", session);
		} else {
		  console.log("session %o abort fail", session);
		}
	  });
	},

	shareSession : function(){
	  var controller = this;
	  var session = this.get('currentSession');
	  if (session == undefined) { return; }
	},

	deleteSession : function(){
	  var controller = this;
	  var session = this.get('currentSession');
	  if(session==undefined) {return;}
	  console.log("Delete session %o", session.id);

	  zeppelin.zql.del(session.id, function(c, d){
		if(c==200){
		  controller.get('controllers.zql').send('updateSession');
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
				    controller.get('controllers.zql').send('updateSession');
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
			    controller.get('controllers.zql').send('updateSession');
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



App.ReportController = App.ApplicationController.extend();

App.ReportLinkController = App.ApplicationController.extend({
 currentSession : undefined
});


App.ApplicationController = Ember.Controller.extend({
    zqlLink : "http://zeppelin-project.org/#/zql",
});


App.ZqlController = App.ApplicationController.extend({
    actions : {
        newJob : function(){
            controller = this;
            Ember.$.getJSON('/cxf/zeppelin/zql/new').then(function(d){
                // update runnning
                zeppelin.zql.list(function(c, resp){
                    if(c==200){
                        controller.set('runningJobs', resp);
                    }
                });

                controller.transitionToRoute('zql.edit', {jobid : d.body.id, historyid: "", body:d.body})
            });

        },
        openJob : function(jobid){
            controller = this;
            Ember.$.getJSON('/cxf/zeppelin/zql/get/'+jobid).then(function(d){
		controller.transitionToRoute('zql.edit', {jobid : d.body.id, historyid : "", body:d.body})
            });
        },
        updateJob : function(){
            controller = this;
            zeppelin.zql.list(function(c, resp){
                if(c==200){
                    controller.set('runningJobs', resp);
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
    currentJob : undefined,
    historyId : undefined,
    zql : undefined,
    jobName : undefined,
    jobCron : undefined,
    params : undefined,
    needs: ['zql'],
    
    actions : {
        runJob : function(){
            var controller = this;
            var job = this.get('currentJob');
	    var historyId = this.get('historyId');
            if(job==undefined) return;
	    if(historyId) return;

            var jobid = job.id;

            var zql = this.get('zql');
            var jobName = this.get('jobName');
            var jobCron = this.get('jobCron');
            if(this.get('dryrun')==true && false){
                zeppelin.zql.set(jobid, jobName, zql, undefined, jobCron, function(c, d){
                    if(c!=200){
                        zeppelin.alert("Error: Invalid Job", "#alert");
                    } else {
                        zeppelin.zql.dryRun(jobid, function(c, d){
                            if(c==200){
                                controller.set('dryrun', false);
                                controller.send('loadJob', jobid);
                            }            
                        });
                    }
                }, this);
            } else {
                if(job.status=="RUNNING"){
                    return;
                }
                zeppelin.zql.set(jobid, jobName, zql, undefined, jobCron, function(c, d){
                    if(c!=200){
                        zeppelin.alert("Error: Invalid Job", "#alert");
                    } else {
                        controller.set('dirty', 0);
                        zeppelin.zql.run(jobid, function(c, d){
                            if (c==200) {
                                controller.send('loadJob', jobid);
                            }
                        });                     
                    }
                }, this);
            }
        },

        abortJob : function(){
            var controller = this;
            var job = this.get('currentJob');
            if (job == undefined) { return; }
            zeppelin.zql.abort(job.id, function(c,d){
                if (c == 200){
                    console.log("job %o aborted", job);
                } else {
                    console.log("job %o abort fail", job);
                }
            });
        },

        shareJob : function(){
            var controller = this;
            var job = this.get('currentJob');
	    var historyId = this.get('historyId');
            if (job == undefined) { return; }
	    
	    // TODO save job
	    controller.transitionToRoute('report.link', {jobid : job.id, historyid : (historyId==undefined) ? "" : historyId});	    
        },

        deleteJob : function(){
            var controller = this;
            var job = this.get('currentJob');
	    var historyId = this.get('historyId');
            if(job==undefined) {return;}
            console.log("Delete job %o", job.id);
	    if (!historyId) {
		zeppelin.zql.del(job.id, function(c, d){
                    if(c==200){
			controller.get('controllers.zql').send('updateJob');
			controller.transitionToRoute('zql');                            
                    } else {
			// handle error
                    }
		});
	    } else {
		zeppelin.zql.delHistory(job.id, historyId, function(c, d){
		    if (c==200) {
			controller.transitionToRoute('zql.edit', {jobid:job.id, historyid:""});
		    } else {
			// handle error
		    }
		});
	    }
        },
        loadJob : function(jobid){
            var controller = this;
            zeppelin.zql.get(jobid, function(c, d){
                if(c==200){
                    controller.set('historyId', undefined);
                    controller.set('currentJob', d);
                }
            });
        },
        // called from view
        beforeChangeJob : function(model, jobNameEditor, editor, jobCronEditor){
            if(model==null) return;
            // TODO save job before change
            if(model.status=="READY"){
                if(this.get('dirty')){
                    zeppelin.zql.set(model.id, jobNameEditor.val(), editor.getValue(), undefined, jobCronEditor.getValue(), function(c, d){
                        if(c==200){
                            console.log("job %o saved", model.id)
                        } else {
                            // TODO : handle error
                        }
                    });                     
                }
            }
        },
        // called from view
        afterChangeJob : function(model, jobNameEditor, editor, jobCronEditor){
            this.set('dirty', 0);
            this.set("zql", editor.getValue());
            this.set("jobName", jobNameEditor.editable('getValue', true));
            this.set("jobCron", jobCronEditor.editable('getValue', true));

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
            this.set("jobTook", durationToString(getDuration(new Date(model.dateFinished).getTime() - new Date(model.dateStarted).getTime())));
        },

        zqlChanged : function(zql){
            //console.log("Zql changed from %o to %o", this.get('zql'), zql);
            this.set('dirty', this.get('dirty') | this.get('dirtyFlag').ZQL);
            this.set('dryrun', true);
            this.set('zql', zql);
        },

        zqlJobNameChanged : function(jobName){
            //console.log("Job name changed %o", jobName);
            this.set('dirty', this.get('dirty') | this.get('dirtyFlag').NAME);
            this.set('jobName', jobName);
        },
        
        zqlJobCronChanged : function(jobCron){
            //console.log("Job name changed %o", jobName);
            this.set('dirty', this.get('dirty') | this.get('dirtyFlag').CRON);
            this.set('jobCron', jobCron);
        },

        /**
         * called when user select on of history from history list
         */
        zqlJobHistoryChanged : function(jobId, historyId){
            // save current job if it is not saved
            console.log("load history %o", historyId);
            this.transitionToRoute('zql.edit', {jobid:jobId, historyid:historyId});
        },

        updateHistory : function(jobId){
            controller = this;
            zeppelin.zql.listHistory(jobId, function(c, hist){
                if(c==200){
                    controller.set('historyList', hist);
                }
            });
        },

        // called from view
        loop : function(jobNameEditor, editor, jobCronEditor){
            var controller = this;
            var job = this.get('currentJob');
            if(job==null) return;
            if(job.status=="READY" || job.status=="FINISHED" || job.status=="ERROR" || job.status=="ABORT"){
                // auto save every 10 sec
                if(new Date().getSeconds() % 10 == 0 && (this.get('dirty') & this.get('dirtyFlag').ZQL)){
                    
                    // TODO display saving... -> saved message
                    zeppelin.zql.setZql(job.id, editor.getValue(), function(c, d){
                        if(c==200){
                            this.set('dirty', (this.get('dirty') & ~this.get('dirtyFlag').ZQL));
                            console.log("autosave zql completed %o %o", c, d);

                            // send zqlcontroller to refresh job list. (job name may change by this save)
                            controller.get('controllers.zql').send('updateJob');
                        }
                        
                    }, this);
                }

                if(new Date().getSeconds() % 10 == 0 && (this.get('dirty') & this.get('dirtyFlag').NAME)){
                    
                    // TODO display saving... -> saved message
                    zeppelin.zql.setName(job.id, jobNameEditor.editable('getValue', true), function(c, d){
                        if(c==200){
                            this.set('dirty', (this.get('dirty') & ~this.get('dirtyFlag').NAME));
                            console.log("autosave name completed %o %o", c, d);
                        }
                        
                    }, this);
                }

                if(new Date().getSeconds() % 10 == 0 && (this.get('dirty') & this.get('dirtyFlag').CRON)){
                    
                    // TODO display saving... -> saved message
                    zeppelin.zql.setCron(job.id, jobCronEditor.editable('getValue', true), function(c, d){
                        if(c==200){
                            this.set('dirty', (this.get('dirty') & ~this.get('dirtyFlag').CRON));
                            console.log("autosave cron completed %o %o", c, d);
                        }
                        
                    }, this);
                }


                if(new Date().getSeconds() % 60 == 0){ // check if it is running by scheduler every 1m
                    zeppelin.zql.get(job.id, function(c, d){
                        if(c==200){
                            if(d.status=="RUNNING" || d.dateFinished!=job.dateFinished){
                                if(controller.get('dirty')==0){ // auto refresh in only clean state
                                    controller.set("currentJob", d);
                                }
                            }
                        }
                    });
                    controller.get('controllers.zql').send('updateJob');
                }

		// auto height visualizer
		if(new Date().getSeconds() % 1 == 0){ // refreshing every 1 sec
		    // jquery iframe auto height plugin does not work in this place
		    // jQuery('#visualizationContainer > iframe').iframeAutoHeight();

		    $('#visualizationContainer > iframe').each(function(idx, el){
			if(!el.contentWindow.document.body) return;
			var height = el.contentWindow.document.body.scrollHeight;
			$(el).height(height);
		    });
		}
            } else if(job.status=="RUNNING"){
                if(new Date().getSeconds() % 1 == 0){ // refreshing every 1 sec
                    controller.send('loadJob', job.id);
                }
            } else if(job.status=="FINISHED"){
		// historyis made when job finishes. update history list
		controller.send('updateHistory');
            } else if(job.status=="ERROR"){
            } else if(job.status=="ABORT"){
            }
        } 
    }
});



App.ReportController = App.ApplicationController.extend();

App.ReportLinkController = App.ApplicationController.extend({
    currentJob : undefined
});


App.ZanController = App.ApplicationController.extend({
/**
    runningJobs : undefined,
    searchResult : undefined,
    waitForComplete : undefined,
*/

    actions : {
	search : function(queryString){
	    var controller = this;
	    zeppelin.zan.search({ queryString:queryString }, function(c, d){
		if (c==200) {
		    controller.set("searchResult", d);
		} else {
		    // error
		}
	    }, this);
	},

	update : function(){
	    var controller = this;
	    zeppelin.zan.update(function(c, d){
		if(c==200){
		    controller.send('waitForComplete');
		} else {
		    // error
		}
	    }, this);
	},

	install : function(libName){
	    var controller = this;
	    zeppelin.zan.install(libName, function(c, d){
		if (c==200) {
		    controller.send('waitForComplete');
		} else {
		    // error
		}
	    }, this);
	},

	uninstall : function(libName){
	    var controller = this;
	    zeppelin.zan.uninstall(libName, function(c, d){
		if (c==200) {
		    controller.send('waitForComplete');
		} else {
		    // error
		}
	    }, this);
	},

	upgrade : function(libName){
	    var controller = this;
	    zeppelin.zan.upgrade(libName, function(c, d){
		if (c==200) {
		    controller.send('waitForComplete');
		} else {
		    // error
		}
	    }, this);
	},


	
	/**
	 * wait for any running job complete
	 */
	waitForComplete : function(){
	    var controller = this;
	    controller.set("waitForComplete", true);

	    zeppelin.zan.running(function(c, d){
		if (c==200) {
		    console.log("running = %o", d);
		    controller.set("runningJobs", d);

		    if(d && d.length>0){
			setTimeout(function(){
			    controller.send('waitForComplete');
			}, 1000);
		    } else {
			controller.set("waitForComplete", false);
			controller.send("search", "*");
		    }
		} else {
		    // error
		}
	    }, this);
	}
    }
});

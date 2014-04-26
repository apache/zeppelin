
App.ApplicationController = Ember.Controller.extend(Ember.Evented, {
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
            zeppelin.zql.getTree(function(c, tree){
                if(c==200){
                    zeppelin.zql.list(function(c, list){
                        if(c==200){
                            var jobs = [];
                            // first traverse tree

                            function findJobByIdFromList(id){
                                for (var i=0; i<list.length; i++) {
                                    if (list[i].id==id) {
                                        var ret = list[i];
                                        list.splice(i, 1);
                                        return ret;
                                    }
                                }
                                return undefined;
                            }

                            function constructTree(sourceTree, targetTree){
                                for(var i=0; i<sourceTree.length; i++){
                                    var job = findJobByIdFromList(sourceTree[i].id);
                                    if (!job) continue;

                                    if (sourceTree[i].children) {
                                        job.children = [];
                                        constructTree(sourceTree[i].children, job.children);
                                    }
                                    targetTree.push(job);
                                }
                            };
                            constructTree(tree, jobs);

                            for (var i=0; i<list.length; i++) {
                                jobs.push(list[i]);
                            }

                            controller.set("runningJobs", jobs);
                        } else {
                            zeppelin.alert("Can't get job list");
                        }
                    }, this);
                } else {
                    zeppelin.alert("Can't get job tree information");
                }
            }, this);
        }
    }
});

App.ZqlIndexController = App.ZqlController.extend({
    needs: ['zql'],
});

App.ZqlEditController = App.ZqlController.extend({
    dirty : 0,
    dirtyFlag : {
        CLEAN : 0,
        ZQL : 1,
        PARAMS : 2
    },
    dryrun : true,
    currentJob : undefined,
    historyId : undefined,
    zql : undefined,
    params : undefined,
    needs: ['zql'],

    printInfo : function(arg){
        var controller = this;
        controller.set('jobMessage', arg);
        console.log("info : %o", arg);
        if(controller._printInfoT){
            clearTimeout(controller._printInfoT);
        }

        controller._printInfoT = setTimeout(function(){
            controller.set('jobMessage', "");
        }, 5000);
    },

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
                        zeppelin.alert("Error: Invalid Job");
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

                // TODO: move button controling to view
                $('#zqlRunButton').text("Running ...");
                $('#zqlRunButton').addClass('disabled');
                $('#zqlRunButton').prop('disabled', true);
                zeppelin.zql.set(jobid, jobName, zql, undefined, jobCron, function(c, d){
                    if(c!=200){
                        $('#zqlRunButton').text("Run");
                        $('#zqlRunButton').prop('disabled', false);
                        zeppelin.alert("Error: Invalid Job");
                    } else {
                        $('#zqlRunButton').prop('disabled', false);
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
            var controller = this;
            if(model==null) return;
            if(model.status=="READY"){
                if(this.get('dirty')){
                    zeppelin.zql.set(model.id, this.get("jobName"), this.get('zql'), undefined, this.get('jobCron'), function(c, d){
                        if(c==200){
                            console.log("job %o saved", model.id);
                        } else {
                            zeppelin.alert("Can't save job");
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

            // update cron
            var cronPreset = jobCronEditor.cronPreset
            var cronValue = jobCronEditor.editable('getValue', true)
            var found = false;
            for(var i=0; i<jobCronEditor.cronPreset.length; i++){
                if(jobCronEditor.cronPreset[i].id==cronValue){
                    found = true;
                    break;
                }
            }
            if(found==false){
                jobCronEditor.cronPreset.push({id:cronValue, text:cronValue});
                jobCronEditor.editable('option', 'source', jobCronEditor.cronPreset);
                jobCronEditor.editable('setValue', cronValue);
            }

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
            var controller = this;
            var job = this.get('currentJob');
	    var historyId = this.get('historyId');
            if(historyId) return;
            if(job.status!="READY" && job.status!="FINISHED" && job.status!="ERROR" && job.status!="ABORT") return;

            zeppelin.zql.setName(job.id, jobName, function(c, d){
                if(c==200){
                    controller.set('jobName', jobName);
                    controller.printInfo("change saved");
                } else {
                    zeppelin.alert("Error! Can't save change");
                }
            }, this);
        },

        zqlJobCronChanged : function(jobCron){
            var controller = this;
            var job = this.get('currentJob');
	    var historyId = this.get('historyId');
            if(historyId) return;
            if(job.status!="READY" && job.status!="FINISHED" && job.status!="ERROR" && job.status!="ABORT") return;
            zeppelin.zql.setCron(job.id, jobCron, function(c, d){
                if(c==200){
                    controller.set('jobCron', jobCron);
                    controller.printInfo("change saved");
                } else {
                    zeppelin.alert("Error! Can't save change");
                }
            }, this);
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
	    var historyId = this.get('historyId');
            if(job==null) return;
            if(!historyId){
                if(job.status=="READY" || job.status=="FINISHED" || job.status=="ERROR" || job.status=="ABORT"){
                    // auto save every 10 sec
                    if(new Date().getSeconds() % 10 == 0 && (this.get('dirty') & this.get('dirtyFlag').ZQL)){

                        // TODO display saving... -> saved message
                        zeppelin.zql.setZql(job.id, editor.getValue(), function(c, d){
                            if(c==200){
                                this.set('dirty', (this.get('dirty') & ~this.get('dirtyFlag').ZQL));
                                this.printInfo("autosave completed");
                            } else {
                                zeppelin.alert("autosave failed");
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

    getApplications : function() {
    	var controller = this;
    	zeppelin.zan.getApplications(function(c, d){
			if (c==200) {
			    controller.set("zanAppList", d);
			} else {
			    // error
			}
	    }, this);
    },

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
		if(c==202){
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
			controller.send("getApplications");
			//controller.send("search", "*");
		    }
		} else {
		    // error
		}
	    }, this);
	}
    }
});

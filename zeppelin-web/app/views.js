//require('views/application');

App.IndexView = Ember.View.extend({
    layoutName: 'default_layout',
});

App.ZqlView = Ember.View.extend({
    layoutName: 'default_layout',
});

App.ZqlEditView = Ember.View.extend({
    jobNameEditor : undefined,
    jobCronEditor : undefined,
    editor : undefined,
    currentModel : undefined,

    modelChanged : function(){      // called when model is changed
        var controller = this.get("controller");
        var model = controller.get('currentJob');
        var historyId = controller.get('historyId');
        var editor = this.get('editor');
        var jobNameEditor = this.get('jobNameEditor');
        var jobCronEditor = this.get('jobCronEditor');

        controller.send("beforeChangeJob", this.get('currentModel'), jobNameEditor, editor, jobCronEditor);
        this.set('currentModel', model);

        if( editor == null ) { return; }
        if( editor.getValue() != model.zql ) {
            editor.setValue(model.zql);
        }

        console.log("Current job=%o", model);

        if (model.jobName && model.jobName != "") {
            if (jobNameEditor.editable('getValue', true) != model.jobName) {
                jobNameEditor.editable('setValue', model.jobName);
            }
        } else {
            jobNameEditor.editable('setValue', model.id);
        }

        // update history list
        controller.send('updateHistory', model.id);

        if(model.cron && model.cron!=""){
            if(jobCronEditor.editable('getValue', true)!=model.cron){
                jobCronEditor.editable('setValue', model.cron);
            }
        } else {
            jobCronEditor.editable('setValue', "");
        }

        // clear visualizations
        $('#visualizationContainer iframe').remove();
        $('#visualizationContainer div').remove();
        $('#msgBox div').remove();

        if(model.status=="READY"){
            editor.setReadOnly(false);
            jobNameEditor.editable('enable');
            jobCronEditor.editable('enable');
            $('#zqlRunButton').text("Run");
            //$('#zqlRunButton').removeClass('disabled');                   
        } else if(model.status=="RUNNING"){
            $('#zqlRunButton').text("Running ...");
            //$('#zqlRunButton').addClass('disabled');
            //$('#zqlRunButton').prop('disabled', true);
            editor.setReadOnly(true);
            jobNameEditor.editable('disable');
            jobCronEditor.editable('disable');
        } else if(model.status=="FINISHED"){
            $('#zqlRunButton').text("Run");
            //$('#zqlRunButton').addClass('disabled');
            //$('#zqlRunButton').prop('disabled', true);
	    if(!historyId){
		editor.setReadOnly(false);
		jobNameEditor.editable('enable');
		jobCronEditor.editable('enable');
	    } else {
		editor.setReadOnly(true);
		jobNameEditor.editable('disable');
		jobCronEditor.editable('disable');
		$('#zqlRunButton').text("Finished");
	    }

            // draw visualization if there's some
            if(model.zqlPlans){
                var planModel;
                for(var i=0; i<model.zqlPlans.length; i++){
                    planModel = model.zqlPlans[i];
                    if(!planModel) { continue;}

                    var planStack = [];
                    for(var p = planModel; p!=undefined; p = p.prev){
                        planStack.unshift(p);
                    }

                    var plan;
                    for(var j=0; j<planStack.length; j++){
                        plan = planStack[j];
                        if(!plan || !plan.webEnabled) { continue; }
                        if(!plan.result || !plan.result.columnDef || plan.result.columnDef.length==0) {continue;}

                        console.log("Displaying plan %o", plan);
                        var planInfo = (plan.libName) ? plan.libName : plan.query;
                        if(planInfo.length>1 && planInfo[0]=='!'){
                            planInfo = planInfo.substring(1);
                        }
                        $('#visualizationContainer').append('<div class="visTitle">'+planInfo+"</div>");
                        $('<iframe />', {                               
                            name : plan.id,
                            id : plan.id,
                            src : zeppelin.getWebResourceURL(model.id, historyId, plan.id),
                            scrolling : 'auto'
                        }).appendTo('#visualizationContainer');

                        $('#'+plan.id).load(function(c,d){
                            //console.log("iframe %o %o", c,d);
                        });
                    }}
                

                jQuery('iframe').iframeAutoHeight();
            }


        } else if(model.status=="ERROR"){
            $('#zqlRunButton').text("Run");
            for(var i=0; i< model.error.rows.length; i++){
                $('#msgBox').append("<div>"+model.error.rows[0][3]+"</div>");
            }
            //$('#zqlRunButton').removeClass('disabled');
            editor.setReadOnly(false);
            jobNameEditor.editable('enable');
            jobCronEditor.editable('enable');
        } else if(model.status=="ABORT"){
            $('#zqlRunButton').text("Run");
            //$('#zqlRunButton').removeClass('disabled');
            editor.setReadOnly(false);
            jobNameEditor.editable('enable');
            jobCronEditor.editable('enable');
        }
        controller.send("afterChangeJob", model, jobNameEditor, editor, jobCronEditor);
    }.observes('controller.currentJob'),


    historyListUpdated : function(){
        var controller = this.get("controller");
        var historyList = controller.get('historyList');
        var historyId = controller.get('historyId');
        var model = controller.get('currentJob');
        var jobHistorySelector = $('#zqlJobHistory');

	if(model==undefined){
	    jobHistorySelector.editable('destroy');
	    jobHistorySelector.off('save');
	    return;
	}

        var source = [];
        source.push({value:"", text: "Latest"});
        for(var k in historyList){
            source.push({value:k, text: k});
        }

        jobHistorySelector.editable('destroy');
	jobHistorySelector.off('save');

        $('#zqlJobHistory').editable({
            value : historyId,
            source : source,
            showbuttons : false
        });
        
        $('#zqlJobHistory').editable('setValue', (historyId==undefined) ? "" : historyId);
        $('#zqlJobHistory').on('save', function(e, params) {
            controller.send("zqlJobHistoryChanged", model.id, params.newValue);
        });
    }.observes('controller.historyList'), // updated from controller:updateHistory


    didInsertElement : function(){ // when it is first time of loading this view, jobChanged can not be observed
        var controller = this.get("controller");
        var model = controller.get('currentJob');
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

        var jobNameEditor = $('#zqlJobName');
        jobNameEditor.editable();
        jobNameEditor.on('save', function(e, params) {
            controller.send("zqlJobNameChanged", params.newValue);  
        });
        this.set('jobNameEditor', jobNameEditor);

        var jobCronEditor = $('#zqlJobCron');
        $('#zqlJobCron').editable({
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
        jobCronEditor.on('save', function(e, params) {
            controller.send("zqlJobCronChanged", params.newValue);  
        });             
        this.set('jobCronEditor', jobCronEditor);

	// bootstrap confirmation plugin http://ethaizone.github.io/Bootstrap-Confirmation
        $('#zqlDeleteButton').confirmation({
	    title : "Delete?",
	    btnOkLabel : "Delete",
	    btnCancelLabel : "Cancel",
	    href : "/#/zql",
	    popout : true,
	    onConfirm : function(){
		controller.send("deleteJob");
	    }
	});

        var editorLoop = function(){
            setTimeout(function(){
                editorLoop();
            }, 1000);

            controller.send("loop", jobNameEditor, editor, jobCronEditor);
        };
        editorLoop();
    },

    willClearRender: function(){
        var controller = this.get("controller");
        var model = controller.get("currentJob");
        var view = this;
        var editor = ace.edit("zqlEditor");
        var jobNameEditor = this.get('jobNameEditor');
        controller.send('beforeChangeJob', model, jobNameEditor, editor);
        this.set('currentModel', null);
    },

    zqlParamView : Ember.View.extend({
        templateName : 'zql/param',
        paramInfo : null,

        modelChanged : function() {
            var controller = this.get("controller");
            var job = controller.get('currentJob');
            //console.log("model changes %o", job);
            var planStack = [];

            if(job.zqlPlans){
                var planModel;
                for(var i=0; i<job.zqlPlans.length; i++){
                    planModel = job.zqlPlans[i];
                    if(!planModel) { continue; }

                    for(var p = planModel; p!=undefined; p = p.prev){
                        //console.log("P=%o", p.paramInfos);
                        planStack.unshift(p);
                    }
                }
            }

            //console.log("paramInfo=%o", planStack);
            this.set('paramInfo', planStack);
        }.observes('controller.currentJob'),

        didInsertElement : function(){
            var controller = this.get("controller");
            var model = controller.get("currentJob");
            var view = this;
        },
        willClearRender: function(){
            var controller = this.get("controller");
            var model = controller.get("currentJob");
            var view = this;
            this.set('paramInfo', null);
        }
    })
});



App.ReportLinkView = Ember.View.extend({
    layoutName: 'report_layout',

    modelChanged : function(){      // called when model is changed
        var controller = this.get("controller");
        var historyId = controller.get('historyId');
        var model = controller.get('currentJob');
        if (!model) {
            return null;
        }
        console.log("report %o", model);

        var jobNameEditor = $('#zqlJobName');
        if(model.jobName && model.jobName!=""){
            if(jobNameEditor.text()!=model.jobName){
                jobNameEditor.html(model.jobName);
            }
        } else {
            jobNameEditor.html(model.id);
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
                        if(!plan.result || !plan.result.columnDef || plan.result.columnDef.length==0) continue;
                        var planInfo = (plan.libName) ? plan.libName : plan.query;
                        if(planInfo.length>1 && planInfo[0]=='!'){
                            planInfo = planInfo.substring(1);
                        }
                        $('#visualizationContainer').append('<div class="visTitle">'+planInfo+"</div>");
                        $('<iframe />', {                               
                            name : plan.id,
                            id : plan.id,
                            src : zeppelin.getWebResourceURL(model.id, historyId, plan.id),
                            scrolling : 'auto'
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
        
    }.observes('controller.currentJob'),
});


App.ZanView = Ember.View.extend({
    layoutName: 'default_layout',

    didInsertElement : function(){
        var controller = this.get("controller");
	controller.send('waitForComplete');
    },

    update : function(){
	console.log("update");
    },

    searchResultChanged : function(){
        var controller = this.get("controller");
        var searchResult = controller.get('searchResult');
	console.log("searchResult=%o", searchResult);
	this.set('searchResult', searchResult);
    }.observes('controller.searchResult'),

    waitForCompleteChanged : function(){
        var controller = this.get("controller");
	var waitForComplete = controller.get('waitForComplete');
	var runningJobs = controller.get('runningJobs');
	console.log("waitForComplete=%o, %o", waitForComplete, runningJobs);
	this.set('waitForComplete', waitForComplete);
	this.set('runningJobs', runningJobs);
    }.observes('controller.waitForComplete'),

    willClearRender: function(){
    }
});


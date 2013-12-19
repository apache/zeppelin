//require('views/application');

App.IndexView = Ember.View.extend({
    layoutName: 'default_layout',
});

App.ZqlView = Ember.View.extend({
    layoutName: 'default_layout',
});

App.ZqlEditView = Ember.View.extend({
    sessionNameEditor : undefined,
    sessionCronEditor : undefined,
    editor : undefined,
    currentModel : undefined,

    modelChanged : function(){      // called when model is changed
        var controller = this.get("controller");
        var model = controller.get('currentSession');
        var historyId = controller.get('historyId');
        var editor = this.get('editor');
        var sessionNameEditor = this.get('sessionNameEditor');
        var sessionCronEditor = this.get('sessionCronEditor');

        controller.send("beforeChangeSession", this.get('currentModel'), sessionNameEditor, editor, sessionCronEditor);
        this.set('currentModel', model);

        if( editor == null ) { return; }
        if( editor.getValue() != model.zql ) {
            editor.setValue(model.zql);
        }

        console.log("Current session=%o", model);

        if (model.jobName && model.jobName != "") {
            if (sessionNameEditor.editable('getValue', true) != model.jobName) {
                sessionNameEditor.editable('setValue', model.jobName);
            }
        } else {
            sessionNameEditor.editable('setValue', model.id);
        }

        // update history list
        console.log("Sned update history");
        controller.send('updateHistory', model.id);

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
                            scrolling : 'no',
                            /*,
                              frameborder : "0",
                              height : "100%",
                              width : "100%"*/
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


    historyListUpdated : function(){
        var controller = this.get("controller");
        var historyList = controller.get('historyList');
        var historyId = controller.get('historyId');
        var model = controller.get('currentSession');
        var sessionHistorySelector = $('#zqlSessionHistory');

	if(model==undefined){
	    sessionHistorySelector.editable('destroy');
	    sessionHistorySelector.off('save');
	    return;
	}

        console.log("HistoryListUpdated!!! %o %o", model, historyId);

        var source = [];
        source.push({value:"", text: "Latest"});
        for(var k in historyList){
            source.push({value:k, text: k});
        }

        sessionHistorySelector.editable('destroy');
	sessionHistorySelector.off('save');

        $('#zqlSessionHistory').editable({
            value : historyId,
            source : source,
            showbuttons : false
        });
        
	console.log("Source=%o", source);

        $('#zqlSessionHistory').editable('setValue', (historyId==undefined) ? "" : historyId);
        $('#zqlSessionHistory').on('save', function(e, params) {
            controller.send("zqlSessionHistoryChanged", model.id, params.newValue);
        });

    }.observes('controller.historyList'), // updated from controller:updateHistory


    didInsertElement : function(){ // when it is first time of loading this view, sessionChanged can not be observed
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
        sessionNameEditor.editable();
        sessionNameEditor.on('save', function(e, params) {
            controller.send("zqlSessionNameChanged", params.newValue);  
        });
        this.set('sessionNameEditor', sessionNameEditor);

        var sessionCronEditor = $('#zqlSessionCron');
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

        modelChanged : function() {
            var controller = this.get("controller");
            var session = controller.get('currentSession');
            //console.log("model changes %o", session);
            var planStack = [];

            if(session.zqlPlans){
                var planModel;
                for(var i=0; i<session.zqlPlans.length; i++){
                    planModel = session.zqlPlans[i];
                    if(!planModel) { continue; }

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



App.ReportLinkView = Ember.View.extend({
    layoutName: 'report_layout',

    modelChanged : function(){      // called when model is changed
        var controller = this.get("controller");
        var historyId = controller.get('historyId');
        var model = controller.get('currentSession');
        if (!model) {
            return null;
        }
        console.log("report %o", model);

        var sessionNameEditor = $('#zqlSessionName');
        if(model.jobName && model.jobName!=""){
            if(sessionNameEditor.text()!=model.jobName){
                sessionNameEditor.html(model.jobName);
            }
        } else {
            sessionNameEditor.html(model.id);
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

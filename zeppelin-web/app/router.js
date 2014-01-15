var App = require('app');


var Zeppelin = require('zeppelin').Zeppelin,
zeppelin = new Zeppelin();

App.Router = Ember.Router.extend({
    enableLogging: true,
});



App.Router.map(function(){
    this.resource('zql', function(){
        this.route('edit', {path:'/:jobid'});
        this.route('edit', {path:'/:jobid/:historyid'});
    });
    this.resource('report', function(){
        this.route('link', {path:'/:jobid'});
        this.route('link', {path:'/:jobid/:historyid'});
    });
    this.resource('zan', function(){
    });
});



App.ZqlRoute = Ember.Route.extend({
    model : function(params){	
        return params;
    },
    setupController : function(controller, model){
        zeppelin.zql.list(function(c, resp){
            if ( c == 200 ) {
                controller.set('runningJobs', resp);
            }
        });
    }
});


// ZqlEidt ---------------------------------------
App.ZqlEditRoute = App.ZqlRoute.extend({
    model : function(params){
	return params;
    },

    setupController : function(controller, model){	
        if(model.historyid==undefined || model.historyid==""){
            zeppelin.zql.get(model.jobid, function(c, d){
                controller.set('historyId', model.historyid);
                controller.set('currentJob', d);  // currentJob is observed by ZqlEditView
            }, this);
        } else {
            zeppelin.zql.getHistory(model.jobid, model.historyid, function(c, d){
                controller.set('historyId', model.historyid);
                controller.set('currentJob', d);  // currentJob is observed by ZqlEditView
            }, this);      
        }
    }
});

// Report  --------------------------------------
App.ReportRoute = Ember.Route.extend({
    model : function(params){
        return params;
    },
    setupController : function(controller, model){
    }
});

App.ReportLinkRoute = App.ReportRoute.extend({
    model : function(params){
        return params;
    },
    setupController : function(controller, model){
        if(model.historyid==undefined || model.historyid==""){
            zeppelin.zql.get(model.jobid, function(c, d){
                controller.set('historyId', model.historyid);
                controller.set('currentJob', d);  // currentJob is observed by ZqlEditView
            }, this);
        } else {
            zeppelin.zql.getHistory(model.jobid, model.historyid, function(c, d){
                controller.set('historyId', model.historyid);
                controller.set('currentJob', d);  // currentJob is observed by ZqlEditView
            }, this);      
        }
    }
});


// --------- zan --------------
App.ZanRoute = Ember.Route.extend({
    model : function(params){	
        return params;
    },
    setupController : function(controller, model){
    }
});

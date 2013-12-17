var App = require('app');


var Zeppelin = require('zeppelin').Zeppelin,
    zeppelin = new Zeppelin();

App.Router = Ember.Router.extend({
  enableLogging: true,
});



App.Router.map(function(){
  this.resource('zql', function(){
	this.route('edit', {path:'/:sessionid'});
	this.route('edit', {path:'/:sessionid/:historyid'});
  });
  this.resource('report', function(){
	this.route('link', {path:'/:sessionid'});
	this.route('link', {path:'/:sessionid/:historyid'});
  });
});



App.ZqlRoute = Ember.Route.extend({
  model : function(params){
	return params;
  },
  setupController : function(controller, model){
    zeppelin.zql.list(function(c, resp){
	  if ( c == 200 ) {
		controller.set('runningSessions', resp);
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
    zeppelin.zql.get(model.sessionid, function(c, d){
      controller.set('currentSession', d);
      controller.set('historyId', model.historyid);
    }, this);
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
	zeppelin.zql.get(model.sessionid, function(c, d){
	  controller.set('currentSession', d);
	  controller.set('historyId', model.historyid);
	}, this);
  }
});



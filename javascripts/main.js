
window.App = Ember.Application.create({
});

App.Router.map(function(){
    this.route("zengine", { path:"/zengine"});
    this.route("zql", { path:"/zql"});
    this.route("zan", { path:"/zan"});
    this.route("download", { path:"/download"});
    this.route("license", { path:"/license"});
    this.route("docZeppelinGettingstarted", { path:"/doc/zeppelin/getting_started"});
    this.route("docZeppelinInstall", { path:"/doc/zeppelin/install"});
});


App.ApplicationController = Ember.Controller.extend({
    currentPathChanged: function() {
	var page;
	window.scrollTo(0, 0);
	// window.location gets updated later in the current run loop, so we will
	// wait until the next run loop to inspect its value and make the call
	// to track the page view
	Ember.run.next(function() {
	    // Track the page in Google Analytics
	    if (!Ember.isNone(ga)) {
		// Assume that if there is a hash component to the url then we are using
		// the hash location strategy. Otherwise, we'll assume the history
		// strategy.
		page = window.location.hash.length > 0 ?
		    window.location.hash.substring(1) :
		    window.location.pathname + window.location.search;
		ga('send', 'pageview', page);
	    }
	});
    }.observes('currentPath')

});




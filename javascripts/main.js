
window.App = Ember.Application.create();

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
    
});




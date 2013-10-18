
window.App = Ember.Application.create();

App.Router.map(function(){
    this.route("zengine", { path:"/zengine"});
    this.route("zql", { path:"/zql"});
    this.route("zan", { path:"/zan"});
    this.route("docZeppelinGettingstarted", { path:"/doc/zeppelin/getting_started"});
    this.route("docZeppelinInstall", { path:"/doc/zeppelin/install"});
    this.route("docZeppelinConfigure", { path:"/doc/zeppelin/configure"});
});


App.ApplicationController = Ember.Controller.extend({
    
});




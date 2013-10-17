
window.App = Ember.Application.create();

App.Router.map(function(){
    this.route("zengine", { path:"/zengine"});
    this.route("zql", { path:"/zql"});
    this.route("zan", { path:"/zan"});
});


App.ApplicationController = Ember.Controller.extend({

});



App.ZengineView = Ember.View.extend({
    templateName : 'zengine',
});



App.ZQLView = Ember.View.extend({
    templateName : 'zql',
});

App.ZANView = Ember.View.extend({
    templateName : 'zan',
});



window.App = Ember.Application.create();

App.Router.map(function(){
    this.route("core", { path:"/core"});
});


App.ApplicationController = Ember.Controller.extend({

});



App.CoreView = Ember.View.extend({
    templateName : 'core',
    
    firstName: "einstein"
});



App.ZQLView = Ember.View.extend({
    templateName : 'zql',
});

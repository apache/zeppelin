package com.nflabs.zeppelin.web;

import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.request.mapper.parameter.PageParameters;


public class Base  extends WebPage{

    public Base(final PageParameters parameters) {
    	super(parameters);
    }
    
    public void renderHead(IHeaderResponse response){
    	// jquery
    	response.render(JavaScriptHeaderItem.forUrl("https://ajax.googleapis.com/ajax/libs/jquery/2.0.3/jquery.min.js"));
    	//response.render(JavaScriptHeaderItem.forUrl("https://ajax.googleapis.com/ajax/libs/jqueryui/1.10.3/jquery-ui.js"));

    	// jquery plugin for iframe autoheight
    	response.render(JavaScriptHeaderItem.forUrl("js/lib/browser.js"));
    	response.render(JavaScriptHeaderItem.forUrl("js/lib/iframe_auto_height.js"));
    	
    	// ember
    	response.render(JavaScriptHeaderItem.forUrl("js/lib/handlebars-1.0.0.js"));
    	response.render(JavaScriptHeaderItem.forUrl("js/lib/ember-1.0.0.js"));
    	
    	// bootstrap
    	response.render(JavaScriptHeaderItem.forUrl("http://netdna.bootstrapcdn.com/bootstrap/3.0.0/js/bootstrap.min.js"));	
    	response.render(CssHeaderItem.forUrl("http://netdna.bootstrapcdn.com/bootstrap/3.0.0/css/bootstrap.min.css", "screen"));
    	response.render(CssHeaderItem.forUrl("http://netdna.bootstrapcdn.com/bootstrap/3.0.0/css/bootstrap-theme.min.css"));

    	// ace
    	response.render(JavaScriptHeaderItem.forUrl("js/lib/ace/ace.js"));
    	
    	// x-editable
    	response.render(JavaScriptHeaderItem.forUrl("http://cdnjs.cloudflare.com/ajax/libs/x-editable/1.5.0/bootstrap3-editable/js/bootstrap-editable.min.js"));
    	response.render(CssHeaderItem.forUrl("http://cdnjs.cloudflare.com/ajax/libs/x-editable/1.5.0/bootstrap3-editable/css/bootstrap-editable.css"));
    	
    	// zeppelin-dependency
    	response.render(JavaScriptHeaderItem.forUrl("js/lib/json2.js"));
    	
    	// zeppelin
    	response.render(CssHeaderItem.forUrl("css/zeppelin.css"));
    	response.render(JavaScriptHeaderItem.forUrl("js/zeppelin.js"));
    	
    	response.render(JavaScriptHeaderItem.forUrl("app/main.js"));
    	
    	if(isDevMode()){
    		response.render(JavaScriptHeaderItem.forScript("var zeppelinMode=\"development\";\n", "configure-zeppelin"));
    	}
    	response.render(JavaScriptHeaderItem.forScript("var zeppelin = new Zeppelin();", "init-zeppelin"));
    }
    
    
	protected boolean isDevMode(){
		return RuntimeConfigurationType.DEVELOPMENT.equals(getApplication().getConfigurationType());
	}
}

package com.nflabs.zeppelin.web;

import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.request.mapper.parameter.PageParameters;


public class Base  extends WebPage{

    public Base(final PageParameters parameters) {
    	super(parameters);
    }
    
    public void renderHead(IHeaderResponse response){
    	// jquery
    	response.renderJavaScriptReference("https://ajax.googleapis.com/ajax/libs/jquery/2.0.3/jquery.min.js");
    	//response.renderJavaScriptReference("https://ajax.googleapis.com/ajax/libs/jqueryui/1.10.3/jquery-ui.js");
    	
    	// bootstrap
    	response.renderJavaScriptReference("http://netdna.bootstrapcdn.com/bootstrap/3.0.0/js/bootstrap.min.js");	
    	response.renderCSSReference("http://netdna.bootstrapcdn.com/bootstrap/3.0.0/css/bootstrap.min.css", "screen");
    	response.renderCSSReference("http://netdna.bootstrapcdn.com/bootstrap/3.0.0/css/bootstrap-theme.min.css");
    	
    	// zeppelin-dependency
    	response.renderJavaScriptReference("js/lib/json2.js");
    	
    	// zeppelin
    	response.renderCSSReference("css/zeppelin.css");
    	response.renderJavaScriptReference("js/zeppelin.js");
    	
    	if(isDevMode()){
    		response.renderJavaScript("var zeppelinMode=\"development\";\n", "configure-zeppelin");
    	}
    	response.renderJavaScript("var zeppelin = new Zeppelin();", "init-zeppelin");
    	


    }
    
    
	protected boolean isDevMode(){
		return RuntimeConfigurationType.DEVELOPMENT.equals(getApplication().getConfigurationType());
	}
}

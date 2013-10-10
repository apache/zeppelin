package com.nflabs.zeppelin.web;

import org.apache.wicket.protocol.http.WebApplication;

import com.nflabs.zeppelin.web.analyze.Analyze;



public class WicketApplication extends WebApplication{
	/**
	 * @see org.apache.wicket.Application#getHomePage()
	 */
	@Override
	public Class<Home> getHomePage()
	{
		return Home.class;
	}

	/**
	 * @see org.apache.wicket.Application#init()
	 */
	@Override
	public void init()
	{
		super.init();
		mountPage("home", Home.class);
		mountPage("analyze", Analyze.class);
		mountPage("analyze/new", com.nflabs.zeppelin.web.analyze.New.class);
	}
	
}

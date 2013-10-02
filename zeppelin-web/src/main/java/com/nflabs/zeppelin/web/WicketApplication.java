package com.nflabs.zeppelin.web;

import org.apache.wicket.protocol.http.WebApplication;



public class WicketApplication extends WebApplication{
	/**
	 * @see org.apache.wicket.Application#getHomePage()
	 */
	@Override
	public Class<HomePage> getHomePage()
	{
		return HomePage.class;
	}

	/**
	 * @see org.apache.wicket.Application#init()
	 */
	@Override
	public void init()
	{
		super.init();
		mountPage("home", HomePage.class);
	}
	
}

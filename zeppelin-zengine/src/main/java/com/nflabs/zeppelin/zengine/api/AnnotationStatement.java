package com.nflabs.zeppelin.zengine.api;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.zengine.ParamInfo;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.Zengine;

public class AnnotationStatement extends Q {

	public AnnotationStatement(String stmt, Zengine z, ZeppelinDriver driver) throws ZException{
		super(stmt, z, driver);
	}

	@Override
	public String getQuery() throws ZException {
		return super.getQuery();
	}

	@Override
	public List<URI> getResources() throws ZException {
		return null;
	}

	@Override
	public String getReleaseQuery() throws ZException {
		return null;
	}

	@Override
	public InputStream readWebResource(String path) throws ZException {
		return null;
	}

	@Override
	public boolean isWebEnabled() {
		return false;
	}

	@Override
	protected void initialize() throws ZException {
	}

	@Override
	protected Map<String, ParamInfo> extractParams() throws ZException {
		return null;
	}
}

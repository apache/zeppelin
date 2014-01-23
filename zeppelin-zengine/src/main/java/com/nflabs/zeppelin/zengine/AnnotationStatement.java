package com.nflabs.zeppelin.zengine;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class AnnotationStatement extends Z {
	private String stmt;

	public AnnotationStatement(String stmt){
		this.stmt = stmt;
	}

	@Override
	public String getQuery() throws ZException {
		return stmt;
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

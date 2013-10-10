package com.nflabs.zeppelin.web.analyze;

import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import com.nflabs.zeppelin.web.Base;

public class Analyze extends Base{

	public Analyze(PageParameters parameters) {
		super(parameters);
	}

	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);
		response.renderJavaScriptReference("js/analyze.js");
	}
}

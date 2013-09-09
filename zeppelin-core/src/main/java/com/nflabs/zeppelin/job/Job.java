package com.nflabs.zeppelin.job;

import java.io.File;

public class Job {

	private File[] resources;
	private String script;
	private JobId jobId;

	public Job(JobId jobId, String script, File [] resources){
		this.jobId = jobId;
		this.script = script;
		this.resources = resources;
	}

	public File[] getResources() {
		return resources;
	}

	public void setResources(File[] resources) {
		this.resources = resources;
	}

	public String getScript() {
		return script;
	}

	public void setScript(String script) {
		this.script = script;
	}

	public JobId getJobId() {
		return jobId;
	}

	public void setJobId(JobId jobId) {
		this.jobId = jobId;
	}
	

}

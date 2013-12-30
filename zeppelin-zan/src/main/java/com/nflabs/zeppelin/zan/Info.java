package com.nflabs.zeppelin.zan;

public class Info {
	private Meta meta;
	private String name;
	private boolean installed;
	private String commit;

	public Info(String name, Meta meta, boolean installed, String commit) {
		super();
		this.name = name;
		this.meta = meta;
		this.installed = installed;
		this.commit = commit;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Meta getMeta() {
		return meta;
	}

	public void setMeta(Meta meta) {
		this.meta = meta;
	}

	public boolean isInstalled() {
		return installed;
	}

	public void setInstalled(boolean installed) {
		this.installed = installed;
	}

	public boolean isUpdateAvailable() {
		if (!isInstalled()) {
			return false;
		}				
		
		return (meta.commit.compareTo(commit)==0) ? false : true; 
	}

}

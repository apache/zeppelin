package com.nflabs.zeppelin.zan;

public class Info {
	public static enum Status{
		UNKNOWN,
		INSTALLING,
		INSTALLED,
		UNINSTALLED,
		UNINSTALLING,
		UPDATEAVAILABLE,
		UPDATING,
		NOT_MANAGED,          // manually installed by user. not managed by ZAN
	}
	private String name;
	private Status status;
	private String commit;
	private String path;
	private String url;

	public Info(String name, String path, boolean installed, boolean updateAvailable, String commit) {
		this.name = name;
		this.path = path;
		this.commit = commit;
		
		if (installed) {
			if (updateAvailable) {
				status = Status.UPDATEAVAILABLE;
			} else {
				status = Status.INSTALLED;
			}
		} else {
			status = Status.UNINSTALLED;
		}
		
	}
	
	public Info(String name, String path) {
		this.name = name;
		this.path = path;
		
		status = Status.NOT_MANAGED;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Status getStatus() {
		return status;
	}

	public String getPath(){
		return path;
	}

	public void setPath(String path) {
		this.path = path;		
	}

	public String getUrl(){
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

}

package com.nflabs.zeppelin.zan;

public class Info {
	public static enum Status{
		UNKNOWN,
		INSTALLING,
		INSTALLED,
		UNINSTALLED,
		UNINSTALLING,
		UPDATEAVAILABLE,
		UPUPDATING				
	}
	private Meta meta;
	private String name;
	private Status status;
	private String commit;

	public Info(String name, Meta meta, boolean installed, String commit) {
		super();
		this.name = name;
		this.meta = meta;
		this.commit = commit;

		if(installed==false){
			status=Status.UNINSTALLED;
		} else {
			if(meta.commit.compareTo(commit)==0){
				status = Status.INSTALLED;
			} else {
				status = Status.UPDATEAVAILABLE;
			}
		}
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

	public Status getStatus() {
		return status;
	}

}

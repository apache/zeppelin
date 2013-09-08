package org.apache.zeppelin.driver;

public class Progress {
	int percentage;
	String message;
	public Progress(int percentage, String message) {
		super();
		this.percentage = percentage;
		this.message = message;
	}
	public int getPercentage() {
		return percentage;
	}
	public void setPercentage(int percentage) {
		this.percentage = percentage;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	
}

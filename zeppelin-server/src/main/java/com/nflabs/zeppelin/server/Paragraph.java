package com.nflabs.zeppelin.server;

import java.io.Serializable;
import java.util.Map;
import java.util.Random;

import com.nflabs.zeppelin.repl.Repl;
import com.nflabs.zeppelin.repl.ReplResult;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.JobListener;

/**
 * execution unit 
 */
public class Paragraph extends Job implements Serializable{
	String paragraph;
	private transient Map<String, Repl> repls;
	
	public Paragraph(JobListener listener, Map<String, Repl> repls){
		super(generateId(), listener);
		this.repls = repls;
		paragraph = null;
	}
	
	private static String generateId(){
		return "paragraph_"+System.currentTimeMillis()+"_"+new Random(System.currentTimeMillis()).nextInt();
	}
	
	public String getParagraph() {
		return paragraph;
	}

	public void setParagraph(String paragraph) {
		this.paragraph = paragraph;
	}
	
	public String getRequiredReplName(){
		if(paragraph==null) return null;
		
		String magic = null;
		for(int i=0; i < paragraph.length(); i++){
			int ch = paragraph.charAt(i);
			if (!(i==0 && ch == '%')) {  // detect magic
				break;
			} else {
				magic = "";
			}
			
			if (ch == ' ' || ch == '\n') {
				break;
			} else {
				magic += ch;
			}
		}
		
		return magic;
	}
	
	public Map<String, Repl> getRepls(){
		return repls;
	}
	
	public Repl getRepl(String name) {
		return repls.get(name);
	}
	
	public void setRepls(Map<String, Repl> repls) {
		this.repls = repls;
	}
	
	public void putRepl(String name, Repl repl) {
		repls.put(name, repl);
	}
	
	@Override
	public int progress() {
		return 0;
	}

	@Override
	public Map<String, Object> info() {
		return null;
	}

	@Override
	protected Object jobRun() throws Throwable {
		Repl repl = repls.get(getRequiredReplName());
		ReplResult ret = repl.interpret(paragraph);
		return ret;
	}

	@Override
	protected boolean jobAbort() {
		Repl repl = repls.get(getRequiredReplName());
		repl.cancel();
		return true;
	}
}

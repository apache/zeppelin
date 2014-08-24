package com.nflabs.zeppelin.server;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.repl.Repl;
import com.nflabs.zeppelin.repl.ReplResult;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.JobListener;

/**
 * execution unit 
 */
public class Paragraph extends Job implements Serializable{
	String paragraph;
	Map<String, Object> params = new HashMap<String, Object>();
	private transient NoteReplLoader replLoader;
	
	public Paragraph(JobListener listener, NoteReplLoader replLoader){
		super(generateId(), listener);
		this.replLoader = replLoader;
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
	
	
	public Map<String, Object> getParams() {
		return params;
	}

	public void setParams(Map<String, Object> params) {
		this.params = params;
	}

	public String getRequiredReplName(){
		if(paragraph==null) return null;

		// get script head
		int scriptHeadIndex = 0;
		for(int i=0; i < paragraph.length(); i++){
			char ch = paragraph.charAt(i);
			if(ch==' ' || ch == '\n'){
				scriptHeadIndex = i;
				break;
			}
		}
		if(scriptHeadIndex==0) return null;
		String head = paragraph.substring(0, scriptHeadIndex);
		if(head.startsWith("%")){
			return head.substring(1);
		} else {
			return null;
		}
	}
	
	
	private String getScriptBody(){
		if(paragraph==null) return null;
		
		String magic = getRequiredReplName();
		if(magic==null) return paragraph;
		if(magic.length()+2>=paragraph.length()) return "";
		return paragraph.substring(magic.length()+2);
	}
	
	public NoteReplLoader getNoteReplLoader(){
		return replLoader;
	}
	
	public Repl getRepl(String name, Properties properties) {
		return replLoader.getRepl(name, properties);
	}
	
	public void setNoteReplLoader(NoteReplLoader repls) {
		this.replLoader = repls;
	}
	
	public ReplResult getResult() {
		return (ReplResult) getReturn();
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
		Repl repl = getRepl(getRequiredReplName(), new Properties());
		if(repl==null) {	
			logger().error("Can not find interpreter name "+getRequiredReplName());
			throw new RuntimeException("Can not find interpreter for "+getRequiredReplName());
		}
		ReplResult ret = repl.interpret(getScriptBody());
		return ret;
	}

	@Override
	protected boolean jobAbort() {
		Repl repl = getRepl(getRequiredReplName(), new Properties());
		repl.cancel();
		return true;
	}
	
	private Logger logger(){
		Logger logger = LoggerFactory.getLogger(Paragraph.class);
		return logger;
	}
	
}

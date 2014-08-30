package com.nflabs.zeppelin.notebook;

import java.io.Serializable;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.Interpreter.FormType;
import com.nflabs.zeppelin.notebook.form.Form;
import com.nflabs.zeppelin.notebook.form.Input;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.JobListener;

/**
 * execution unit 
 */
public class Paragraph extends Job implements Serializable{
	String paragraph;
	private transient NoteReplLoader replLoader;
	public final Form form;
	
	public Paragraph(JobListener listener, NoteReplLoader replLoader){
		super(generateId(), listener);
		this.replLoader = replLoader;
		paragraph = null;
		form = new Form();
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
	
	public Interpreter getRepl(String name) {
		return replLoader.getRepl(name);
	}
	
	public void setNoteReplLoader(NoteReplLoader repls) {
		this.replLoader = repls;
	}
	
	public InterpreterResult getResult() {
		return (InterpreterResult) getReturn();
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
		String replName = getRequiredReplName();
		Interpreter repl = getRepl(replName);
		logger().info("run paragraph {} using {} "+repl, getId(), replName);
		if(repl==null) {	
			logger().error("Can not find interpreter name "+repl);
			throw new RuntimeException("Can not find interpreter for "+getRequiredReplName());
		}
		
		String script = getScriptBody();
		// inject form
		if(repl.getFormType()==FormType.NATIVE) {
			form.clearForms();
			repl.bindValue("form", form);  // user code will dynamically create inputs
		} else if(repl.getFormType()==FormType.SIMPLE){ 
			String scriptBody = getScriptBody();
			Map<String, Input> inputs = Input.extractSimpleQueryParam(scriptBody);  // inputs will be built from script body
			form.setForms(inputs);
			script = Input.getSimpleQuery(form.getParams(), scriptBody);
		}
		logger().info("RUN : "+script);
		InterpreterResult ret = repl.interpret(script);
		return ret;
	}

	@Override
	protected boolean jobAbort() {
		Interpreter repl = getRepl(getRequiredReplName());
		repl.cancel();
		return true;
	}
	
	private Logger logger(){
		Logger logger = LoggerFactory.getLogger(Paragraph.class);
		return logger;
	}
	

}

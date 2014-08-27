package com.nflabs.zeppelin.markdown;

import java.io.IOException;
import java.util.Properties;

import org.markdown4j.Markdown4jProcessor;

import com.nflabs.zeppelin.repl.Repl;
import com.nflabs.zeppelin.repl.ReplResult;
import com.nflabs.zeppelin.repl.ReplResult.Code;

public class Markdown extends Repl {
	private Markdown4jProcessor md;

	public Markdown(Properties property){
		super(property);
	}	
	
	@Override
	public void initialize() {
		md = new Markdown4jProcessor();
	}

	@Override
	public void destroy() {
	}

	@Override
	public Object getValue(String name) {
		return null;
	}

	@Override
	public ReplResult interpret(String st) {
		String html;
		try {
			html = md.process(st);
		} catch (IOException e) {
			return new ReplResult(Code.ERROR, e.getMessage());
		}
		return new ReplResult(Code.SUCCESS, html);
	}

	@Override
	public void cancel() {
	}

	@Override
	public void bindValue(String name, Object o) {
	}

	@Override
	public FormType getFormType() {
		return FormType.SIMPLE;
	}
}

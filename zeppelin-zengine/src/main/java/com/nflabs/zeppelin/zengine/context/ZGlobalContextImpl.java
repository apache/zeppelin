package com.nflabs.zeppelin.zengine.context;

public class ZGlobalContextImpl implements ZContext {

	public ZGlobalContextImpl() {

	}

	@Override
	public Object param(String name) {
		throw new RuntimeException("Can not access z.param in global context");
	}

	@Override
	public Object param(String name, Object defaultValue) {
		throw new RuntimeException("Can not access z.param in global context");
	}

	@Override
	public String in() {
		return "<%= z.in %>";
	}

	@Override
	public String out() {
		throw new RuntimeException("Can not access z.out in global context");
	}

	@Override
	public String arg() {
		throw new RuntimeException("Can not access z.arg in global context");
	}

}

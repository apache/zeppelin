package com.nflabs.zeppelin.zai;

import java.util.LinkedList;
import java.util.List;

public abstract class ParamSpec <T>{
	String name;
    String description; 
    T defaultValue;
	private List<Option<T>> options;
	private Range<T> range;
	boolean allowAny = false;
    
    
	public ParamSpec(String name, 
		    String description, 
		    T defaultValue,
		    boolean allowAny,
		    List<Option<T>> options,
		    Range<T> range
			){
		this.name = name;
		this.description = description;
		this.defaultValue = defaultValue;
		this.allowAny = allowAny;
		this.options = options;
		this.range = range;
	}
	
	public ParamSpec(String name){
		this.name = name;
	}
	
	public ParamSpec<T> withDescription(String desc){
		this.description = desc;
		return this;
	}
	
	public ParamSpec<T> withDefaultValue(T def){
		this.defaultValue = def;
		return this;
	}
	
	public ParamSpec<T> withAllowAny(boolean tf){
		this.allowAny = tf;
		return this;
	}
	
	public ParamSpec<T> addOption(String friendlyName, T v){
		if(options == null){
			options = new LinkedList<Option<T>>();
		}
		options.add(new Option<T>(friendlyName, v));
		return this;
	}
	
	public ParamSpec<T> addOption(T v){
		if(options == null){
			options = new LinkedList<Option<T>>();
		}
		options.add(new Option<T>(v));
		return this;
	}
	
	public ParamSpec<T> from(T v){
		if(range== null){
			range = new Range<T>(new Option<T>(v), null);
		} else {
			range = new Range<T>(new Option<T>(v), range.to());
		}
		return this;
	}
	
	public ParamSpec<T> to(T v){
		if(range== null){
			range = new Range<T>(null, new Option<T>(v));
		} else {
			range = new Range<T>(range.from(), new Option<T>(v));
		}
		return this;
	}
	
	

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public T getDefaultValue() {
		return defaultValue;
	}

	public List<Option<T>> getOptions() {
		return options;
	}

	public Range<T> getRange() {
		return range;
	}

	public boolean isAllowAny() {
		return allowAny;
	}

	public T validate(T v) throws ParamSpecException {
		if(v==null){
			if(defaultValue!=null){
				return defaultValue;
			} else {
				throw new ParamSpecException("Value can not be null");
			}
		} else {

			if(defaultValue!=null){
				if(compare(defaultValue, v)==0){
					return v;
				}
			}
			if(options!=null){
				for(Option<T> o : options){
					if(compare(o.getValue(), v)==0){
						return v;
					}
				}
			}
			
			if(allowAny==true){
				if(range!=null){
					Option<T> from = range.from();
					Option<T> to = range.to();
					if((from==null || compare(from.getValue(), v)>=0) &&
						(to==null || compare(v, to.getValue())>=0)){
						return v;
					} else {	
						throw new ParamSpecException("Value not in range");
					}
				} else {
					return v;
				}
			} else {
				throw new ParamSpecException("Any value is not allowed");
			}
		}
	}
	
	public abstract int compare(T a, T b);
	
	public static class Option<T> {
		String name;
		T value;
		
		public Option(String name, T value){
			this.name = name;
			this.value = value;
		}
		
		public Option(T value){
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public T getValue() {
			return value;
		}

		public void setValue(T value) {
			this.value = value;
		}
		
	}
	
	public static class Range<T>{

		private Option<T> to;
		private Option<T> from;

		public Range(Option<T> from, Option<T> to){
			this.to = to;
			this.from = from;
		}

		public Option<T> to() {
			return to;
		}

		public Option<T> from() {
			return from;
		}


	}
	
	public static class IntegerParamSpec extends ParamSpec<Integer>{

		public IntegerParamSpec(
				String name,
				String description,
				Integer defaultValue,
				boolean allowAny,
				List<com.nflabs.zeppelin.zai.ParamSpec.Option<Integer>> options,
				com.nflabs.zeppelin.zai.ParamSpec.Range<Integer> range) {
			super(name, description, defaultValue, allowAny, options, range);
		}
		public IntegerParamSpec(String name){
			super(name);
		}

		@Override
		public int compare(Integer a, Integer b) {
			return b.compareTo(a);
		}
		
	}
	
	public static class StringParamSpec extends ParamSpec<String>{
		public StringParamSpec(String name){
			super(name);
		}
		
		public StringParamSpec(
				String name,
				String description,
				String defaultValue,
				boolean allowAny,
				List<com.nflabs.zeppelin.zai.ParamSpec.Option<String>> options,
				com.nflabs.zeppelin.zai.ParamSpec.Range<String> range) {
			super(name, description, defaultValue, allowAny, options, range);
		}

		@Override
		public int compare(String a, String b) {
			return b.compareTo(a);
		}
		
	}
	
	
}

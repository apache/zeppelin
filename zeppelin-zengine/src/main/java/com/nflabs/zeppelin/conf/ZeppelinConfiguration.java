package com.nflabs.zeppelin.conf;

import java.net.URL;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.ConfigurationNode;

public class ZeppelinConfiguration extends XMLConfiguration {
    private static final long serialVersionUID = 4749305895693848035L;

    public ZeppelinConfiguration(URL url) throws ConfigurationException {
		super(url);
	}
	
	public ZeppelinConfiguration() {
		ConfVars[] vars = ConfVars.values();
		for(ConfVars v : vars){
			if(v.getType()==ConfVars.VarType.BOOLEAN){
				this.setProperty(v.getVarName(), v.getBooleanValue());
			} else if(v.getType()==ConfVars.VarType.LONG){
				this.setProperty(v.getVarName(), v.getLongValue());
			} else if(v.getType()==ConfVars.VarType.INT){
				this.setProperty(v.getVarName(), v.getIntValue());
			} else if(v.getType()==ConfVars.VarType.FLOAT){
				this.setProperty(v.getVarName(), v.getFloatValue());
			} else if(v.getType()==ConfVars.VarType.STRING){
				this.setProperty(v.getVarName(), v.getStringValue());
			} else {
				throw new RuntimeException("Unsupported VarType");
			}
		}
		
	}

	/**
	 * Laod from resource
	 * @throws ConfigurationException 
	 */
	public static ZeppelinConfiguration create() throws ConfigurationException{
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
	    if (classLoader == null) {
	      classLoader = ZeppelinConfiguration.class.getClassLoader();
	    }
	    
		URL url = classLoader.getResource("zeppelin-site.xml");
		if(url!=null){
			return new ZeppelinConfiguration(url);
		} else {
			return new ZeppelinConfiguration();
		}
	}
	
	
	private String getStringValue(String name, String d){
		List<ConfigurationNode> properties = getRootNode().getChildren();
		if(properties==null || properties.size()==0) return d;
		for(ConfigurationNode p : properties){
			if(p.getChildren("name")!=null && p.getChildren("name").size()>0 && name.equals(p.getChildren("name").get(0).getValue())){
				return (String) p.getChildren("value").get(0).getValue();
			}
		}		
		return d;
	}
	
	private int getIntValue(String name, int d){
		List<ConfigurationNode> properties = getRootNode().getChildren();
		if(properties==null || properties.size()==0) return d;
		for(ConfigurationNode p : properties){
			if(p.getChildren("name")!=null && p.getChildren("name").size()>0 && name.equals(p.getChildren("name").get(0).getValue())){
				return (Integer) p.getChildren("value").get(0).getValue();
			}
		}		
		return d;
	}
	
	private long getLongValue(String name, long d){
		List<ConfigurationNode> properties = getRootNode().getChildren();
		if(properties==null || properties.size()==0) return d;
		for(ConfigurationNode p : properties){
			if(p.getChildren("name")!=null && p.getChildren("name").size()>0 && name.equals(p.getChildren("name").get(0).getValue())){
				return (Long) p.getChildren("value").get(0).getValue();
			}
		}		
		return d;
	}
	
	private float getFloatValue(String name, float d){
		List<ConfigurationNode> properties = getRootNode().getChildren();
		if(properties==null || properties.size()==0) return d;
		for(ConfigurationNode p : properties){
			if(p.getChildren("name")!=null && p.getChildren("name").size()>0 && name.equals(p.getChildren("name").get(0).getValue())){
				return (Float) p.getChildren("value").get(0).getValue();
			}
		}		
		return d;
	}
	
	private boolean getBooleanValue(String name, boolean d){
		List<ConfigurationNode> properties = getRootNode().getChildren();
		if(properties==null || properties.size()==0) return d;
		for(ConfigurationNode p : properties){
			if(p.getChildren("name")!=null && p.getChildren("name").size()>0 && name.equals(p.getChildren("name").get(0).getValue())){
				return (Boolean) p.getChildren("value").get(0).getValue();
			}
		}		
		return d;
	}
	
	public String getString(ConfVars c){
		if(System.getenv(c.name())!=null){
			return System.getenv(c.name());
		}
		if(System.getProperty(c.getVarName())!=null){
			return System.getProperty(c.getVarName());
		}

		return getStringValue(c.getVarName(), c.getStringValue());
	}

	
	public int getInt(ConfVars c){
		if(System.getenv(c.name())!=null){
			return Integer.parseInt(System.getenv(c.name()));
		}
		
		if(System.getProperty(c.getVarName())!=null){
			return Integer.parseInt(System.getProperty(c.getVarName()));
		}
		return getIntValue(c.getVarName(), c.getIntValue());
	}
	
	public long getLong(ConfVars c){
		if(System.getenv(c.name())!=null){
			return Long.parseLong(System.getenv(c.name()));
		}
		
		if(System.getProperty(c.getVarName())!=null){
			return Long.parseLong(System.getProperty(c.getVarName()));
		}
		return getLongValue(c.getVarName(), c.getLongValue());
	}

	public float getFloat(ConfVars c){
		if(System.getenv(c.name())!=null){
			return Float.parseFloat(System.getenv(c.name()));
		}
		if(System.getProperty(c.getVarName())!=null){
			return Float.parseFloat(System.getProperty(c.getVarName()));
		}
		return getFloatValue(c.getVarName(), c.getFloatValue());
	}

	public boolean getBoolean(ConfVars c){
		if(System.getenv(c.name())!=null){
			return Boolean.parseBoolean(System.getenv(c.name()));
		}
		
		if(System.getProperty(c.getVarName())!=null){
			return Boolean.parseBoolean(System.getProperty(c.getVarName()));
		}
		return getBooleanValue(c.getVarName(), c.getBooleanValue());
	}

	
	public static enum ConfVars {
		ZEPPELIN_HOME				("zeppelin.home", "../"),
		ZEPPELIN_PORT				("zeppelin.server.port", 8080),
		ZEPPELIN_WAR				("zeppelin.war", "../zeppelin-web/src/main/webapp"),
		ZEPPELIN_SESSION_DIR		("zeppelin.session.dir", "sessions"),
		ZEPPELIN_LOCAL_WAREHOUSE	("zeppelin.local.warehouse", "data"),
		ZEPPELIN_ZAN_LOCAL_REPO		("zeppelin.zan.localrepo", "../zan-repo"),
		ZEPPELIN_COMMAND_TIMEOUT	("zeppelin.command.timeout", 1000*60*30),  // 30 min
		ZEPPELIN_JOB_SCHEDULER	    ("zeppelin.job.scheduler", "FIFO"), // FIFO or PARALLEL
		ZEPPELIN_MAX_RESULT			("zeppelin.max.result", 10000),     // max num result taken by result class
		HIVE_DRIVER					("hive.driver.class", "org.apache.hadoop.hive.jdbc.HiveDriver"), // for hive2 "org.apache.hive.jdbc.HiveDriver"
		HIVE_CONNECTION_URI			("hive.connection.uri", null) // jdbc:hive://localhost:10001/default
		;
		
		
		
		private String varName;
		@SuppressWarnings("rawtypes")
        private Class varClass;
		private String stringValue;
		private VarType type;
		private int intValue;
		private float floatValue;
		private boolean booleanValue;
		private long longValue;
		

		ConfVars(String varName, String varValue){
			this.varName = varName;
			this.varClass = String.class;
			this.stringValue = varValue;
			this.intValue = -1;
			this.floatValue = -1;
			this.longValue = -1;
			this.booleanValue = false;
			this.type = VarType.STRING;
		}
		
		ConfVars(String varName, int intValue){
			this.varName = varName;
			this.varClass = Integer.class;
			this.stringValue = null;
			this.intValue = intValue;
			this.floatValue = -1;
			this.longValue = -1;
			this.booleanValue = false;
			this.type = VarType.INT;
		}
		
		ConfVars(String varName, long longValue){
			this.varName = varName;
			this.varClass = Integer.class;
			this.stringValue = null;
			this.intValue = -1;
			this.floatValue = -1;
			this.longValue = longValue;
			this.booleanValue = false;
			this.type = VarType.INT;
		}
		
		ConfVars(String varName, float floatValue){
			this.varName = varName;
			this.varClass = Float.class;
			this.stringValue = null;
			this.intValue = -1;
			this.longValue = -1;
			this.floatValue = floatValue;
			this.booleanValue = false;
			this.type = VarType.FLOAT;
		}
		
		ConfVars(String varName, boolean booleanValue){
			this.varName = varName;
			this.varClass = Boolean.class;
			this.stringValue = null;
			this.intValue = -1;
			this.longValue = -1;
			this.floatValue = -1;
			this.booleanValue = booleanValue;
			this.type = VarType.BOOLEAN;
		}
		
	    public String getVarName() {
			return varName;
		}

		@SuppressWarnings("rawtypes")
        public Class getVarClass() {
			return varClass;
		}

		public int getIntValue(){
			return intValue;
		}
		
		public long getLongValue(){
			return longValue;
		}
		
		public float getFloatValue(){
			return floatValue;
		}
		
		public String getStringValue() {
			return stringValue;
		}

		public boolean getBooleanValue(){
			return booleanValue;
		}

		public VarType getType() {
			return type;
		}

		enum VarType {
	        STRING { @Override
	        void checkType(String value) throws Exception { } },
	        INT { @Override
	        void checkType(String value) throws Exception { Integer.valueOf(value); } },
	        LONG { @Override
	        void checkType(String value) throws Exception { Long.valueOf(value); } },
	        FLOAT { @Override
	        void checkType(String value) throws Exception { Float.valueOf(value); } },
	        BOOLEAN { @Override
	        void checkType(String value) throws Exception { Boolean.valueOf(value); } };

	        boolean isType(String value) {
	          try { checkType(value); } catch (Exception e) { return false; }
	          return true;
	        }
	        String typeString() { return name().toUpperCase();}
	        abstract void checkType(String value) throws Exception;
	    }
	}

}

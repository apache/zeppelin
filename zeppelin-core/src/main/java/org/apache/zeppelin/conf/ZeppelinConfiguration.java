package org.apache.zeppelin.conf;

import java.net.URL;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;

public class ZeppelinConfiguration extends XMLConfiguration{
	
	public ZeppelinConfiguration(URL url) throws ConfigurationException{		
		super(url);
	}
	
	/**
	 * Laod from resource
	 * @throws ConfigurationException 
	 */
	public static ZeppelinConfiguration create() throws ConfigurationException{
		URL url = ZeppelinConfiguration.class.getClassLoader().getResource("zeppelin-site.xml");
		return new ZeppelinConfiguration(url);
	}
	
	
	public String getString(String k){
		return super.getString(k, ConfVars.valueOf(k).getStringValue());
	}
	
	public String getString(ConfVars c){
		return super.getString(c.name(), c.getStringValue());
	}

	public int getInt(String k){
		return super.getInt(k, ConfVars.valueOf(k).getIntValue());
	}
	
	public int getInt(ConfVars c){
		return super.getInt(c.name(), c.getIntValue());
	}
	
	public float getFloat(String k){
		return super.getFloat(k, ConfVars.valueOf(k).getFloatValue());
	}
	
	public float getFloat(ConfVars c){
		return super.getFloat(c.name(), c.getFloatValue());
	}

	public boolean getBoolean(String k){
		return super.getBoolean(k, ConfVars.valueOf(k).getBooleanValue());
	}
	
	public boolean getBoolean(ConfVars c){
		return super.getBoolean(c.name(), c.getBooleanValue());
	}

	
	public static enum ConfVars {
		DRIVERCLASS("zeppelin.driver.class", null)
		;
		
		
		
		private String varName;
		private Class varClass;
		private String stringValue;
		private VarType type;
		private int intValue;
		private float floatValue;
		private boolean booleanValue;
		

		ConfVars(String varName, String varValue){
			this.varName = varName;
			this.varClass = String.class;
			this.stringValue = varValue;
			this.intValue = -1;
			this.floatValue = -1;
			this.booleanValue = false;
			this.type = VarType.STRING;
		}
		
		ConfVars(String varName, int intValue){
			this.varName = varName;
			this.varClass = Integer.class;
			this.stringValue = null;
			this.intValue = intValue;
			this.floatValue = -1;
			this.booleanValue = false;
			this.type = VarType.INT;
		}
		
		ConfVars(String varName, float floatValue){
			this.varName = varName;
			this.varClass = Float.class;
			this.stringValue = null;
			this.intValue = -1;
			this.floatValue = floatValue;
			this.booleanValue = false;
			this.type = VarType.FLOAT;
		}
		
		ConfVars(String varName, boolean booleanValue){
			this.varName = varName;
			this.varClass = Boolean.class;
			this.stringValue = null;
			this.intValue = -1;
			this.floatValue = -1;
			this.booleanValue = booleanValue;
			this.type = VarType.BOOLEAN;
		}
		
		
		
		
	    public String getVarName() {
			return varName;
		}



		public Class getVarClass() {
			return varClass;
		}

		public int getIntValue(){
			return intValue;
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

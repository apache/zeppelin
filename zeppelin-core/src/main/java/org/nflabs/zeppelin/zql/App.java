package org.nflabs.zeppelin.zql;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.apache.commons.io.FileUtils;



public class App {
	Logger logger = Logger.getLogger(App.class.getName());
	String name;
	String templatePath;
	List<File> resources;
	boolean erb;
	Random random = new Random();
	
	public App(String name, String templatePath, List<File> resources, boolean erb) {
		super();
		
		this.templatePath = templatePath;
		this.name = name;
		this.resources = resources;
		this.erb = erb;
	}
	
	public int hashCode(){
		return name.hashCode();
	}
	
	public boolean equals(Object o){
		return name.equals(((App)o).getName());
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	public List<File> getResources() {
		return resources;
	}
	public void setResources(List<File> resources) {
		this.resources = resources;
	}
	
	private static ScriptEngine rubyEngine;
	public static ScriptEngine getRubyScriptEngine(){
		if(rubyEngine==null){
			System.setProperty("org.jruby.embed.localvariable.behavior", "global");
			rubyEngine = new ScriptEngineManager().getEngineByName("jruby");
		}
		return rubyEngine;
	}

	public String getErbQuery(Map<String, String> params, String sql){
		
		String varName = "zeppelinAppScriptOut_"+System.currentTimeMillis()+"_"+random.nextInt(100);
		ScriptEngine engine = getRubyScriptEngine();

		
		
		StringBuffer rubyScript = new StringBuffer();
		rubyScript.append("require 'erb'\n");
		rubyScript.append("$"+varName+" = ERB.new(File.read('"+templatePath+"')).result(binding)\n");
		SimpleBindings bindings = new SimpleBindings();

		engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
		bindings.put("hql", sql);
		bindings.putAll(params);
		try {
			engine.eval(rubyScript.toString(), bindings);
		} catch (ScriptException e) {
			e.printStackTrace();
			logger.log(Level.SEVERE, "ERB error", e);
			return null;
		}
		return (String) engine.getBindings(ScriptContext.ENGINE_SCOPE).get(varName);
	}
	public String getQuery(Map<String, String> params, String sql) throws IOException{
		if(erb==false){
			return getSimpleQuery(params, sql);
		} else {
			return getErbQuery(params, sql);
		}
	}
	public String getSimpleQuery(Map<String, String> params, String sql) throws IOException{
		String script = FileUtils.readFileToString(new File(templatePath));
		String replaced = script.replace('\n', ' ');

		for(String key : params.keySet()){
			String value = params.get(key);
			replaced = replaced.replaceAll("[$][{]"+key+"(=[^}]*)?[}]", value);
		}
		
		Pattern pattern = Pattern.compile("[$][{]([^=}]*[=][^}]*)[}]");
		while(true){
			Matcher match = pattern.matcher(replaced);
			if(match!=null && match.find()){
				String m = match.group(1);
				int p = m.indexOf('=');
				String replacement=m.substring(p+1);
				replaced = replaced.replaceFirst("[$][{]"+m+"[}]", replacement);
			} else {
				break;
			}
		}

		replaced = replaced.replace("${hql}", sql);
		
		replaced = replaced.replace("[$][{]([^=}]*)[}]", "");
		return replaced;		
	}

	public static App load(File path){
		if(path.isDirectory()==false) return null;
		
		String name = path.getName();
		List<File> rs = new LinkedList<File>();;
		
		boolean erb = false;
		String templatePath = null;
		for(File f : path.listFiles()){
			if(f.isFile()==false) continue;
			
			if("hql".equals(f.getName()) && templatePath==null){
				templatePath = f.getAbsolutePath();
			} else if("hql.erb".equals(f.getName())){
				templatePath = f.getAbsolutePath();
				erb = true;
			} else if(f.getName().startsWith(".") || f.getName().startsWith("#") || f.getName().startsWith("~") || f.getName().startsWith(name+"_")==false){
				continue;
			} else {
				rs.add(f);
			}
		}
		
		if(templatePath==null) return null;
		return new App(name, templatePath, rs, erb);
	}
}

package com.nflabs.zeppelin.zengine.stmt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nflabs.zeppelin.driver.LazyConnection;
import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.util.Util;
import com.nflabs.zeppelin.zengine.ERBEvaluator;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.ZPlan;
import com.nflabs.zeppelin.zengine.ZQLException;
import com.nflabs.zeppelin.zengine.context.ZGlobalContextImpl;
import com.nflabs.zeppelin.zengine.stmt.AnnotationStatement.ANNOTATION;
import com.nflabs.zeppelin.zengine.stmt.AnnotationStatement.COMMAND;
/**
 * ZQL parses Zeppelin Query Language (http://nflabs.github.io/zeppelin/#zql) 
 * and generate logical execution plan.
 *  
 * IN:  Plain-text user input in Zeppelin Query Language syntax
 *      Collection of available driver instances
 * OUT: sequence of Z instances (lined to appropriate drivers)
 * 
 * @author moon
 *
 */
public class ZQL {
	String [] op = new String[]{";", "|" /* Disable redirect. see ZEPPELIN-99, ">>", ">" */};
	StringBuilder sb = new StringBuilder();
	ERBEvaluator erbEvaluator = new ERBEvaluator();

	public ZQL(){
	}
	
	/**
	 * Load ZQL statements from string
	 * @param zql Zeppelin query language statements
	 * @param z Zengine
	 * @throws ZException
	 */
	public ZQL(String zql) throws ZException{
	    append(zql);
	}
	
	/**
	 * Load ZQL statements from File
	 * @param file File contains text representation of ZQL
	 * @throws ZException
	 * @throws IOException
	 */
	public ZQL(File file) throws ZException, IOException{
		load(file);
	}
	
	/**
	 * Load ZQL statements from input stream
	 * @param ins input stream which streams ZQL
	 * @throws IOException
	 * @throws ZException
	 */
	public ZQL(InputStream ins) throws IOException, ZException{
		//Z.configure();
		load(ins);
	}
	
	
	/**
	 * Load ZQL statements from File
	 * @param file File contains text representation of ZQL
	 * @throws ZException
	 * @throws IOException
	 */
	public void load(File file) throws IOException{ 
		FileInputStream ins = new FileInputStream(file);
		load(ins);
	}
	
	/**
	 * Load ZQL statements from input stream.
	 * Closes the stream at the end.
	 * 
	 * @param ins input stream which streams ZQL
	 * @throws IOException
	 * @throws ZException
	 */
	public void load(InputStream ins) throws IOException{
		BufferedReader in = new BufferedReader(new InputStreamReader(ins));
		String line = null;
		while((line = in.readLine())!=null){
			sb.append(line);
		}
		ins.close();
	}
	
	/**
	 * Append ZQL statement
	 * @param s statement to add
	 */
	public void append(String s){
		sb.append(s);
	}
	
	/**
	 * Clear ZQL statement
	 */
	public void clear(){
		sb = new StringBuilder();
	}
	
	/**
	 * Compile ZQL statements and return logical plan
	 * @return ZPlan 
	 * @throws ZQLException
	 * @throws ZException 
	 */
	public ZPlan compile() throws ZQLException, ZException{
		return compileZql(sb.toString());
	}

	private String erbEvalGlobalScope(String stmts) throws ZException{
		ERBEvaluator evaluator = new ERBEvaluator();
		ZGlobalContextImpl zcontext = new ZGlobalContextImpl();
		return evaluator.eval(stmts, zcontext);
	}

	/**
	 * Each Z should obtain ref to one of Zengine.supportedDrivers here:
	 *  - either from explicit @Driver statement
	 *  - or implicitly copy one from previous Z
     * 
	 * 
	 * @param stmts
	 * @return
	 * @throws ZQLException
	 * @throws ZException 
	 */
	private ZPlan compileZql(String stmts) throws ZQLException, ZException{
	    Map<String, ZeppelinConnection> connections = new HashMap<String, ZeppelinConnection>();
	    String currentDriverName = null;
	    
	    // create default connection
	    ZeppelinConnection currentConnection = new LazyConnection(currentDriverName);
	    connections.put(currentDriverName, currentConnection);
	    
	    ZPlan plan = new ZPlan();
		Z currentZ = null;
		
		String escapeSeq = "\"',;<%>!";
		char escapeChar = '\\';
		String [] blockStart = new String[]{ "\"", "'", "<%", "<", "N_<", "!"};
		String [] blockEnd = new String[]{ "\"", "'", "%>", ";", "N_>", ";" };
		String [] t = Util.split(erbEvalGlobalScope(stmts), escapeSeq, escapeChar, blockStart, blockEnd, op, true);

		String currentOp = null;
		for(int i=0; i<t.length; i++){
			String stmt = t[i];
			if(stmt==null) continue;
			stmt = stmt.trim();
			if (stmt.endsWith(";")==true && stmt.length()>1) {
				stmt = stmt.substring(0, stmt.length()-1);
			}
			if(stmt.length()==0) continue;
			
			// check if it is operator ----
			boolean operatorFound = false;
			for(String o : op){
				if(o.equals(stmt)){
					if(currentOp!=null){
						throw new ZQLException("Operator "+o+" can not come after "+currentOp);
					}
					currentOp = o;
					operatorFound = true;
					break;
				}
			}
			if(operatorFound==true){
				continue;
			}
			
			// it is not an operator ------			
			if(currentZ==null && currentOp!=null){  // check if stmt start from operator
				throw new ZQLException(currentOp+" can not be at the beginning");
			}
			
			// Current operator is redirect
			/* Disable redirect. see ZEPPELIN-99
			if(op[2].equals(currentOp)){ // redirect append
				throw new ZQLException("redirection (append) not implemented");
			} else if(op[3].equals(currentOp)){ // redirect overwrite
				if(currentZ!=null){
					RedirectStatement red = new RedirectStatement(stmt);
					currentZ.withName(red.getName());
					currentZ.withTable(red.isTable());
					currentOp = null;
					continue;
				} else {
					throw new ZQLException("Can not redirect empty");
				}				
			}
			*/
			
			// check if it is Annotation
			if(stmt.startsWith("@")){
				if(currentZ!=null){ // previous query exist
					if(currentOp==null || (currentOp!=null && currentOp.equals(op[0]))){ // semicolon
			            currentZ.setConnection(currentConnection);
			            plan.add(currentZ);
						currentZ = null;
						currentOp = null;
					} else {
						throw new ZQLException("Can not piped or redirected from/to exec statement");
					}
				}
				try {				
					AnnotationStatement annotation = new AnnotationStatement(stmt);
					if (ANNOTATION.DRIVER == annotation.getAnnotation()) {
						if (COMMAND.SET == annotation.getCommand()) {
							String driverName = annotation.getArgument();
							if (driverName == null) {
								driverName = null;
							}
							currentConnection = connections.get(driverName);
							if (currentConnection == null) {
								currentConnection = new LazyConnection(driverName);
								connections.put(driverName, currentConnection);
							}
						}
					}
					currentZ = annotation;
				} catch (ZException e) {
					throw new ZQLException(e);
				}
				continue;				
			}
			
			// check if a statement is L --
			Z z= null;
			try {
				L l = loadL(stmt);
				z = l;
			} catch (ZException e) {
			    //statement is not Library.. and we go on
			}
			
			// if it is not L statement, assume it is a Q statement --
			if(z==null){
				Q q;
				try {
					q = new Q(stmt);
					q.withErbEvaluator(erbEvaluator);
				} catch (ZException e) {
					throw new ZQLException(e);
				}
				z = q;
			}
			if(currentZ==null){
				currentZ = z;
			} else if(currentOp==null){
				throw new ZQLException("Assert! Statment does not have operator in between");
			} else if(currentOp.equals(op[0])){ // semicolon
                currentZ.setConnection(currentConnection);
                plan.add(currentZ);
				currentZ = z;
				currentOp = null;
			} else if(currentOp.equals(op[1])){ // pipe
				currentZ = currentZ.pipe(z);
				currentOp = null;
			}
		}
		if(currentZ!=null){
            currentZ.setConnection(currentConnection);
            plan.add(currentZ);
		}
		
		return plan;
	}
	
	/**
	 * L pattern
	 * 
	 * libName
	 * libName(param1=value1, param2=value2, ...)
	 * libName(param1=value1, param2=value2, ...) args
	 */
	static final Pattern LPattern = Pattern.compile("([^ ()]*)\\s*([(][^)]*[)])?\\s*(.*)", Pattern.DOTALL);
	private L loadL(String stmt) throws ZException{
		Matcher m = LPattern.matcher(stmt);
		
		if(m.matches()==true && m.groupCount()>0){
			String libName = m.group(1);

			
			String args = m.group(3);
			L l = new L(libName, args);
			
			String params = m.group(2);
			
			if(params!=null){
				params = params.trim();
				params = params.substring(1, params.length() - 1);
				params = params.trim();

				String[] paramKVs = Util.split(params, ',');
				if(paramKVs!=null){					
					for(String kvPair : paramKVs){
						if(kvPair.trim().length()==0) continue;
						
						 String[] kv = Util.split(kvPair, '=');
						 if(kv.length==1){
							 l.withParam(kv[0].trim(), null); 	 
						 } else if(kv.length==2){
							 l.withParam(kv[0].trim(), kv[1].trim()); 	 
						 }						 
					}
				}
			}
			return l;
			
		} else {
			return null;
		}
	}


}

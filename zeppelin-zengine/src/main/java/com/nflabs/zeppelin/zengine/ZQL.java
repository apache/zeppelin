package com.nflabs.zeppelin.zengine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.Path;

import com.nflabs.zeppelin.util.Util;
/**
 * class ZQL parses Zeppelin Query Language (http://nflabs.github.io/zeppelin/#zql) and generate logical plan
 * @author moon
 *
 */
public class ZQL {
	String [] op = new String[]{";", "|", ">>", ">"};
	StringBuilder sb = new StringBuilder();

	/**
	 * Constructor
	 * @throws ZException
	 */
	public ZQL() throws ZException{
		Z.configure();
	}

	/**
	 * Load ZQL statements from string
	 * @param zql Zeppelin query language statements
	 * @throws ZException
	 */
	public ZQL(String zql) throws ZException{
		Z.configure();
		append(zql);
	}
	
	
	/**
	 * Load ZQL statements from URI location
	 * @param uri URI location of text resource which contains ZQL
	 * @throws IOException
	 * @throws ZException
	 */
	public ZQL(URI uri) throws IOException, ZException{
		Z.configure();
		load(uri);
	}
	
	/**
	 * Load ZQL statements from File
	 * @param file File contains text representation of ZQL
	 * @throws ZException
	 * @throws IOException
	 */
	public ZQL(File file) throws ZException, IOException{
		Z.configure();
		load(file);
	}
	
	/**
	 * Load ZQL statements from input stream
	 * @param ins input stream which streams ZQL
	 * @throws IOException
	 * @throws ZException
	 */
	public ZQL(InputStream ins) throws IOException, ZException{
		Z.configure();
		load(ins);
	}
	

	/**
	 * Load ZQL statements from URI location
	 * @param uri URI location of text resource which contains ZQL
	 * @throws IOException
	 * @throws ZException
	 */
	public void load(URI uri) throws IOException{
		FSDataInputStream ins = Z.fs().open(new Path(uri));
		load(ins);
		ins.close();
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
		ins.close();
	}
	
	/**
	 * Load ZQL statements from input stream
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
	}
	
	/**
	 * Append ZQL statement
	 * @param s statmetn to add
	 */
	public void append(String s){
		sb.append(s);
	}
	
	/**
	 * Clear ZQL statments
	 */
	public void clear(){
		sb = new StringBuilder();
	}
	
	/**
	 * Compile ZQL statements and return logical plan
	 * @return List of Z. Each Z represent single statement. 
	 * @throws ZQLException
	 */
	public List<Z> compile() throws ZQLException{
		return compileZql(sb.toString());
	}
	
	private List<Z> compileZql(String stmts) throws ZQLException{
		List<Z> zList = new LinkedList<Z>();
		Z currentZ = null;
		
		String [] t = Util.split(stmts, op, true);
		String currentOp = null;
		for(int i=0; i<t.length; i++){
			String stmt = t[i];
			if(stmt==null) continue;
			stmt = stmt.trim();
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
			
			
			// check if it is L statment --
			Z z= null;
			try {
				z = loadL(stmt);
			} catch (ZException e) {
			}
			
			
			
			// if it is not L statment, assume it is a Q statement --
			if(z==null){
				Q q;
				try {
					q = new Q(stmt);
				} catch (ZException e) {
					throw new ZQLException(e);
				}
				if(stmt.toLowerCase().trim().startsWith("select")==false && stmt.toLowerCase().trim().startsWith("map")==false){
					q.withName(null);
				}
				z = q;
			}
			if(currentZ==null){
				currentZ = z;
			} else if(currentOp==null){
				throw new ZQLException("Assert! Statment does not have operator in between");
			} else if(currentOp.equals(op[0])){ // semicolon
				zList.add(currentZ);
				currentZ = z;
				currentOp = null;
			} else if(currentOp.equals(op[1])){ // pipe
				currentZ = currentZ.pipe(z);
				currentOp = null;
			}
		}
		if(currentZ!=null){
			zList.add(currentZ);
		}
		
		return zList;
	}
	
	/**
	 * L pattern
	 * 
	 * libName
	 * libName(param1=value1, param2=value2, ...)
	 * libName(param1=value1, param2=value2, ...) args
	 */
	static final Pattern LPattern = Pattern.compile("([^ ()]*)\\s*([(][^)]*[)])?\\s*(.*)");
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

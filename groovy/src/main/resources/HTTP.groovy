/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import groovy.json.JsonOutput

/**
 * simple http rest client for groovy
 * by dlukyanov@ukr.net 
 */
@groovy.transform.CompileStatic
public class HTTP{
	//default response handler
	public static Closure TEXT_RECEIVER = {InputStream instr,Map ctx->
		return instr.getText( (String)ctx.encoding );
	}
	
	public static Closure JSON_RECEIVER = { InputStream instr, Map ctx-> 
		return new groovy.json.JsonSlurper().parse(instr,(String)ctx.encoding);
	}
	
	public static Closure FILE_RECEIVER(File f){
		return { InputStream instr, Map ctx-> 
			f<<instr;
			return f;
		}
	}
	
	public static Map<String,Object> get(Map<String,Object> ctx)throws IOException{
		ctx.put('method','GET');
		return send(ctx);
	}
	
	public static Map<String,Object> post(Map<String,Object> ctx)throws IOException{
		ctx.put('method','POST');
		return send(ctx);
	}
	
	public static Map<String,Object> put(Map<String,Object> ctx)throws IOException{
		ctx.put('method','PUT');
		return send(ctx);
	}
	
	public static Map<String,Object> delete(Map<String,Object> ctx)throws IOException{
		ctx.put('method','DELETE');
		return send(ctx);
	}
	
	public static Map<String,Object> send(Map<String,Object> ctx)throws IOException{
		String             url      = ctx.url;
		Map<String,String> headers  = (Map<String,String>)ctx.headers;
		String             method   = ctx.method;
		Object             body     = ctx.body;
		String             encoding = ctx.encoding?:"UTF-8";
		Closure            receiver = (Closure)ctx.receiver;
		Map<String,String> query    = (Map<String,String>)ctx.query;
		
		//copy context and set default values
		ctx = [:] + ctx;
		ctx.encoding = encoding;
		String contentType="";
		
		if(query){
			url+="?"+query.collect{k,v-> k+"="+URLEncoder.encode(v,'UTF-8') }.join('&')
		}
		
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		
        connection.setDoOutput(true);
        connection.setRequestMethod(method);
		if ( headers!=null && !headers.isEmpty() ) {
			//add headers
			for (Map.Entry<String, String> entry : headers.entrySet()) {
				connection.addRequestProperty(entry.getKey(), entry.getValue());
				if("content-type".equals(entry.getKey().toLowerCase()))contentType=entry.getValue();
			}
		}
		
		if(body!=null){
			//write body
			OutputStream out = connection.getOutputStream();
			if( body instanceof Closure ){
				((Closure)body).call(out, ctx);
			}else if(body instanceof InputStream){
				out << (InputStream)body;
			}else if(body instanceof Map){
				if( contentType.matches("(?i)[^/]+/json") ){
					out.withWriter((String)ctx.encoding){
						it.append( JsonOutput.toJson((Map)body) );
						it.flush();
					}
				}else{
					throw new IOException("Map body type supported only for */json content-type");
				}
			}else if(body instanceof CharSequence){
				out.withWriter((String)ctx.encoding){
					it.append((CharSequence)body);
					it.flush();
				}
			}else{
				throw new IOException("Unsupported body type: "+body.getClass());
			}
			out.flush();
			out.close();
			out=null;
		}
		
		Map response     = [:];
		ctx.response     = response;
		response.code    = connection.getResponseCode();
		response.message = connection.getResponseMessage();
		response.headers = connection.getHeaderFields();
		
		InputStream instr = null;
		
		if( ((int)response.code)>=400 ){
			try{
				instr = connection.getErrorStream();
			}catch(Exception ei){}
		}else{
			try{
				instr = connection.getInputStream();
			}catch(java.io.IOException ei){
				throw new IOException("fail to open InputStream for http code "+response.code+":"+ei);
			}
		}
		
		if(instr!=null) {
			instr = new BufferedInputStream(instr);
			if(receiver==null){
				if( response.headers['Content-Type']?.toString()?.indexOf('/json')>0 ){
					receiver=JSON_RECEIVER;
				} else receiver=TEXT_RECEIVER;
			}
			response.body = receiver(instr,ctx);
			instr.close();
			instr=null;
		}
		return ctx;
	}
}

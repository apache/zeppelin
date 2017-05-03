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

import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import java.security.SecureRandom;
import javax.net.ssl.HttpsURLConnection;

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
	
	public static Map<String,Object> head(Map<String,Object> ctx)throws IOException{
		ctx.put('method','HEAD');
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
	
	/**
	 * @param url string where to send request
	 * @param query Map parameters to append to url
	 * @param method http method to be used in request. standard methods: GET, POST, PUT, DELETE, HEAD
	 * @param headers key-value map with headers that should be sent with request
	 * @param body request body/data to send to url (InputStream, CharSequence, or Map for json and x-www-form-urlencoded context types)
	 * @param encoding encoding name to use to send/receive data - default UTF-8
	 * @param receiver Closure that will be called to receive data from server. Defaults: `HTTP.JSON_RECEIVER` for json content-type and `HTTP.TEXT_RECEIVER` otherwise. Available: `HTTP.FILE_RECEIVER(File)` - stores response to file.
	 * @param ssl javax.net.ssl.SSLContext or String that evaluates the javax.net.ssl.SSLContext. example: send( url:..., ssl: "HTTP.getKeystoreSSLContext('./keystore.jks', 'testpass')" )
	 */
	public static Map<String,Object> send(Map<String,Object> ctx)throws IOException{
		String             url      = ctx.url;
		Map<String,String> headers  = (Map<String,String>)ctx.headers;
		String             method   = ctx.method;
		Object             body     = ctx.body;
		String             encoding = ctx.encoding?:"UTF-8";
		Closure            receiver = (Closure)ctx.receiver;
		Map<String,String> query    = (Map<String,String>)ctx.query;
		Object             sslCtxObj= ctx.ssl;
		
		//copy context and set default values
		ctx = [:] + ctx;
		ctx.encoding = encoding;
		String contentType="";
		
		if(query){
			url+="?"+query.collect{k,v-> k+"="+URLEncoder.encode(v,'UTF-8') }.join('&')
		}
		
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		if(sslCtxObj!=null && connection instanceof HttpsURLConnection){
			SSLContext         sslCtx   = null;
			if(sslCtxObj instanceof SSLContext){
				sslCtx = (SSLContext)sslCtxObj;
			}else if(sslCtxObj instanceof CharSequence){
				//assume this is a groovy code to get ssl context
				sslCtx = evaluateSSLContext((CharSequence)sslCtxObj);
			}else{
				throw new IllegalArgumentException("Unsupported ssl parameter ${sslCtxObj.getClass()}")
			}
			((HttpsURLConnection)connection).setSSLSocketFactory(sslCtx.getSocketFactory());
		}
		
        connection.setDoOutput(true);
        connection.setRequestMethod(method);
		if ( headers!=null && !headers.isEmpty() ) {
			//add headers
			for (Map.Entry<String, String> entry : headers.entrySet()) {
				if(entry.getValue()){
					connection.addRequestProperty(entry.getKey(), entry.getValue());
					if("content-type".equals(entry.getKey().toLowerCase()))contentType=entry.getValue();
				}
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
				if( contentType =~ "(?i)[^/]+/json" ) {
					out.withWriter((String)ctx.encoding){
						it.append( JsonOutput.toJson((Map)body) );
					}
				} else if( contentType =~ "(?i)[^/]+/x-www-form-urlencoded" ) {
					out.withWriter((String)ctx.encoding) {
						it.append( ((Map)body).collect{k,v-> ""+k+"="+URLEncoder.encode((String)v,'UTF-8') }.join('&') )
					}
				} else {
					throw new IOException("Map body type supported only for */json of */x-www-form-urlencoded content-type");
				}
			}else if(body instanceof CharSequence){
				out.withWriter((String)ctx.encoding){
					it.append((CharSequence)body);
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
	
	@groovy.transform.Memoized
	public static SSLContext getKeystoreSSLContext(String keystorePath, String keystorePass, String keystoreType="JKS", String keyPass = null){
		if(keyPass == null) keyPass=keystorePass;
		KeyStore clientStore = KeyStore.getInstance(keystoreType);
		clientStore.load(new File( keystorePath ).newInputStream(), keystorePass.toCharArray());
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(clientStore, keyPass.toCharArray());
		KeyManager[] kms = kmf.getKeyManagers();
		//init TrustCerts
		TrustManager[] trustCerts = new TrustManager[1];                
		trustCerts[0] = new X509TrustManager() {
			public void checkClientTrusted( final X509Certificate[] chain, final String authType ) { }
			public void checkServerTrusted( final X509Certificate[] chain, final String authType ) { }
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}	
		}
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(kms, trustCerts, new SecureRandom());
		return sslContext;
	}
    
    @groovy.transform.Memoized
	public static SSLContext getNaiveSSLContext(){
		System.err.println("HTTP.getNaiveSSLContext() used. Must be disabled on prod!");
        KeyManager[] kms = new KeyManager[0];
		TrustManager[] trustCerts = new TrustManager[1];                
		trustCerts[0] = new X509TrustManager() {
			public void checkClientTrusted( final X509Certificate[] chain, final String authType ) { }
			public void checkServerTrusted( final X509Certificate[] chain, final String authType ) { }
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}	
		}
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, trustCerts, new SecureRandom());
		return sslContext;
	}
	
	/**
	 * evaluates code that should return SSLContext
	 */
    @groovy.transform.Memoized
	public static SSLContext evaluateSSLContext(CharSequence code) {
		Object ssl = new GroovyShell( HTTP.class.getClassLoader() ).evaluate( code as String );
		return (SSLContext) ssl;
	}
}

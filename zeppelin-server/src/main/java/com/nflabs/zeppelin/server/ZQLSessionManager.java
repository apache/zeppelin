package com.nflabs.zeppelin.server;

import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.JobListener;
import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.scheduler.Job.Status;

public class ZQLSessionManager implements JobListener {
	Logger logger = Logger.getLogger(ZQLSessionManager.class);
	
	public TreeMap<String, ZQLSession> sessions = new TreeMap<String, ZQLSession>();
	Gson gson =new GsonBuilder().setPrettyPrinting().create();;
	
	AtomicLong counter = new AtomicLong(0);
	private Scheduler scheduler;
	private String sessionPersistBasePath;
	private FileSystem fs;
	
	SimpleDateFormat pathFormat = new SimpleDateFormat("yyyy/MM/dd/HH/");
	
	public ZQLSessionManager(Scheduler scheduler, FileSystem fs, String sessionPersistBasePath){
		this.scheduler = scheduler;
		this.sessionPersistBasePath = sessionPersistBasePath;
		this.fs = fs;
	}
	
	
	public ZQLSession create(){
		ZQLSession as = new ZQLSession("", this);
		
		synchronized(sessions){
			sessions.put(as.getId(), as);
		}
		
		return as;
	}
	
	public ZQLSession get(String sessionId){
		synchronized(sessions){
			if(sessions.containsKey(sessionId)){
				return sessions.get(sessionId);
			}
		}
		
		// search session dir
		Path path = getPathForSessionId(sessionId);
		try {
			return load(path);
		} catch (IOException e) {
			return null;
		}
		
	}
	
	public ZQLSession run(String sessionId){		
		ZQLSession s = get(sessionId);
		if(s==null) return null;
		scheduler.submit(s);
		return s;
	}
	
	
	public ZQLSession setZql(String sessionId, String zql){
		ZQLSession s = get(sessionId);
		if(s==null){
			return null;
		} else {
			s.setZQL(zql);
			try {
				persist(s);
			} catch (IOException e) {
				logger.error("Can't persist session "+sessionId, e);
			}
			return s;
		}
	}

	public boolean discard(String sessionId){
		ZQLSession s= get(sessionId);
		if(s==null) return false;
		
		abort(sessionId);
		synchronized(sessions){
			sessions.remove(sessionId);
		}
		return true;
	}
	
	public ZQLSession abort(String sessionId){
		ZQLSession s= get(sessionId);
		if(s==null) return null;
		
		s.abort();
		return s;
	}

	@Override
	public void statusChange(Job job) {
		if(job.getStatus()==Status.FINISHED ||
		   job.getStatus()==Status.ERROR ||
		   job.getStatus()==Status.ABORT){
			synchronized(sessions){
				sessions.remove(job.getId());
			}
		}
		
		try {
			persist((ZQLSession) job);
		} catch (IOException e) {
			logger.error("Can't persist session "+job.getId(), e);
		}
		
	}

	public Map<String, ZQLSession> getActive() {
		return (Map<String, ZQLSession>) sessions.clone();
	}
	
	
	public Map<String, ZQLSession> find(Date from, Date to, int max){
		TreeMap<String, ZQLSession> found = new TreeMap<String, ZQLSession>();
		
		for(Date cur=to; cur.before(from)==false; cur = new Date(cur.getTime()-(1000*60*60*24))){
			System.out.println("Current Date="+cur);
			Path dir = new Path(sessionPersistBasePath+"/"+pathFormat.format(cur));
			try {
				if(fs.isDirectory(dir)==false) continue;
			} catch (IOException e) {
				logger.error("Can't scan dir "+dir, e);
			}
			
			FileStatus[] files = null;
			try {
				files = fs.listStatus(dir);
			} catch (IOException e) {
				logger.error("Can't list dir "+dir, e);
				e.printStackTrace();
			}
			if(files==null) continue;
			
			Arrays.sort(files, new Comparator<FileStatus>(){

				@Override
				public int compare(FileStatus a, FileStatus b) {
					String aName = a.getPath().getName();
					String bName = b.getPath().getName();
					return bName.compareTo(aName);
				}				
			});
			
			for(FileStatus f : files){
				ZQLSession session;
				try {
					session = load(f.getPath());
					found.put(session.getId(), session);
				} catch (IOException e) {
					logger.error("Can't load session from path "+f.getPath(), e);
				}
				
				if(found.size()>=max) break;
			}
			
			if(found.size()>=max) break;	
		}
		
		return found;
	}
	
	
	private Path getPathForSession(ZQLSession session){
		Date date = session.getDateCreated();
		return new Path(sessionPersistBasePath+"/"+pathFormat.format(date)+session.getId());		
	}
	
	private Path getPathForSessionId(String sessionId){
		String datePart = sessionId.substring(0, sessionId.indexOf('_'));
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd-HHmmss");
		Date date;
		try {
			date = df.parse(datePart);
		} catch (ParseException e) {
			logger.error("Invalid sessionId format "+sessionId, e);
			return null;
		}
		return new Path(sessionPersistBasePath+"/"+pathFormat.format(date)+sessionId);
	}
	
	private void persist(ZQLSession session) throws IOException{		
		String json = gson.toJson(session);
		Path path = getPathForSession(session);
		fs.mkdirs(path.getParent());
		FSDataOutputStream out = fs.create(path, true);
		out.writeBytes(json);
		out.close();
	}
	
	private ZQLSession load(Path path) throws IOException{
		if(fs.isFile(path)==false){
			return null;
		}
		FSDataInputStream ins = fs.open(path);
		ZQLSession session = gson.fromJson(new InputStreamReader(ins), ZQLSession.class);
		if(session.getStatus()==Status.RUNNING){
			session.setStatus(Status.ABORT);
		}
		ins.close();
		return session;
	}
}

package com.nflabs.zeppelin.server;

import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.JobListener;
import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.scheduler.Job.Status;
import com.nflabs.zeppelin.zengine.L;
import com.nflabs.zeppelin.zengine.Q;
import com.nflabs.zeppelin.zengine.Z;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.ZQLException;

public class ZQLSessionManager implements JobListener {
	Logger logger = Logger.getLogger(ZQLSessionManager.class);
	Map<String, ZQLSession> active = new HashMap<String, ZQLSession>();
	Gson gson ;
	
	AtomicLong counter = new AtomicLong(0);
	private Scheduler scheduler;
	private String sessionPersistBasePath;
	private FileSystem fs;
	
	SimpleDateFormat pathFormat = new SimpleDateFormat("yyyy/MM/dd/HH/");
	
	public ZQLSessionManager(Scheduler scheduler, FileSystem fs, String sessionPersistBasePath){
		this.scheduler = scheduler;
		this.sessionPersistBasePath = sessionPersistBasePath;
		this.fs = fs;
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setPrettyPrinting();
		gsonBuilder.registerTypeAdapter(Z.class, new ZAdapter());

		gson = gsonBuilder.create();		
	}
	
	
	public ZQLSession create(){
		ZQLSession as = new ZQLSession("", this);
		try {
			persist(as);
			synchronized(active){
				active.put(as.getId(), as);
			}
		} catch (IOException e) {
			logger.error("Can't create session ", e);
			return null;
		}
		return as;
	}
	
	public ZQLSession get(String sessionId){
		synchronized(active){
			ZQLSession session = active.get(sessionId);
			if(session!=null) return session;
		}
		
		// search session dir
		Path path = getPathForSessionId(sessionId);
		try {
			ZQLSession session = load(path);
			if(session!=null){
				session.setListener(this);
				return session;
			} else {
				return null;
			}
		} catch (IOException e) {
			return null;
		}
		
	}
	
	
	public ZQLSession run(String sessionId){		
		ZQLSession s = get(sessionId);
		if(s==null) return null;
		s.setListener(this);
		scheduler.submit(s);
		return s;
	}
	
	public ZQLSession dryRun(String sessionId) {
		ZQLSession s = get(sessionId);
		if(s==null) return null;
		try {
			s.dryRun();
			persist(s);			
			logger.debug("---------------- persist -------------");
		} catch (ZException e) {
			logger.error("z error", e);
		} catch (ZQLException e) {
			logger.error("zql error", e);			
		} catch (IOException e) {
			logger.error("persist error", e);			
		}
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
	

	public ZQLSession setParams(String sessionId, List<Map<String, Object>> params) {
		ZQLSession s = get(sessionId);
		if(s==null){
			return null;
		} else {
			s.setParams(params);
			try {
				persist(s);
			} catch (IOException e) {
				logger.error("Can't persist session "+sessionId, e);
			}
			return s;
		}
	}
	
	public ZQLSession setName(String sessionId, String name) {
		ZQLSession s = get(sessionId);
		if(s==null){
			return null;
		} else {
			s.setName(name);
			try {
				persist(s);
			} catch (IOException e) {
				logger.error("Can't persist session "+sessionId, e);
			}
			return s;
		}		
	}

	public boolean delete(String sessionId){
		ZQLSession s= get(sessionId);
		if(s==null) return false;
		
		// can't delete running session
		if(s.getStatus()==Status.RUNNING) return false;
		
		synchronized(active){
			active.remove(sessionId);
		}
		Path path = getPathForSessionId(sessionId);
		try {
			return fs.delete(path, true);
		} catch (IOException e) {
			logger.error("Can't remove session file "+path, e);
			return false;
		}
	}
	
	public ZQLSession abort(String sessionId){
		ZQLSession s= get(sessionId);
		if(s==null) return null;
		
		s.abort();
		return s;
	}


	
	public TreeMap<String, ZQLSession> find(Date from, Date to, int max){
		if(to==null){
			to = new Date();
		}
		if(from==null){
			from = new Date(to.getTime()-(1000*60*60*24*7));
		}
		TreeMap<String, ZQLSession> found = new TreeMap<String, ZQLSession>();
		
		for(Date cur=to; cur.before(from)==false; cur = new Date(cur.getTime()-(1000*60*60))){
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
	
	
	@Override
	public void statusChange(Job job) {
		try {
			if(job.getStatus()==Status.FINISHED){
				synchronized(active){
					active.remove(job.getId());
				}
			} else {
				synchronized(active){
					active.put(job.getId(), (ZQLSession) job);
				}
			}
			if(job.getStatus()==Status.ERROR && job.getException()!=null){
				logger.error("Session error", job.getException());
			} 
			logger.info("Session "+job.getId()+" status changed "+job.getStatus());
			persist((ZQLSession) job);
		} catch (IOException e) {
			logger.error("Can't persist session "+job.getId(), e);
		}
		
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
		
		if(session.getStatus()!=Status.FINISHED){
			synchronized(active){
				active.put(session.getId(), session);
			}
		}
		ins.close();
		return session;
	}





}

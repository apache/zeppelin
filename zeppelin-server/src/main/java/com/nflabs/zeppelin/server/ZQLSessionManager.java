package com.nflabs.zeppelin.server;

import java.io.File;
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
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.Job.Status;
import com.nflabs.zeppelin.scheduler.JobListener;
import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.zengine.Z;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.ZQLException;

public class ZQLSessionManager implements JobListener {
	private static final String HISTORY_DIR_NAME = "/history";
	private static final String CURRENT_SESSION_FILE_NAME = "/current";
	Logger logger = LoggerFactory.getLogger(ZQLSessionManager.class);
	Map<String, ZQLSession> active = new HashMap<String, ZQLSession>();
	Gson gson ;
	
	AtomicLong counter = new AtomicLong(0);
	private Scheduler scheduler;
	private String sessionPersistBasePath;
	private FileSystem fs;
	
	SimpleDateFormat historyPathFormat = new SimpleDateFormat("yyyy-MM-dd_HHmmss_SSS");
	private StdSchedulerFactory quertzSchedFact;
	private org.quartz.Scheduler quertzSched;
	
	public ZQLSessionManager(Scheduler scheduler, FileSystem fs, String sessionPersistBasePath) throws SchedulerException{
		this.scheduler = scheduler;
		this.sessionPersistBasePath = sessionPersistBasePath;
		this.fs = fs;
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setPrettyPrinting();
		gsonBuilder.registerTypeAdapter(Z.class, new ZAdapter());

		gson = gsonBuilder.create();
		
		quertzSchedFact = new org.quartz.impl.StdSchedulerFactory();

		quertzSched = quertzSchedFact.getScheduler();
		quertzSched.start();
		
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
			ZQLSession session = load(sessionId, new Path(path.toString()+CURRENT_SESSION_FILE_NAME), false);			
			if(session!=null){				
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
		if (s==null) { return null; }

		if (s.getStatus() == Status.RUNNING) { 
		    return s; 
		}
		s.setListener(this);
		scheduler.submit(s);
		return s;
	}
	
	public ZQLSession dryRun(String sessionId) {
		ZQLSession s = get(sessionId);
		if (s==null) {return null;}
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
		if (s==null) {
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
		if (s==null) {
			return null;
		} else {
			s.setJobName(name);
			try {
				persist(s);
			} catch (IOException e) {
				logger.error("Can't persist session "+sessionId, e);
			}
			return s;
		}		
	}
	
	public ZQLSession setCron(String sessionId, String cron) {
		ZQLSession s = get(sessionId);
		if (s==null) {
			return null;
		} else {
			s.setCron(cron);			
			try {
				persist(s);
				refreshQuartz(s);
			} catch (IOException e) {
				logger.error("Can't persist session "+sessionId, e);
			}
			return s;
		}		
	}

	public boolean delete(String sessionId){
		ZQLSession s= get(sessionId);
		if (s==null) { return false; }
		
		// can't delete running session
		if(s.getStatus()==Status.RUNNING) return false;
		
		synchronized(active){
			removeQuartz(s);
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
		if (s==null) { return null; }
		
		s.abort();
		return s;
	}
	
	public TreeMap<String, ZQLSession> list(){
		TreeMap<String, ZQLSession> found = new TreeMap<String, ZQLSession>();
		Path dir = new Path(sessionPersistBasePath);
		try {
		    if (!fs.getFileStatus(dir).isDir()) {
		        return found;
			}
		} catch (IOException e) {
			logger.error("Can't scan dir "+dir, e);
			return found;
		}
		
		FileStatus[] files = null;
		try {
			files = fs.listStatus(dir);
		} catch (IOException e) {
			logger.error("Can't list dir "+dir, e);
			e.printStackTrace();
		}
		if (files==null) { return found; }
			
		Arrays.sort(files, new Comparator<FileStatus>(){
		    @Override public int compare(FileStatus a, FileStatus b) {
				String aName = a.getPath().getName();
				String bName = b.getPath().getName();
				return bName.compareTo(aName);
			}				
		});
			
		for(FileStatus f : files) {
			ZQLSession session;
			try {
				Path path = f.getPath();
				String sessionId = getSessionIdFromPath(path);
				session = load(sessionId, new Path(path.toString()+CURRENT_SESSION_FILE_NAME), false);
				if (session!=null) {
					found.put(session.getId(), session);
				} else {
					logger.error("Session not loaded "+f.getPath());
				}
			} catch (IOException e) {
				logger.error("Can't load session from path "+f.getPath(), e);
			}
		}
		return found;
	}
	
	private Path getPathForSession(ZQLSession session){
		return getPathForSessionId(session.getId());
	}
	
	private Path getPathForSessionId(String sessionId){
		return new Path(sessionPersistBasePath+"/"+sessionId);
	}
	
	
	private String getSessionIdFromPath(Path path){
		return new File(path.getName()).getName();
	}
	
	private void makeHistory(String sessionId) throws IOException{
		Path from = new Path(getPathForSessionId(sessionId).toString()+CURRENT_SESSION_FILE_NAME);
		Path to = new Path(getPathForSessionId(sessionId).toString()+HISTORY_DIR_NAME+"/"+historyPathFormat.format(new Date()));
		fs.rename(from, to);		
	}
	
	private void persist(ZQLSession session) throws IOException{		
		String json = gson.toJson(session);
		Path path = getPathForSession(session);
		fs.mkdirs(new Path(path.toString()+HISTORY_DIR_NAME));
		FSDataOutputStream out = fs.create(new Path(path.toString()+CURRENT_SESSION_FILE_NAME), true);
		out.writeBytes(json);
		out.close();
	}
	
	@Override
	public void statusChange(Job job) {
		try {
			if(job.getStatus()==Status.ERROR && job.getException()!=null){
				logger.error("Session error", job.getException());
			} 
			
			if(job.getStatus()==Status.FINISHED){
				makeHistory(((ZQLSession)job).getId());
			}
			
			logger.info("Session "+job.getId()+" status changed "+job.getStatus());
			persist((ZQLSession) job);
		} catch (IOException e) {
			logger.error("Can't persist session "+job.getId(), e);
		}
		
	}

	private ZQLSession load(String sessionId, Path path, boolean history) throws IOException{
		
		synchronized(active){			
			if(active.containsKey(sessionId)==false){
				if(fs.isFile(path)==false){
					return null;
				}
				FSDataInputStream ins = fs.open(path);
				ZQLSession session = gson.fromJson(new InputStreamReader(ins), ZQLSession.class);
				if(session.getStatus()==Status.RUNNING){
					session.setStatus(Status.ABORT);
				}
				ins.close();
				
				if(history==false){
					session.setListener(this);
					active.put(session.getId(), session);
				
					refreshQuartz(session);
				}
				return session;
			} else {
				return active.get(sessionId);
			}
		}
	}
	
	public void deleteHistory(String sessionId, String name){
		Path file = new Path(getPathForSessionId(sessionId)+HISTORY_DIR_NAME+"/"+name);
		try {
			if(fs.isFile(file)){
				fs.delete(file, true);
			}
		} catch (IOException e) {
			logger.error("Can't delete history "+file, e);
		}
	}

	public void deleteHistory(String sessionId){
		Path file = new Path(getPathForSessionId(sessionId)+HISTORY_DIR_NAME);
		try {
			fs.delete(file, true);
			fs.mkdirs(file);
		} catch (IOException e) {
			logger.error("Can't delete history "+file, e);
		}
	}

	
	public ZQLSession getHistory(String sessionId, String name){
		Path file = new Path(getPathForSessionId(sessionId)+HISTORY_DIR_NAME+"/"+name);
		try {
			return load(sessionId, file, true);
		} catch (IOException e) {
			logger.error("Can't load session history "+file, e);
			return null;
		}		
	}
	
	public TreeMap<String, ZQLSession> listHistory(String sessionId){
		TreeMap<String, ZQLSession> found = new TreeMap<String, ZQLSession>();
		Path dir = new Path(getPathForSessionId(sessionId)+HISTORY_DIR_NAME);
		try {
		    if (!fs.getFileStatus(dir).isDir()) {
		        return found;
			}
		} catch (IOException e) {
			logger.error("Can't scan dir "+dir, e);
			return found;
		}
		
		FileStatus[] files = null;
		try {
			files = fs.listStatus(dir);
		} catch (IOException e) {
			logger.error("Can't list dir "+dir, e);
			e.printStackTrace();
		}
		if (files==null) { return found; }
			
		Arrays.sort(files, new Comparator<FileStatus>(){
		    @Override public int compare(FileStatus a, FileStatus b) {
				String aName = a.getPath().getName();
				String bName = b.getPath().getName();
				return bName.compareTo(aName);
			}				
		});
			
		for(FileStatus f : files) {
			ZQLSession session;
			try {
				Path path = f.getPath();
				session = load(sessionId, path, true);
				if (session!=null) {
					found.put(f.getPath().getName(), session);
				} else {
					logger.error("Session not loaded "+f.getPath());
				}
			} catch (IOException e) {
				logger.error("Can't load session from path "+f.getPath(), e);
			}
		}
		return found;		
	}

	public static class SessionCronJob implements org.quartz.Job {
		static ZQLSessionManager sm;
		
		public SessionCronJob() {
		}

		public void execute(JobExecutionContext context) throws JobExecutionException {
			String sessionId = context.getJobDetail().getKey().getName();
			sm.run(sessionId);						
		}
	}
	
	private void removeQuartz(ZQLSession session){
		try {
			quertzSched.deleteJob(new JobKey(session.getId(), "zql"));
		} catch (SchedulerException e) {
			logger.error("Failed to delete Quartz job", e);
		}	
	}
	
	private void refreshQuartz(ZQLSession session){
		removeQuartz(session);
		if(session.getCron()==null || session.getCron().trim().length()==0){
			return;
		}

		SessionCronJob.sm = this;
		JobDetail job = JobBuilder.newJob(SessionCronJob.class)
			      .withIdentity(session.getId(), "zql")
			      .usingJobData("sessionId", session.getId())
			      .build();
				
		CronTrigger trigger = TriggerBuilder.newTrigger()
		  .withIdentity("trigger_"+session.getId(), "zql")
		  .withSchedule(CronScheduleBuilder.cronSchedule(session.getCron()))
		  .forJob(session.getId(), "zql")
		  .build();

		try {
			quertzSched.scheduleJob(job, trigger);
		} catch (SchedulerException e) {
			logger.error("Failed to schedule Quartz job",e);
		}
	}
}

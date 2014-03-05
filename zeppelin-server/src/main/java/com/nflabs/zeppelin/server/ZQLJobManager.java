package com.nflabs.zeppelin.server;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.IOUtils;
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
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.Job.Status;
import com.nflabs.zeppelin.scheduler.JobListener;
import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.util.Util;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.ZQLException;
import com.nflabs.zeppelin.zengine.Zengine;
import com.nflabs.zeppelin.zengine.stmt.Z;

public class ZQLJobManager implements JobListener {
	private static final String HISTORY_DIR_NAME = "/history";
	private static final String CURRENT_JOB_FILE_NAME = "/current";
	private static final String HISTORY_PATH_FORMAT="yyyy-MM-dd_HHmmss_SSS";
	Logger logger = LoggerFactory.getLogger(ZQLJobManager.class);
	Map<String, ZQLJob> active = new HashMap<String, ZQLJob>();
	Gson gson ;
	
	AtomicLong counter = new AtomicLong(0);
	private Scheduler scheduler;
	private String jobPersistBasePath;
	private FileSystem fs;
	
	private StdSchedulerFactory quertzSchedFact;
	private org.quartz.Scheduler quertzSched;
	private Zengine zengine;
	
	public ZQLJobManager(Zengine zengine, FileSystem fs, Scheduler scheduler, String jobPersistBasePath) throws SchedulerException{
		this.scheduler = scheduler;
		this.jobPersistBasePath = jobPersistBasePath;
		this.zengine = zengine;
		
		this.fs = fs;
		
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setPrettyPrinting();
		gsonBuilder.registerTypeAdapter(Z.class, new ZAdapter());

		gson = gsonBuilder.create();
		
		quertzSchedFact = new org.quartz.impl.StdSchedulerFactory();

		quertzSched = quertzSchedFact.getScheduler();
		quertzSched.start();
		
	}
	
	
	public ZQLJob create(){
		ZQLJob as = new ZQLJob("", zengine, this);
		try {
			persist(as);
			synchronized(active){
				active.put(as.getId(), as);
			}
		} catch (IOException e) {
			logger.error("Can't create job ", e);
			return null;
		}
		return as;
	}
	
	public ZQLJob get(String jobId){
		synchronized(active){
			ZQLJob job = active.get(jobId);
			if(job!=null) return job;
		}
		
		// search job dir
		Path path = getPathForJobId(jobId);
		try {
			ZQLJob job = load(jobId, new Path(path.toString()+CURRENT_JOB_FILE_NAME), false);			
			if(job!=null){				
				return job;
			} else {
				return null;
			}
		} catch (IOException e) {
			return null;
		}
		
	}
	
	
	public ZQLJob run(String jobId){		
		ZQLJob s = get(jobId);
		if (s==null) { return null; }

		if (s.getStatus() == Status.RUNNING) { 
		    return s; 
		} else {
			s.setStatus(Status.READY);
		}
		s.setListener(this);
		scheduler.submit(s);
		return s;
	}
	
	public ZQLJob dryRun(String jobId) {
		ZQLJob s = get(jobId);
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
	
	public ZQLJob setZql(String jobId, String zql){
		ZQLJob s = get(jobId);
		if(s==null){
			return null;
		} else {
			s.setZQL(zql);
			try {
				persist(s);
			} catch (IOException e) {
				logger.error("Can't persist job "+jobId, e);
			}
			return s;
		}
	}
	

	public ZQLJob setParams(String jobId, List<Map<String, Object>> params) {
		ZQLJob s = get(jobId);
		if (s==null) {
			return null;
		} else {
			s.setParams(params);
			try {
				persist(s);
			} catch (IOException e) {
				logger.error("Can't persist job "+jobId, e);
			}
			return s;
		}
	}
	
	public ZQLJob setName(String jobId, String name) {
		ZQLJob s = get(jobId);
		if (s==null) {
			return null;
		} else {
			s.setJobName(name);
			try {
				persist(s);
			} catch (IOException e) {
				logger.error("Can't persist job "+jobId, e);
			}
			return s;
		}		
	}
	
	public ZQLJob setCron(String jobId, String cron) {
		ZQLJob s = get(jobId);
		if (s==null) {
			return null;
		} else {
			s.setCron(cron);			
			try {
				persist(s);
				refreshQuartz(s);
			} catch (IOException e) {
				logger.error("Can't persist job "+jobId, e);
			}
			return s;
		}		
	}

	public boolean delete(String jobId){
		ZQLJob s= get(jobId);
		if (s==null) { return false; }
		
		// can't delete running job
		if(s.getStatus()==Status.RUNNING) return false;
		
		synchronized(active){
			removeQuartz(s);
			active.remove(jobId);
		}
		Path path = getPathForJobId(jobId);
		try {
			return fs.delete(path, true);
		} catch (IOException e) {
			logger.error("Can't remove job file "+path, e);
			return false;
		}
	}
	
	public ZQLJob abort(String jobId){
		ZQLJob s= get(jobId);
		if (s==null) { return null; }
		
		s.abort();
		return s;
	}
	
	public TreeMap<String, ZQLJob> list(){
		TreeMap<String, ZQLJob> found = new TreeMap<String, ZQLJob>();
		Path dir = new Path(jobPersistBasePath);
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
			ZQLJob job;
			try {
				Path path = f.getPath();
				String jobId = getJobIdFromPath(path);
				job = load(jobId, new Path(path.toString()+CURRENT_JOB_FILE_NAME), false);
				if (job!=null) {
					found.put(job.getId(), job);
				} else {
					logger.error("Job not loaded "+f.getPath());
				}
			} catch (IOException e) {
				logger.error("Can't load job from path "+f.getPath(), e);
			}
		}
		return found;
	}
	
	private Path getPathForJob(ZQLJob job){
		return getPathForJobId(job.getId());
	}
	
	private Path getPathForJobId(String jobId){
		return new Path(jobPersistBasePath+"/"+jobId);
	}
	
	
	private String getJobIdFromPath(Path path){
		return new File(path.getName()).getName();
	}
	
	private void makeHistory(String jobId) throws IOException{
		SimpleDateFormat historyPathFormat = new SimpleDateFormat(HISTORY_PATH_FORMAT);
		Path from = new Path(getPathForJobId(jobId).toString()+CURRENT_JOB_FILE_NAME);
		Path to = new Path(getPathForJobId(jobId).toString()+HISTORY_DIR_NAME+"/"+historyPathFormat.format(new Date()));
		fs.rename(from, to);		
	}

	private void persist(ZQLJob job) throws IOException{		
		String json = gson.toJson(job);
		Path path = getPathForJob(job);
		fs.mkdirs(new Path(path.toString()+HISTORY_DIR_NAME));
		FSDataOutputStream out = fs.create(new Path(path.toString()+CURRENT_JOB_FILE_NAME), true);		
		out.write(json.getBytes(zengine.getConf().getString(ConfVars.ZEPPELIN_ENCODING)));
		out.close();
	}

	@Override
	public void beforeStatusChange(Job job, Status before, Status after) {
		try {
			if(after==Status.ERROR && job.getException()!=null){
				logger.error("Job error", job.getException());
			} 
			
			logger.info("Job "+job.getId()+" status changed from "+before+" to "+after);
			
			// clone and change status to save
			ZQLJob jobToSave = ((ZQLJob) job).clone();
			jobToSave.setListener(null);
			jobToSave.setStatus(after);			
			
			persist(jobToSave);
			if(after==Status.FINISHED){
				makeHistory(jobToSave.getId()); // rename
				persist(jobToSave); // create current
			}
		} catch (IOException e) {
			logger.error("Can't persist job "+job.getId(), e);
		}		
	}
	
	@Override
	public void afterStatusChange(Job job, Status before, Status after) {

	}

	private ZQLJob load(String jobId, Path path, boolean history) throws IOException{
		
		synchronized(active){			
			if(active.containsKey(jobId)==false || history==true){
				if(fs.isFile(path)==false){
					return null;
				}
				FSDataInputStream ins = fs.open(path);
				String json = IOUtils.toString(ins, zengine.getConf().getString(ConfVars.ZEPPELIN_ENCODING));
				ZQLJob job = gson.fromJson(json, ZQLJob.class);
				if(job.getStatus()==Status.RUNNING){
					job.setStatus(Status.ABORT);
				}
				ins.close();
				
				// inject zengine object. Zengine object inside of ZQLJob is not persisted to File. 
				// therefore after loading ZQLJob from file, we need inject Zengine object again.
				job.setZengine(zengine);
				
				if(history==false){
					// inject listener. the same reason why we're injecting zengine.
					job.setListener(this);					
					active.put(job.getId(), job);
					refreshQuartz(job);
				}
				return job;
			} else {
				return active.get(jobId);
			}
		}
	}
	
	public void deleteHistory(String jobId, String name){
		Path file = new Path(getPathForJobId(jobId)+HISTORY_DIR_NAME+"/"+name);
		try {
			if(fs.isFile(file)){
				fs.delete(file, true);
			}
		} catch (IOException e) {
			logger.error("Can't delete history "+file, e);
		}
	}

	public void deleteHistory(String jobId){
		Path file = new Path(getPathForJobId(jobId)+HISTORY_DIR_NAME);
		try {
			fs.delete(file, true);
			fs.mkdirs(file);
		} catch (IOException e) {
			logger.error("Can't delete history "+file, e);
		}
	}

	
	public ZQLJob getHistory(String jobId, String name){
		Path file = new Path(getPathForJobId(jobId)+HISTORY_DIR_NAME+"/"+name);
		try {
			return load(jobId, file, true);
		} catch (IOException e) {
			logger.error("Can't load job history "+file, e);
			return null;
		}		
	}
	
	public TreeMap<String, ZQLJob> listHistory(String jobId){
		TreeMap<String, ZQLJob> found = new TreeMap<String, ZQLJob>();
		Path dir = new Path(getPathForJobId(jobId)+HISTORY_DIR_NAME);
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
			ZQLJob job;
			try {
				Path path = f.getPath();
				job = load(jobId, path, true);
				if (job!=null) {
					found.put(f.getPath().getName(), job);
				} else {
					logger.error("Job not loaded "+f.getPath());
				}
			} catch (IOException e) {
				logger.error("Can't load job from path "+f.getPath(), e);
			}
		}
		return found;		
	}

	public static class JobCronJob implements org.quartz.Job {
		static ZQLJobManager sm;
		
		public JobCronJob() {
		}

		public void execute(JobExecutionContext context) throws JobExecutionException {
			String jobId = context.getJobDetail().getKey().getName();
			sm.run(jobId);						
		}
	}
	
	private void removeQuartz(ZQLJob job){
		try {
			quertzSched.deleteJob(new JobKey(job.getId(), "zql"));
		} catch (SchedulerException e) {
			logger.error("Failed to delete Quartz job", e);
		}	
	}
	
	private void refreshQuartz(ZQLJob job){
		removeQuartz(job);
		if(job.getCron()==null || job.getCron().trim().length()==0){
			return;
		}

		JobCronJob.sm = this;
		JobDetail newjob = JobBuilder.newJob(JobCronJob.class)
			      .withIdentity(job.getId(), "zql")
			      .usingJobData("jobId", job.getId())
			      .build();
				
		CronTrigger trigger = TriggerBuilder.newTrigger()
		  .withIdentity("trigger_"+job.getId(), "zql")
		  .withSchedule(CronScheduleBuilder.cronSchedule(job.getCron()))
		  .forJob(job.getId(), "zql")
		  .build();

		try {
			quertzSched.scheduleJob(newjob, trigger);
		} catch (SchedulerException e) {
			logger.error("Failed to schedule Quartz job",e);
		}
	}
}

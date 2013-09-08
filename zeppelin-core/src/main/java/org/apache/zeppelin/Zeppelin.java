package org.apache.zeppelin;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.zeppelin.driver.Driver;
import org.apache.zeppelin.driver.DriverManager;
import org.apache.zeppelin.driver.Progress;
import org.apache.zeppelin.job.Job;
import org.apache.zeppelin.job.JobHistoryManager;
import org.apache.zeppelin.job.JobId;
import org.apache.zeppelin.job.JobInfo;
import org.apache.zeppelin.job.JobResult;
import org.apache.zeppelin.job.JobRunner;
import org.apache.zeppelin.job.JobRunnerListener;
import org.apache.zeppelin.job.JobRunner.Status;
import org.apache.zeppelin.zql.Zql;

public class Zeppelin implements JobRunnerListener {
	Logger logger = Logger.getLogger(Zeppelin.class.getName());
	private ThreadPoolExecutor executorPool;
	private DriverManager driverManager;
	LinkedHashMap<JobId, JobRunner> jobs = new LinkedHashMap<JobId, JobRunner>();
	private JobHistoryManager jobHistoryManager;
	
	public Zeppelin(String historyPath){
        //Get the ThreadFactory implementation to use
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        //creating the ThreadPoolExecutor
        int corePoolSize = 10; //  the number of threads to keep in the pool, even if they are idle.
        int maximumPoolSize = 20; //the maximum number of threads to allow in the pool. 
        long keepAliveTime = 10; //when the number of threads is greater than the core, this is the maximum time that excess idle threads will wait for new tasks before terminating.
        executorPool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(corePoolSize), threadFactory);
        driverManager = new DriverManager();
        jobHistoryManager = new JobHistoryManager(new File(historyPath));
	}
	

	public JobId [] submit(Zql zql, String driverClass) throws IOException{
		List<Job> jobList = zql.compile();
		JobId [] jobIdList = new JobId[jobList.size()];
		for(int i=0; i<jobList.size(); i++){
			Job job = jobList.get(i);
			jobIdList[i] = submit(job, driverClass);
		}
		return jobIdList;
	}

	
	/**
	 * Run job using specific driver
	 * @param job job object
	 * @param driverClass driver to run this job
	 * @return job id
	 */
	public JobId submit(Job job, String driverClass){
		JobRunner jobRunner = new JobRunner(job, driverClass, driverManager, this);
		executorPool.execute(jobRunner);
		return job.getJobId();
	}
	
	/**
	 * Abort the job
	 * @param jobId
	 */
	public void abort(JobId jobId){
		synchronized(jobs){
			JobRunner jobRunner = jobs.get(jobId);
			if(jobRunner!=null) {
				jobRunner.abort();
			}	
		}
		
	}
	/**
	 * Get all running jobs
	 * @return
	 * @throws IOException 
	 */
	public List<JobInfo> getAllJobs(Date fromInclusive, Date toExclusive) throws IOException{
		List<JobInfo> jobList = new LinkedList<JobInfo>();
		synchronized(jobs){
			for(JobRunner runner : jobs.values()){
				JobId id = runner.getJob().getJobId();
				
				if(id.getDate().before(fromInclusive) || id.getDate().before(toExclusive)==false){
					break;
				}
				Job job = runner.getJob();
				Driver driver = runner.getDriver();
				Progress progress = (driver==null) ? null : driver.progress(job);
				jobList.add(new JobInfo(job, runner.getStatus(), progress, runner.getDriverClass(), runner.getResult()));
			}
		}
		jobList.addAll(jobHistoryManager.getAll(fromInclusive, toExclusive));
		return jobList;
	}

	class SortByFileName implements Comparator<File>{
	     public int compare(File f1, File f2) {
	    	 return f1.getName().compareTo(f2.getName());
	     }
	}
	
	
	/**
	 * Get job information
	 * @param jobId
	 * @return
	 * @throws IOException 
	 */
	public JobInfo getJobInfo(JobId jobId) throws IOException{
		// search for running job
		synchronized(jobs){
			JobRunner runner = jobs.get(jobId);
			if(runner!=null){
				Job job = runner.getJob();
				Driver driver = runner.getDriver();
				Progress progress = (driver==null) ? null : driver.progress(job);
				return new JobInfo(job, runner.getStatus(), progress, runner.getDriverClass(), runner.getResult());
			}
		}
		
		// search for history
		JobInfo jobInfo = jobHistoryManager.load(jobId);
		return jobInfo;
	}



	@Override
	public void onStatusChange(Job job, JobRunner jobRunner, JobRunner.Status status) {
		if(status==Status.CREATED){
			synchronized(jobs){
				jobs.put(job.getJobId(), jobRunner);
			}
		} else if(status==Status.RUNNING){
			
		} else if(status==Status.FINISHED){

		} else if(status==Status.ERROR){

		} else if(status==Status.ABORTED){
		}
		
		if(jobRunner.isTerminated()){
			synchronized(jobs){
				jobs.remove(job.getJobId());
			}
			
			Driver driver = jobRunner.getDriver();
			Progress progress = (driver==null) ? null : driver.progress(job);
			JobInfo jobInfo = new JobInfo(job, jobRunner.getStatus(), progress, jobRunner.getDriverClass(), jobRunner.getResult());
			try {
				jobHistoryManager.save(jobInfo);
			} catch (IOException e) {
				logger.log(Level.SEVERE, "Can not write history for job "+job.getJobId(), e);
			}
		}
		
	}
	
}

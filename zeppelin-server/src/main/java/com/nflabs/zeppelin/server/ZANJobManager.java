package com.nflabs.zeppelin.server;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.Job.Status;
import com.nflabs.zeppelin.scheduler.JobListener;
import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.zan.ZAN;

/**
 * ZAN Job manager currently expect FIFO scheduler.
 * @author moon
 *
 */
public class ZANJobManager implements JobListener {
	private ZAN zan;
	private Scheduler scheduler;
	List<ZANJob> runningJobs = new LinkedList<ZANJob>();

	public ZANJobManager(ZAN zan, Scheduler scheduler){
		this.zan = zan;
		this.scheduler = scheduler;
	}
	
	public void install(String libName){
		ZANJob job = new ZANJob("Install "+libName, zan, ZANJob.Operation.INSTALL, libName, this);
		scheduler.submit(job);
	}
	
	public void uninstall(String libName){
		ZANJob job = new ZANJob("Uninstall "+libName, zan, ZANJob.Operation.UNINSTALL, libName, this);
		scheduler.submit(job);
	}
	
	public void upgrade(String libName){
		ZANJob job = new ZANJob("Upgrade "+libName, zan, ZANJob.Operation.UPGRADE, libName, this);
		scheduler.submit(job);
	}
	
	public void update(){
		ZANJob job = new ZANJob("Update", zan, ZANJob.Operation.UPDATE, null, this);
		scheduler.submit(job);		
	}

	@Override
	public void beforeStatusChange(Job job, Status before, Status after) {
		if(after==Status.READY){
			synchronized(runningJobs){
				runningJobs.add((ZANJob) job);
			}
		}
	}

	@Override
	public void afterStatusChange(Job job, Status before, Status after) {
		if(job.isTerminated()){
			synchronized(runningJobs){
				runningJobs.remove(job);
			}
		}
	}
	
	public List<ZANJob> getJobsRunning(){
		synchronized(runningJobs){
			return new LinkedList<ZANJob>(runningJobs);
		}
	}
}	


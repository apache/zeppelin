package com.nflabs.zeppelin.zan;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.SubmoduleInitCommand;
import org.eclipse.jgit.api.SubmoduleStatusCommand;
import org.eclipse.jgit.api.SubmoduleUpdateCommand;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.submodule.SubmoduleStatus;
import org.eclipse.jgit.submodule.SubmoduleStatusType;

public class ZAN {
	Logger logger = Logger.getLogger(ZAN.class);
	private String localPath;
	private String remotePath;
	private FileSystem dfs;
	private String zanRepo;


	/**
	 * Initialize ZAN with localRepository on local file system
	 * @param localPath
	 */
	public ZAN(String zanRepo, String localPath){
		this(zanRepo, localPath, null, null);
	}
	
	
	/**
	 * Initialize ZAN with localRepository at localPath and syncwith remote path dfsPath
	 * @param zanRepo ZAN catalog repository url. 
	 * @param localPath local path to install zan library
	 * @param remotePath  dfs path to sync, nullable
	 */
	public ZAN(String zanRepo, String localPath, String remotePath, FileSystem dfs){
		this.zanRepo = zanRepo;
		this.localPath = localPath;
		this.remotePath = remotePath;
		this.dfs = dfs;
	}
	
	public void init(ZANProgressMonitor progressListener) throws ZANException{
		String branch = "master";
		String commit = "HEAD";
		File lp = new File(localPath);

		try {
			if (lp.exists()==false) {
				logger.info("Init");
				CloneCommand clone = Git.cloneRepository();
				clone.setURI(zanRepo);
				clone.setDirectory(lp);
				clone.setBranch(branch);
				clone.setNoCheckout(true);
				if(progressListener!=null){
					clone.setProgressMonitor(progressListener);
				}
				
				clone.call();
	
				Git git = Git.open(lp);
				
				// fetch
				git.fetch().call();
	
				git.checkout().setName(branch)
							  .setCreateBranch(true)
							  .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM)
							  .setStartPoint("origin/"+branch)
							  .call();
				
				git.reset().setRef(commit)
						   .setMode(ResetType.HARD)
						   .call();
				git.close();
			}
		} catch (GitAPIException e) {
			throw new ZANException(e);
		} catch (IOException e) {
			throw new ZANException(e);
		}
	}
	
	
	public void install(String libraryName, ZANProgressMonitor progressListener) throws ZANException{
		init(null);
		File lp = getLocalLibraryPath(libraryName);
		if (lp.exists()) {
			throw new ZANException(libraryName+" already installed");
		}

		Git git;
		try {
			logger.info("install "+libraryName);
			git = Git.open(new File(localPath));
			SubmoduleInitCommand init = git.submoduleInit();
			init.addPath(libraryName);
			init.call();

			SubmoduleUpdateCommand submoduleUpdate = git.submoduleUpdate();
			submoduleUpdate.addPath(libraryName);
			if(progressListener!=null){
				submoduleUpdate.setProgressMonitor(progressListener);
			}
			Collection<String> ret = submoduleUpdate.call();
			git.close();

			if ( ret==null || ret.isEmpty() || lp.exists()==false) {
				throw new ZANException("Can't update submodule "+libraryName);
			}
			sync(libraryName);
		} catch (Exception e) {
			throw new ZANException(e);
		}
	}
	
	/**
	 * Get information of the library
	 * @param libraryName
	 * @return
	 * @throws ZANException
	 */
	public Info info(String libraryName) throws ZANException{
		init(null);
		Git git;
		try {
			git = Git.open(new File(localPath));

			// git status libraryName
			StatusCommand sc = git.status();
			sc.addPath(libraryName);
			Status st = sc.call();

			boolean updateAvailable = false;

			if(st.getModified().size()>0 ||
			   st.getChanged().size()>0 ||
			   st.getAdded().size()>0 ||
			   st.getRemoved().size()>0){
				updateAvailable = true;
			}

			// git submodule status libraryName
			SubmoduleStatusCommand ssc = git.submoduleStatus();
			ssc.addPath(libraryName);
			Map<String, SubmoduleStatus> status = ssc.call();
			if ( status==null || status.size()!=1 ){
				// not in git
				File libDir = new File(localPath, libraryName);
				if (libDir.isDirectory()==false) {
					return null;
				} else {
					return new Info(libraryName, libDir.getAbsolutePath());
				}
			} else {	
				// construct info
				SubmoduleStatus ss = status.get(libraryName);
				boolean installed = (ss.getType()==SubmoduleStatusType.INITIALIZED || ss.getType()==SubmoduleStatusType.REV_CHECKED_OUT) ? true : false;
				String commit = ss.getIndexId().getName();
				String path = ss.getPath();
				git.close();
				Info info = new Info(libraryName, path, installed, updateAvailable, commit);	
				return info;
			}
		} catch (IOException e) {
			throw new ZANException(e);
		} catch (GitAPIException e) {
			throw new ZANException(e);
		}
	}
	
	public List<Info> list() throws ZANException{
		init(null);
		List<Info> infos = new LinkedList<Info>();
		
		Git git;
		try {
			git = Git.open(new File(localPath));
			
			SubmoduleStatusCommand sc = git.submoduleStatus();
			Map<String, SubmoduleStatus> status = sc.call();
			
			Set<String> libNames = new HashSet<String>(status.keySet());
			File[] files = new File(localPath).listFiles();
			if (files!=null) {
				for (File file : files) {
					if (file.isDirectory() == false ) continue;
					String name = file.getName();
					if (name==null || name.startsWith(".") ) continue;					
					if (libNames.contains(name) == false ) {
						libNames.add(name);
					}
				}
			}
		
			for(String k : libNames) {
				infos.add(info(k));
			}
			git.close();
		} catch (IOException e) {
			throw new ZANException(e);
		} catch (GitAPIException e) {
			throw new ZANException(e);
		}
		return infos;
	}
	
	public void upgrade(String libraryName, ZANProgressMonitor progressListener) throws ZANException {
		init(null);
		File lp = getLocalLibraryPath(libraryName);
		if (lp.exists() == false) {
			throw new ZANException(libraryName + " not installed");
		}

		Git git;
		try {
			logger.info("upgrade "+libraryName);
			git = Git.open(new File(localPath));
			SubmoduleInitCommand init = git.submoduleInit();
			init.addPath(libraryName);
			init.call();

			SubmoduleUpdateCommand submoduleUpdate = git.submoduleUpdate();
			submoduleUpdate.addPath(libraryName);
			if(progressListener!=null){
				submoduleUpdate.setProgressMonitor(progressListener);
			}
			Collection<String> ret = submoduleUpdate.call();
			git.close();

			if ( ret==null || ret.isEmpty() || lp.exists()==false) {
				throw new ZANException("Can't update submodule "+libraryName);
			}
			sync(libraryName);
		} catch (Exception e) {
			throw new ZANException(e);
		}
	}

	/**
	 * Remove library
	 * @param libraryName
	 * @throws ZANException
	 */
	public void uninstall(String libraryName) throws ZANException{
		init(null);
		logger.info("uninstall "+libraryName);
		File lp = getLocalLibraryPath(libraryName);
		if (lp.exists()==false) {
			throw new ZANException("library "+libraryName+" not installed");
		}
		
		deleteRecursive(lp);
		sync(libraryName);
	}


	/**
	 * Update zan repository catalog
	 * @throws ZANException 
	 */
	public void update() throws ZANException{
		update(null);
	}
	
	/**
	 * Update zan repository catalog
	 * @throws ZANException 
	 */
	public void update(ZANProgressMonitor progressListener) throws ZANException{
		String branch = "master";
		File lp = new File(localPath);
		try {
			logger.info("update");
			if (lp.exists()==false) {
				init(null);
			} else {
				Git git = Git.open(lp);

				// fetch
				git.fetch().setRemote("origin").call();
				git.rebase().setUpstream("origin/"+branch)
						    .call();
				git.close();
				/*
				git.reset().setRef(commit)
						   .setMode(ResetType.HARD)
						   .call();
						   */
			}
		} catch (GitAPIException e) {
			throw new ZANException(e);
		} catch (IOException e) {
			throw new ZANException(e);
		}			
	}
	
	
	
	private void deleteRecursive(File f){
		if(f.isDirectory()){
			for(File c : f.listFiles()){
				deleteRecursive(c);
			}
			
			f.delete();
		} else if(f.isFile()){
			f.delete();
		}
	}
	
	private File getLocalLibraryPath(String libraryName){
		return new File(localPath+"/"+libraryName);
	}
	
	private Path getRemoteLibraryPath(String libraryName){
		return new Path(remotePath+"/"+libraryName);
	}
	
	public Map<String, List<String>> sync(String libraryName) throws ZANException{
		return sync(libraryName, "/");
	}
	
	private Map<String, List<String>> sync(String libraryName, String relativePath) throws ZANException{
		Map<String, List<String>> changes = new HashMap<String, List<String>>();
		changes.put("added", new LinkedList<String>());
		changes.put("modified", new LinkedList<String>());
		changes.put("removed", new LinkedList<String>());
		
		if (syncDfs()==false) return changes;
		
		File local = getLocalLibraryPath(libraryName+relativePath);
		Path remote = getRemoteLibraryPath(libraryName+relativePath);
		try {
			if (local.exists()==false) { // local not exists, delete remote, too				
				if (dfs.exists(remote)) {
					dfs.delete(remote, true);
					changes.get("removed").add(remote.toString());
				}
			} else if (local.isFile()) { // file
				if (dfs.exists(remote) && dfs.isFile(remote)==false) { // remote is dir
					dfs.delete(remote, true);
					changes.get("removed").add(remote.toString());
				}
				
				if (dfs.exists(remote)) {
					FileStatus remoteStatus = dfs.getFileStatus(remote);
					if(local.length() != remoteStatus.getLen() ||
					   local.lastModified() != remoteStatus.getModificationTime()){
						dfs.delete(remote, true);
						changes.get("modified").add(remote.toString());
						dfs.copyFromLocalFile(new Path(local.getAbsolutePath()), remote);
					}
				} else {
					changes.get("added").add(remote.toString());
					dfs.copyFromLocalFile(new Path(local.getAbsolutePath()), remote);					
				}				
			} else { // directory
				if (dfs.exists(remote)) {
					if (dfs.isFile(remote)) {
						dfs.delete(remote, true);
						changes.get("removed").add(remote.toString());
					}
				} else {
					changes.get("added").add(remote.toString());
					dfs.mkdirs(remote);
				}
				
				File[] files = local.listFiles();
				if(files==null) return changes;
				for(File f : files){
					if(f.getName().startsWith(".")) continue;
					Map<String, List<String>> ch = sync(libraryName, relativePath+"/"+f.getName());
					changes.get("added").addAll(ch.get("added"));
					changes.get("removed").addAll(ch.get("removed"));
					changes.get("modified").addAll(ch.get("modified"));
				}
	
				// delete removed files
				FileStatus[] dfsFiles = dfs.listStatus(remote);
				if (dfsFiles!=null) {
					for (FileStatus dfsFile : dfsFiles) {
						boolean delete = true;
						if (files!=null) {
							for (File f : files) {
								if (f.getName().equals(dfsFile.getPath().getName())) {
									delete = false;
								}
							}
						}
						if(delete==true){
							dfs.delete(dfsFile.getPath(), true);
							changes.get("removed").add(dfsFile.toString());
						}
					}
				}
					
			}
		} catch (IOException e) {
			throw new ZANException(e);
		}
		return changes;
	}
	
	public boolean syncDfs(){
		return (dfs!=null && remotePath!=null);
	}

}

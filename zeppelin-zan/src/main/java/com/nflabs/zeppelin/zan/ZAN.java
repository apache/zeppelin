package com.nflabs.zeppelin.zan;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.revwalk.RevCommit;

public class ZAN {
	
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
	
	public void install(String libraryName, ZANProgressMonitor progressListener) throws ZANException{
		Meta meta;
		try {
			meta = getMeta(libraryName);
		} catch (IOException e) {
			throw new ZANException(e);
		}
		if (meta==null) {
			throw new ZANException(libraryName+" not exists");
		}
		
		File lp = getLocalLibraryPath(libraryName);
		try {
			if (lp.exists()==false) {
				CloneCommand clone = Git.cloneRepository();
				clone.setURI(meta.repository);
				clone.setDirectory(lp);
				clone.setBranch(meta.branch);
				clone.setNoCheckout(true);
				
				if(progressListener!=null){
					clone.setProgressMonitor(progressListener);
				}
				
				clone.call();
			}
			Git git = Git.open(lp);
			
			// fetch
			git.fetch().call();

			git.checkout().setName(meta.branch)
						  .setCreateBranch(true)
						  .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM)
						  .setStartPoint("origin/"+meta.branch)
						  .call();

			git.reset().setRef(meta.commit)
					   .setMode(ResetType.HARD)
					   .call();

			// try to sync
			sync(libraryName);
		} catch (InvalidRemoteException e) {
			throw new ZANException(e);
		} catch (TransportException e) {
			throw new ZANException(e);
		} catch (GitAPIException e) {
			throw new ZANException(e);
		} catch (IOException e) {
			throw new ZANException(e);
		}			
	}
	
	private Meta getMeta(String libraryName) throws IOException{
		File metaFile = new File(localPath+"/.zan/"+libraryName+"/meta");
		if (metaFile.exists()==false) {
			return null;
		} else {
			return Meta.createFromFile(metaFile);
		}
	}
	
	/**
	 * Get information of the library
	 * @param libraryName
	 * @return
	 * @throws ZANException
	 */
	public Info info(String libraryName) throws ZANException{
		Meta meta;
		try {
			meta = getMeta(libraryName);
		} catch (IOException e) {
			throw new ZANException(e);
		}
		
		// update available
		try {
			return getInfoFromMeta(libraryName, meta);
		} catch (IOException e) {
			throw new ZANException(e);
		} catch (NoHeadException e) {
			throw new ZANException(e);
		} catch (GitAPIException e) {
			throw new ZANException(e);
		}
	}
	
	public List<Info> list() throws ZANException{
		List<Info> infos = new LinkedList<Info>();
		File zp = new File(localPath+"/.zan");
		
		File[] files = zp.listFiles();
		if(files==null || files.length==0) return infos;
		
		for(File f : files){
			String libraryName = f.getName();
			if (libraryName.startsWith(".") ) continue;
			if (libraryName.startsWith("#") ) continue;
			if (libraryName.startsWith("~") ) continue;
			if (f.isDirectory()==false) continue;
			
			try {
				Meta meta = getMeta(libraryName);
				infos.add(getInfoFromMeta(libraryName, meta));
			} catch (NoHeadException e) {
				throw new ZANException(e);
			} catch (IOException e) {
				throw new ZANException(e);
			} catch (GitAPIException e) {
				throw new ZANException(e);
			}
		}
		
		return infos;
	}
	
	private Info getInfoFromMeta(String libraryName, Meta meta) throws IOException, NoHeadException, GitAPIException{
		File lp = getLocalLibraryPath(libraryName);
		boolean installed;
		String commit = null;
		
		if (lp.exists()) {
			try {
				Git git = Git.open(lp);
				Iterable<RevCommit> commits = git.log().setMaxCount(1).call();
				RevCommit cm = commits.iterator().next();
				commit = cm.getName();
				installed = true;
			} catch (RepositoryNotFoundException e){
				// library not installed from remote
				installed = false;
			}
		} else {
			installed = false;
		}
				
		
		return new Info(libraryName, meta, installed, commit); 
	}
	
	
	public void upgrade(String libraryName, ZANProgressMonitor progressListener) throws ZANException{
		Meta meta;
		try {
			meta = getMeta(libraryName);
		} catch (IOException e) {
			throw new ZANException(e);
		}
		if (meta==null) {
			throw new ZANException(libraryName+" not exists");
		}
		
		File lp = getLocalLibraryPath(libraryName);
		if (lp.exists()==false) {
			throw new ZANException("library "+libraryName+" not installed");
		}
		
		
		try {
			Git git = Git.open(lp);
			
			// fetch
			git.fetch().call();
			git.reset().setRef(meta.commit)
					   .setMode(ResetType.HARD)
					   .call();

			// try to sync
			sync(libraryName);
		} catch (InvalidRemoteException e) {
			throw new ZANException(e);
		} catch (TransportException e) {
			throw new ZANException(e);
		} catch (GitAPIException e) {
			throw new ZANException(e);
		} catch (IOException e) {
			throw new ZANException(e);
		}
				
	}

	/**
	 * Remove library
	 * @param libraryName
	 * @throws ZANException
	 */
	public void uninstall(String libraryName) throws ZANException{
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
		String commit = "HEAD";
		File lp = getLocalLibraryPath(".zan");
		try {
			if (lp.exists()==false) {
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
			} else {
				Git git = Git.open(lp);

				// fetch
				git.fetch().setRemote("origin").call();
				git.rebase().setUpstream("origin/"+branch)
						    .call();
				/*
				git.reset().setRef(commit)
						   .setMode(ResetType.HARD)
						   .call();
						   */
			}
		} catch (InvalidRemoteException e) {
			throw new ZANException(e);
		} catch (TransportException e) {
			throw new ZANException(e);
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

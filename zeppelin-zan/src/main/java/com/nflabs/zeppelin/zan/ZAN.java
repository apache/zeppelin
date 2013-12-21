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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

public class ZAN {
	
	private String localPath;
	private String remotePath;
	private FileSystem dfs;


	/**
	 * Initialize ZAN with localRepository on local file system
	 * @param localPath
	 */
	public ZAN(String localPath){
		this(localPath, null, null);
	}
	
	
	/**
	 * Initialize ZAN with localRepository at localPath and syncwith remote path dfsPath
	 * @param localPath  
	 * @param dfsPath 
	 */
	public ZAN(String localPath, String remotePath, FileSystem dfs){
		this.localPath = localPath;
		this.remotePath = remotePath;
		this.dfs = dfs;
	}
	
	/**
	 * Install new library
	 * 
	 * @param libraryName
	 * @param reporitoryURL
	 * @param branch
	 * @param commit
	 * @throws ZANException 
	 */
	public void install(String libraryName,
						String reporitoryURL, 
						String branch, 
						String commit, 
						ZANProgressMonitor progressListener) throws ZANException{
		File lp = getLocalLibraryPath(libraryName);
		try {
			if (lp.exists()==false) {
				CloneCommand clone = Git.cloneRepository();
				clone.setURI(reporitoryURL);
				clone.setBranch(branch);
				clone.setDirectory(lp);
				
				if(progressListener!=null){
					clone.setProgressMonitor(progressListener);
				}
				
				clone.call();
			}
			Git git = Git.open(lp);
			git.fetch().call();
			
			ResetCommand reset = git.reset();
			
			reset.setRef(commit);
			reset.setMode(ResetType.HARD);
			
			reset.call();
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

package com.nflabs.zeppelin.zan;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * A library information
 * @author moon
 *
 */
public class Meta {
	public String repository;
	public String branch;
	public String commit;
	
	public Meta(String repository, String branch, String commit) {
		super();
		this.repository = repository;
		this.branch = branch;
		this.commit = commit;
	}

	public void write(File file) throws IOException{
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(this);
		if(file.isFile()){
			file.delete();
		}
		FileUtils.writeStringToFile(file, json, false);
	}
	
	public static Meta createFromFile(File file) throws IOException{
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = FileUtils.readFileToString(file);
		return gson.fromJson(json, Meta.class);
	}
}

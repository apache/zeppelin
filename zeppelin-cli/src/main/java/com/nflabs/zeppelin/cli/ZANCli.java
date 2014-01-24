package com.nflabs.zeppelin.cli;

import java.util.List;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.zan.Info;
import com.nflabs.zeppelin.zan.ZAN;
import com.nflabs.zeppelin.zan.ZANException;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.Zengine;
import com.nflabs.zeppelin.zengine.api.Z;

public class ZANCli {
	public static enum Command{
		help,
		update,
		install,
		upgrade,
		uninstall,
		info,
		list
		;
	}
	public static void main(String [] args) throws ZANException, ZException{

		if(args==null || args.length==0){
			printHelp();
			System.exit(0);
		}
		
		int cmdIndex = 0;
		
		String cmdStr = args[cmdIndex];
		Command cmd = null;
		try{
			cmd = Command.valueOf(cmdStr);
		} catch (IllegalArgumentException e){
			// unsupported command
			System.err.println("Unknown command '"+cmdStr+"'");
			System.exit(-1);
		}


		if (cmd==Command.update) {			
			zan().update();
		} else if (cmd==Command.install) {			
			for(int i=cmdIndex+1; i<args.length; i++){
				zan().install(args[i], null);	
			}			
		} else if (cmd==Command.uninstall) {
			for(int i=cmdIndex+1; i<args.length; i++){
				zan().uninstall(args[i]);	
			}
		} else if (cmd==Command.upgrade) {
			for(int i=cmdIndex+1; i<args.length; i++){
				zan().upgrade(args[i], null);	
			}	
		} else if (cmd==Command.info) {
			for(int i=cmdIndex+1; i<args.length; i++){
				System.out.println("-------------------------------------");
				printInfo(args[i], zan().info(args[i]));	
			}
			System.out.println("-------------------------------------");
		} else if (cmd==Command.list) {
			List<Info> infos = zan().list();
			for(Info info : infos){
				System.out.println("-------------------------------------");
				printInfo(info.getName(), info);				
			}
			System.out.println("-------------------------------------");
		} else if (cmd==Command.help){
			printHelp();
			System.exit(0);
		}
	}
	
	public static ZAN zan() throws ZException{
		ZeppelinConfiguration conf = new ZeppelinConfiguration();
		//TODO(alex): replace with just file system
		Zengine z = new Zengine();
	    z.configure();
		
		String zanRepo = conf.getString(ConfVars.ZEPPELIN_ZAN_REPO);
		String zanLocalRepo = conf.getString(ConfVars.ZEPPELIN_ZAN_LOCAL_REPO);
		String zanSharedRepo = conf.getString(ConfVars.ZEPPELIN_ZAN_SHARED_REPO);
		ZAN zan = new ZAN(zanRepo, zanLocalRepo, zanSharedRepo, z.fs());
		return zan;
	}
	
	public static void printInfo(String name, Info info){
		if (info==null) {
			System.out.println(name + " not found");
		} else {
			System.out.println("Name - "+name);
			System.out.println("Status - "+info.getStatus().toString());
		}
	}
	
	public static void printHelp(){
		System.out.println("help\t\t\t\t- print this messsage");
		System.out.println("update\t\t\t\t- update catalog");
		System.out.println("install [library name]\t\t- install new library");
		System.out.println("upgrade [library name]\t\t- upgrade installed library");
		System.out.println("uninstall [library name]\t- uninstall installed library");
		System.out.println("info [library name]\t\t- print information of the library");
		System.out.println("list");
	}
}

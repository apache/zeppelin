package com.nflabs.zeppelin.cli;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import jline.ConsoleReader;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.result.ResultDataException;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.ZPlan;
import com.nflabs.zeppelin.zengine.ZQLException;
import com.nflabs.zeppelin.zengine.Zengine;
import com.nflabs.zeppelin.zengine.stmt.Z;
import com.nflabs.zeppelin.zengine.stmt.ZQL;

public class ZeppelinCli {
	@SuppressWarnings("static-access")
    public static void main(String args[]) throws Exception{
	
		Options options = new Options();
		options.addOption(OptionBuilder.withArgName("File")
									   .withDescription("Run ZQL in given text file")
									   .hasArg(true)
									   .create("f"));
		options.addOption(OptionBuilder.withArgName("ZQLStatement")
				   .withDescription("Run ZQL statement")
				   .hasArg(true)
				   .create("e"));		
		options.addOption(OptionBuilder.withDescription("See this messages")
									   .withLongOpt("help")
									   .create("h"));

		CommandLineParser parser = new BasicParser();
		CommandLine cmd = parser.parse(options, args);

		Zengine z = new Zengine();

		if(cmd.hasOption("f")){
			ZQL zql = new ZQL();
			zql.load(new File(cmd.getOptionValue("f")));
			LinkedList<Result> rs = zql.compile().execute(z);
			
			for(Result r : rs){
				r.write(System.out);
			}
		} else if(cmd.hasOption("e")){ 			
			ZQL zql = new ZQL(cmd.getOptionValue("e"));
			LinkedList<Result> rs = zql.compile().execute(z);
			for(Result r : rs){
				r.write(System.out);
			}
		} else if(cmd.hasOption("h")){
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("ZeppelinCli", options);
		} else {
			ZeppelinCli cli = new ZeppelinCli();
			cli.run(z);
		}

	}
	
	public ZeppelinCli(){
		
	}
	
    private String readLine(ConsoleReader reader, String promtMessage)
            throws IOException {
        String line = reader.readLine(promtMessage + "\nzeppelin> ");
        return line == null ? null : line.trim();
    }
	 
    public void run(Zengine z) throws IOException {
        printWelcomeMessage();
        ConsoleReader reader = new ConsoleReader();
        reader.setBellEnabled(false);
        String line;

        while ((line = readLine(reader, "")) != null) {
            ZQL zql;
            try {
                zql = new ZQL(line);
    			LinkedList<Result> rs = zql.compile().execute(z);
    			
    			for(Result r : rs){
    				r.write(System.out);
    			}
            } catch (ZException e) {
                e.printStackTrace();
            } catch (ZQLException e) {
                e.printStackTrace();
            } catch (Exception e) {
				e.printStackTrace();
			}

        }
    }

	 private void printWelcomeMessage() {
		System.out.println("Welcome to Zeppelin.");
	 }
}

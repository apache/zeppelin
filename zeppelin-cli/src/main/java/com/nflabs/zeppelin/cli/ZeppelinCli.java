package com.nflabs.zeppelin.cli;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class ZeppelinCli {
	public static void main(String args[]) throws ParseException{
	
		Options options = new Options();
		options.addOption(OptionBuilder.withArgName("File")
									   .withDescription("Run ZQL in given text file")
									   .hasArg(true)
									   .create("e"));
		options.addOption(OptionBuilder.withDescription("See this messages")
									   .withLongOpt("help")
									   .create("h"));
									 
				
		
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = parser.parse(options, args);
		
		if(cmd.hasOption("e")){
			
		} else if(cmd.hasOption("h")){
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("ZeppelinCli", options);
		}
		
	}
}

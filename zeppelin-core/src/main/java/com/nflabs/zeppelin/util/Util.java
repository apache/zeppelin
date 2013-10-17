package com.nflabs.zeppelin.util;

import java.util.ArrayList;
import java.util.List;

public class Util {

	public static String [] split(String str, char split){
		return split(str, new String[]{String.valueOf(split)}, false);
	}
	
	public static String [] split(String str, String [] splitters, boolean includeSplitter){
		String escapeSeq = "\"',;${}";
		char escapeChar = '\\';
		String [] blockStart = new String[]{ "\"", "'", "${" };
		String [] blockEnd = new String[]{ "\"", "'", "}" };
		
		return split(str, escapeSeq, escapeChar, blockStart, blockEnd, splitters, includeSplitter);
		
	}
	
	public static String [] split(
			String str,
			String escapeSeq,
			char escapeChar,
			String [] blockStart,
			String [] blockEnd,
			String [] splitters, 
			boolean includeSplitter
		){
		
		List<String> splits = new ArrayList<String>();
		
		String curString ="";
		int ignoreBlockIndex = -1;
		boolean escape = false;  // true when escape char is found
		int lastEscapeOffset = -1;
		for(int i=0; i<str.length();i++){
			char c = str.charAt(i);

			// escape char detected
			if(c==escapeChar && escape == false){
				escape = true;				
				continue;
			}
			
			// escaped char comes
			if(escape==true){
				if(escapeSeq.indexOf(c)<0){
					curString += escapeChar;
				}
				curString += c;
				escape = false;
				lastEscapeOffset = i;
				continue;
			}
			

			if(ignoreBlockIndex>=0){ // inside of block
				curString += c;
				
				// check if block is finishing
				if(curString.substring(lastEscapeOffset+1).endsWith(blockEnd[ignoreBlockIndex])){
					ignoreBlockIndex = -1;
					continue;
				}
								
			} else { // not in the block
				boolean splitted = false;
				for(String splitter : splitters){
					// forward check for splitter
					if(splitter.compareTo(str.substring(i, Math.min(i+splitter.length(), str.length()-1)))==0){
						splits.add(curString);
						if(includeSplitter==true){
							splits.add(splitter);
						}
						curString = "";
						lastEscapeOffset = -1;
						i+=splitter.length()-1;
						splitted = true;
						break;
					}									
				}
				if(splitted == true){
					continue;
				}
				
				// add char to current string
				curString += c;
				
				// check if block is started
				for(int b=0; b<blockStart.length;b++){
					if(curString.substring(lastEscapeOffset+1).endsWith(blockStart[b])==true){
						ignoreBlockIndex = b; // block is started
					}
				}
			}
		}
		if(curString.length()>0)
			splits.add(curString);
		return splits.toArray(new String[]{});
		
	}
}

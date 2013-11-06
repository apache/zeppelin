package com.nflabs.zeppelin.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Util {

	public static String [] split(String str, char split){
		return split(str, new String[]{String.valueOf(split)}, false);
	}
	
	public static String [] split(String str, String [] splitters, boolean includeSplitter){
		String escapeSeq = "\"',;<%>";
		char escapeChar = '\\';
		String [] blockStart = new String[]{ "\"", "'", "<%", "<" };
		String [] blockEnd = new String[]{ "\"", "'", "%>", ">" };
		
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
		int blockStartPos = -1;
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
				// check multichar block
				for(int b=0; b<blockStart.length; b++){
					if(blockStart[b].compareTo(str.substring(blockStartPos, i))==0){
						ignoreBlockIndex = b;
					}
				}
				
				// check if block is finishing
				if(curString.substring(lastEscapeOffset+1).endsWith(blockEnd[ignoreBlockIndex])){
					
					// the block closer is one of the splitters
					for(String splitter : splitters){
						if(splitter.compareTo(blockEnd[ignoreBlockIndex])==0){
							splits.add(curString);
							if(includeSplitter==true){
								splits.add(splitter);
							}
							curString = "";
							lastEscapeOffset = -1;
							
							break;
						}
					}
					blockStartPos = -1;
					ignoreBlockIndex = -1;
					continue;
				}
								
			} else { // not in the block
				boolean splitted = false;
				for(String splitter : splitters){				
					// forward check for splitter
					if(splitter.compareTo(str.substring(i, Math.min(i+splitter.length(), str.length())))==0){
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
						blockStartPos = i;
						break;
					}
				}
			}
		}
		if(curString.length()>0)
			splits.add(curString.trim());
		return splits.toArray(new String[]{});
		
	}
}

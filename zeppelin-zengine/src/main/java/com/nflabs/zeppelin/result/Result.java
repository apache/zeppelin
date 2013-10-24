package com.nflabs.zeppelin.result;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;


public class Result extends AbstractResult{
	List<Object []> rows;

	public Result(ResultSet res, int max) throws ResultDataException {
		super(res, max);
	}
	
	public Result(Exception e1) throws ResultDataException {
		super(e1);
	}

	@Override
	protected void process(ColumnDef[] columnDef, Object[] row, long n) {
		if(rows==null) rows = new LinkedList<Object [] >(); 
		rows.add(row);
	}
	
	public List<Object []> getRows(){
		if(rows==null) rows = new LinkedList<Object [] >(); 
		return rows;
	}
	
	public void write(OutputStream out) throws IOException{
		write(out, "\t", "\n");
	}
	public void write(OutputStream out, String columnSep, String rowSep) throws IOException{
		Iterator<Object[]> it = getRows().iterator();
		while(it.hasNext()){
			Object[] row = it.next();
			
			for(int i=0; i<row.length; i++){
				Object r = row[i];
				String strRepresentation; 
				if(r==null){
					strRepresentation = "null";
				} else {
					strRepresentation = r.toString();
				}
				
				if(i==0){
					out.write(strRepresentation.getBytes());
				} else {
					out.write((columnSep+strRepresentation).getBytes());
				}
				out.write(rowSep.getBytes());
			}
		}
	}
}

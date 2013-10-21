package com.nflabs.zeppelin.result;

import java.sql.ResultSet;
import java.util.LinkedList;
import java.util.List;


public class ResultDataObject extends ResultData{
	List<Object []> rows;
	public ResultDataObject(ResultSet res) throws ResultDataException {
		super(res);
	}
	
	public ResultDataObject(Exception e1) throws ResultDataException {
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
}

package com.nflabs.zeppelin.result;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.ResultSet;

public class ResultDataStream extends AbstractResult{
	public static final String DEFAULT_COLUMN_SEPARATOR="\t";
	public static final String DEFAULT_ROW_SEPARATOR="\n";
	private OutputStream out;
	private String columnSep;
	private String rowSep;
	public ResultDataStream(ResultSet res, OutputStream out) throws ResultDataException {
		this(res, -1, out, DEFAULT_COLUMN_SEPARATOR, DEFAULT_ROW_SEPARATOR);
	}
	
	public ResultDataStream(ResultSet res, OutputStream out, String columnSep, String rowSep) throws ResultDataException {
		this(res, -1, out, columnSep, rowSep);
	}
	
	
	public ResultDataStream(ResultSet res, int max, OutputStream out) throws ResultDataException {
		this(res, max, out, DEFAULT_COLUMN_SEPARATOR, DEFAULT_ROW_SEPARATOR);
	}
	public ResultDataStream(ResultSet res, int max, OutputStream out, String columnSep, String rowSep) throws ResultDataException {
		super(res, max);
		this.out = out;
		this.columnSep = columnSep;
		this.rowSep = rowSep;
	}

	@Override
	protected void process(ColumnDef[] columnDef, Object[] row, long n) throws IOException {
		if(n==0){
			// print column def
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
			}
			out.write(rowSep.getBytes());
		}
	}
}

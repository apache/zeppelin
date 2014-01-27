package com.nflabs.zeppelin.driver.exec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.log4j.Logger;

import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.result.ResultDataException;

public class ExecConnection implements ZeppelinConnection {
	Logger logger = Logger.getLogger(ExecConnection.class);
	
	public ExecConnection(){
		
	}
	
	@Override
	public boolean isConnected() throws ZeppelinDriverException {
		return true;
	}

	@Override
	public void close() throws ZeppelinDriverException {
	}

	@Override
	public void abort() throws ZeppelinDriverException {
	}

	@Override
	public Result query(String query) throws ZeppelinDriverException {
		logger.info("exec "+query);
		CommandLine cmdLine = CommandLine.parse("bash");
		cmdLine.addArgument("-c", false);
		cmdLine.addArgument(query, false);
		DefaultExecutor executor = new DefaultExecutor();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		executor.setStreamHandler(new PumpStreamHandler(outputStream));

		executor.setWatchdog(new ExecuteWatchdog(1000*60*30));
		int exitValue;
		try {
			exitValue = executor.execute(cmdLine);
		} catch (ExecuteException e1) {
			throw new ZeppelinDriverException(e1);
		} catch (IOException e1) {
			throw new ZeppelinDriverException(e1);
		}
		String [] msgs = null;
		if (outputStream!=null){
			String msgstr = outputStream.toString();
			if(msgstr!=null){
				msgs = msgstr.split("\n");
			}
		}
		Result resultData;
		try {
			resultData = new Result(exitValue, msgs);
		} catch (ResultDataException e) {
			throw new ZeppelinDriverException(e);
		}
		return resultData;
	}

	@Override
	public Result addResource(URI resourceLocation)
			throws ZeppelinDriverException {
		return null;
	}

	@Override
	public Result select(String tableName, int limit)
			throws ZeppelinDriverException {
		return null;
	}

	@Override
	public Result createViewFromQuery(String viewName, String query)
			throws ZeppelinDriverException {
		return null;
	}

	@Override
	public Result createTableFromQuery(String tableName, String query)
			throws ZeppelinDriverException {
		return null;
	}

	@Override
	public Result dropView(String viewName) throws ZeppelinDriverException {
		return null;
	}

	@Override
	public Result dropTable(String tableName) throws ZeppelinDriverException {
		return null;
	}

}

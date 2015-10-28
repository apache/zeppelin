package org.apache.zeppelin.interpreter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import javax.sql.rowset.RowSetProvider;
import javax.sql.rowset.CachedRowSet;
import java.sql.SQLException;


import org.apache.commons.codec.binary.StringUtils;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unlike a regular interpreter result, a JdbcInterpreter Result caches its 
 * output so that it can be used later and persisted independently of the regular result.
 * It also has a standard return for all tabular SQL data. 
 * @author Rusty Phillips {@literal: <rusty@cloudspace.com>}
 *
 */
public class JdbcInterpreterResult extends InterpreterResult {
  
  private ResultSet results;
  private String id;
  private String repoName;
  
  private InterpreterResult innerResult;
  
  private Logger logger() {
    
    Logger logger = LoggerFactory.getLogger(JdbcInterpreterResult.class);
    return logger;
  }

  
  public String getRepoName() {
    return repoName;
  }

  public void setRepoName(String repoName) {
    this.repoName = repoName;
  }
  
  public ResultSet getResults() {
    return results;
  }
  
  
  public JdbcInterpreterResult(Code code, String Id)
  {
    super(code);
    this.type = Type.TABLE;
    this.setId(Id);
  }
  
  public JdbcInterpreterResult(Code code, ResultSet resultSet) {
    this(code, resultSet, 
       UUID.randomUUID().toString());
  }
 
  public JdbcInterpreterResult(Code code, ResultSet resultSet, String Id) {
    super(code);
    try {

      this.type = Type.TABLE;
      // Not necessary at this time, but may be something to consider in the future.
      // the results in memory, it is not necessarily a better option than the previous one.
      /*
      CachedRowSet impl = RowSetProvider.newFactory().createCachedRowSet();
      logger().debug("Populating result set"); 
      impl.populate(resultSet); 
      */ 
      logger().debug("Finished populating result set.");
      this.results = resultSet;
      this.id = Id;
      message();
    } catch (Exception ex) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      this.code = code.ERROR;
      ex.printStackTrace(pw);
      logger().debug("Failed to populate result set.\n{}\n{}",
        ex.getMessage(), sw.toString());
      this.msg = ex.getMessage();
    }
  }

  
  @Override
  public String message() {
    if (this.msg != null)
    {
      return this.msg;
    }
    
    StringBuilder msg = new StringBuilder();
    if (code == code.ERROR) { return this.msg; }
    try {
      if (this.results == null)
      {
        this.code = code.ERROR;
        this.msg = "Unable to find any results table";
        return this.msg;
      }
      ResultSetMetaData md = this.results.getMetaData();
      for (int i = 1; i < md.getColumnCount() + 1; i++) {
        if (i == 1) {
          msg.append(md.getColumnName(i));
        } else {
          msg.append("\t" + md.getColumnName(i));
        }
      }
      msg.append("\n");
      while (this.results.next()) {
        for (int i = 1; i < md.getColumnCount() + 1; i++) {
          msg.append(results.getString(i) + "\t");
        }
        msg.append("\n");
      }
      this.msg = msg.toString();
    } catch (SQLException ex) { 
      code = code.ERROR;
      this.msg = ex.getMessage();
    }
    return this.msg;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}

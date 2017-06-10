package org.apache.zeppelin.realm;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.shiro.util.JdbcUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class JdbcRealm extends org.apache.shiro.realm.jdbc.JdbcRealm implements UserLookup {
    
  private static final Logger LOG = LoggerFactory.getLogger(JdbcRealm.class);


  @Override
  public Collection<String> lookupRoles(String query) {
    return Collections.EMPTY_SET;
  }

  /**
   * function to extract users from JDBCs
   */
  @Override
  public List<String> lookupUsers(String searchString) {
    List<String> userlist = new ArrayList<>();
    PreparedStatement ps = null;
    ResultSet rs = null;
    DataSource dataSource = null;
    String authQuery = "";
    String retval[];
    String tablename = "";
    String username = "";
    String userquery = "";
    try {
      dataSource = (DataSource) FieldUtils.readField(this, "dataSource", true);
      authQuery = (String) FieldUtils.readField(this, "DEFAULT_AUTHENTICATION_QUERY", true);
      LOG.info(authQuery);
      String authQueryLowerCase = authQuery.toLowerCase();
      retval = authQueryLowerCase.split("from", 2);
      if (retval.length >= 2) {
        retval = retval[1].split("with|where", 2);
        tablename = retval[0];
        retval = retval[1].split("where", 2);
        if (retval.length >= 2)
          retval = retval[1].split("=", 2);
        else
          retval = retval[0].split("=", 2);
        username = retval[0];
      }

      if (StringUtils.isBlank(username) || StringUtils.isBlank(tablename)) {
        return userlist;
      }

      userquery = "select " + username + " from " + tablename;

    } catch (IllegalAccessException e) {
      LOG.error("Error while accessing dataSource for JDBC Realm", e);
      return null;
    }

    try {
      Connection con = dataSource.getConnection();
      ps = con.prepareStatement(userquery);
      rs = ps.executeQuery();
      while (rs.next()) {
        userlist.add(rs.getString(1).trim());
      }
    } catch (Exception e) {
      LOG.error("Error retrieving User list from JDBC Realm", e);
    } finally {
      JdbcUtils.closeResultSet(rs);
      JdbcUtils.closeStatement(ps);
    }
    return userlist;
  }

}

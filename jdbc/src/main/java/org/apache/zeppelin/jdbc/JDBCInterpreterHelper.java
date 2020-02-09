package org.apache.zeppelin.jdbc;

import java.util.Properties;

public class JDBCInterpreterHelper {

  private static final String DRIVER_KEY = "driver";
  private static final String JDBC_PRESTO_DRIVER = "com.facebook.presto.jdbc.PrestoDriver";
  private static final String JDBC_HIVE_DRIVER = "org.apache.hive.jdbc.HiveDriver";

  static Properties getDriverProperties(String user, Properties properties) {
    Object driverClass = properties.get(DRIVER_KEY);
    if (isPrestoDriver(driverClass)) {
      return getPrestoProperties(user);
    }
    else if (isHiveDriver(driverClass)) {
      return getHiveProperties(user);
    }
    return properties;
  }

  static boolean isPrestoDriver(Object className) {
    return className != null ? className.toString().equalsIgnoreCase(JDBC_PRESTO_DRIVER) : false;
  }

  static boolean isHiveDriver(Object className) {
    return className != null ? className.toString().equalsIgnoreCase(JDBC_HIVE_DRIVER) : false;
  }

  /**
   * Compatible fix for the Presto driver.
   * Presto driver properties accept "user", "password"(optional).
   * Throws error if properties have "url", "driver".
   */
  static Properties getPrestoProperties(String user) {
    Properties properties = new Properties();
    properties.setProperty("user", user);
    return properties;
  }

  static Properties getHiveProperties(String user) {
    Properties properties = new Properties();
    properties.setProperty("user", user);
    return properties;
  }

}

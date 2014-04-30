package com.nflabs.zeppelin.driver.jdbc;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.regex.Pattern;

import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;

/**
 * JDBCDriver support generic JDBC connection for Zeppelin. JDBC driver class
 * and connection string will configured by createConnection(). Note that to use
 * specific JDBC driver class, class must be loaded from classloader before use.
 */
public class JDBCDriver extends ZeppelinDriver {

	@Override
	protected void init() {
	}

	@Override
	public boolean acceptsURL(String url) {
		return Pattern.matches("^jdbc:.*", url);
	}

	/**
	 * url is look like
	 * 
	 * jdbc:com.mysql.jdbc.Driver:mysql://localhost/mydb?user=user1&password=pass1
	 */
	@Override
	protected ZeppelinConnection createConnection(String url)
			throws ZeppelinDriverException {
		URI uri;

		try {
			uri = new URI(new URI(url).getSchemeSpecificPart());
		} catch (URISyntaxException e) {
			throw new ZeppelinDriverException(e);
		}
		String className = uri.getScheme();
		String connectionUrl = uri.getSchemeSpecificPart();

		try {
			Class.forName(className);
		} catch (ClassNotFoundException e) {
			throw new ZeppelinDriverException(e);
		}

		try {
			return new JDBCConnection(DriverManager.getConnection("jdbc:" + connectionUrl));
		} catch (SQLException e1) {
			throw new ZeppelinDriverException(e1);
		}
	}

}

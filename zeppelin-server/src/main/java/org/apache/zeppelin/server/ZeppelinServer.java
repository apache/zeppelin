package org.apache.zeppelin.server;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.zeppelin.conf.ZeppelinConfiguration;

public class ZeppelinServer {
	private static final Log LOG = LogFactory.getLog(ZeppelinServer.class);
	
	public static void main(String [] args) throws InterruptedException, ConfigurationException{

		ZeppelinConfiguration conf = ZeppelinConfiguration.create();
		String driverClassName = conf.getString(ZeppelinConfiguration.ConfVars.DRIVERCLASS);
		LOG.info("Driver class = "+driverClassName);
		
		Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
                LOG.info("Shutdown hook");
            }
        });

        while (true)
        {
            Thread.sleep(10000);
        }
	}
}

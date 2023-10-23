package org.apache.zeppelin.livy.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

public abstract class MiniClusterBase {

    private static final Logger LOG = LoggerFactory.getLogger(MiniClusterBase.class);

    public void main(String[] args) {
        String klass = getClass().getSimpleName();
        LOG.info("{}  is starting up.", klass);

        String configPath = args[0];

        File file = new File(configPath + "/cluster.conf");
        Map<String, String> props = MiniClusterUtils.loadProperties(file);
        MiniClusterConfig config = new MiniClusterConfig(props);

        start(config, configPath);
        LOG.info("{} running.", klass);
        while (true) synchronized (this) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected abstract void start(MiniClusterConfig config, String configPath);
}

package org.apache.zeppelin.livy.cluster;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.MiniDFSCluster;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public class MiniHdfsMain extends MiniClusterBase {

    public static MiniHdfsMain INSTANCE = new MiniHdfsMain();

    @Override
    protected void start(MiniClusterConfig config, String configPath) {
        try {
            Configuration hadoopConf = new Configuration();
            MiniDFSCluster hdfsCluster = new MiniDFSCluster.Builder(hadoopConf)
                    .numDataNodes(config.dnCount)
                    .format(true)
                    .waitSafeMode(true)
                    .build();

            hdfsCluster.waitActive();

            MiniClusterUtils.saveConfig(hadoopConf, new File(configPath + "/core-site.xml"));
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }
}

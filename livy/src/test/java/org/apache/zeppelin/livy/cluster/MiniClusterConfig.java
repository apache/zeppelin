package org.apache.zeppelin.livy.cluster;

import java.util.Map;
import java.util.Optional;

public class MiniClusterConfig {

    private final Map<String, String> config;
    public final int nmCount;
    public final int localDirCount;
    public final int logDirCount;
    public final int dnCount;

    public MiniClusterConfig(Map<String, String> config) {
        this.config = config;
        this.nmCount = getInt("yarn.nm-count", 1);
        this.localDirCount = getInt("yarn.local-dir-count", 1);
        this.logDirCount = getInt("yarn.log-dir-count", 1);
        this.dnCount = getInt("hdfs.dn-count", 1);
    }

    private int getInt(String key, int defaultVal) {
        return Optional.ofNullable(config.get(key)).map(Integer::parseInt).orElse(defaultVal);
    }
}

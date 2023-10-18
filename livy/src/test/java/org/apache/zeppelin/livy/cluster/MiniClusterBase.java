package org.apache.zeppelin.livy.cluster;

public abstract class MiniClusterBase {

    protected abstract void start(MiniClusterConfig config, String configPath);
}

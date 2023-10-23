package org.apache.zeppelin.livy.cluster;

import java.io.File;

public class ProcessInfo {

    public final Process process;

    public final File logFile;

    public ProcessInfo(Process process, File logFile) {
        this.process = process;
        this.logFile = logFile;
    }
}

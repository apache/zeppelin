package org.apache.zeppelin.livy.cluster;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.fs.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class MiniCluster implements LivyCluster {
    public static final Logger LOG = LoggerFactory.getLogger(MiniCluster.class);

    private final Map<String, String> config;

    private String _livyEndpoint;
    private Path _hdfsScrathDir;

    private File _tempDir = new File(System.getProperty("java.io.tmpdir") + "/livy-int-test");
    private File _sparkConfigDir;
    private File _configDir;

    private Optional<ProcessInfo> hdfs = Optional.empty();
    private Optional<ProcessInfo> yarn = Optional.empty();
    private Optional<ProcessInfo> livy = Optional.empty();

    public MiniCluster(Map<String, String> config) {
        this.config = config;
    }

    // Explicitly remove the "test-lib" dependency from the classpath of child processes. We
    // want tests to explicitly upload this jar when necessary, to test those code paths.
//    String[] cp = System.getProperty("java.class.path").split(File.pathSeparator);
//    String[] filtered = Arrays.stream(cp).filter(path -> !new File(path).getName().startsWith("livy-test-lib-")).;
//    assert(cp.size != filtered.size, "livy-test-lib jar not found in classpath!")
//    String childClasspath = filtered.mkString(File.pathSeparator)

    @Override
    public void deploy() {
        if (_tempDir.exists()) {
            FileUtils.deleteQuietly(_tempDir);
        }
        assertTrue(_tempDir.mkdir(), "Cannot create temp test dir.");
        _sparkConfigDir = mkdir("spark-conf", _tempDir);

        Map<String, String> sparkConf = new HashMap<>();
        sparkConf.put("spark.executor.instances", "1");
        sparkConf.put("spark.scheduler.minRegisteredResourcesRatio", "0.0");
        sparkConf.put("spark.ui.enabled", "false");
        sparkConf.put("spark.driver.memory", "512m");
        sparkConf.put("spark.executor.memory", "512m");
        sparkConf.put("spark.driver.extraJavaOptions", "-Dtest.appender=console");
        sparkConf.put("spark.executor.extraJavaOptions", "-Dtest.appender=console");

        MiniClusterUtils.saveProperties(sparkConf, new File(_sparkConfigDir, "spark-defaults.conf"));

        _configDir = mkdir("hadoop-conf", _tempDir);
        MiniClusterUtils.saveProperties(config, new File(_configDir, "cluster.conf"));
        hdfs = Optional.of(start(MiniHdfsMain.getClass(), new File(_configDir, "core-site.xml")));
        yarn = Optional.of(start(MiniYarnMain.getClass(), new File(_configDir, "yarn-site.xml")));
        runLivy();

        _hdfsScrathDir = fs.makeQualified(new Path("/"));
    }

    private File mkdir(String name, File parent) {
        File dir = new File(parent, name);
        if (!dir.exists()) {
            assertTrue(dir.mkdir(), "Failed to create directory " + name);
        }
        return dir;
    }


    @Override
    public void cleanUp() {
        Seq(hdfs, yarn, livy).flatten.foreach(stop)
        hdfs = Optional.empty();
        yarn = Optional.empty();
        livy = Optional.empty();
    }

    @Override
    public File configDir() {
        return _configDir;
    }

    @Override
    public void runLivy() {
        assertFalse(livy.isPresent());
        File confFile = new File(_configDir, "serverUrl.conf");

        ProcessInfo localLivy = start(MiniLivyMain.getClass(), confFile);

        Map<String, String> props = MiniClusterUtils.loadProperties(confFile);
        _livyEndpoint = config.getOrDefault("livyEndpoint", props.get("livy.server.server-url"));

        // Wait until Livy server responds.
//        val httpClient = new DefaultAsyncHttpClient()
//        eventually(timeout(30 seconds), interval(1 second)) {
//            val res = httpClient.prepareGet(_livyEndpoint + "/metrics").execute().get()
//            assert(res.getStatusCode() == HttpServletResponse.SC_OK)
//        }

        livy = Optional.of(localLivy);
    }

    @Override
    public void stopLivy() {
        assertTrue(livy.isPresent());
        livy.foreach(stop)
        _livyEndpoint = null;
        livy = Optional.empty();
    }

    @Override
    public String livyEndpoint() {
        return _livyEndpoint;
    }

    @Override
    public Path hdfsScratchDir() {
        return _hdfsScrathDir;
    }

    private ProcessInfo start(
            Class klass,
            File configFile) {

        String simpleName = StringUtils.stripEnd(klass.getSimpleName(), "$");
        File procDir = mkdir(simpleName, _tempDir);
        File procTmp = mkdir("tmp", procDir);

        // Before starting anything, clean up previous running sessions.
        sys.process.Process(s"pkill -f $simpleName") !

                val cmd =
                Seq(
                        System.getProperty("java.home") + "/bin/java",
                        "-Dtest.appender=console",
                        "-Djava.io.tmpdir=" + procTmp.getAbsolutePath(),
                        "-cp", childClasspath + File.pathSeparator + _configDir.getAbsolutePath())++
        Seq(
                StringUtils.stripEnd(klass.getName(), "$"),
                _configDir.getAbsolutePath());

        File logFile = new File(procDir, "output.log");
        ProcessBuilder pb = new ProcessBuilder(cmd.toArray:_ *)
            .directory(procDir)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))

        pb.environment().put("LIVY_CONF_DIR", _configDir.getAbsolutePath())
        pb.environment().put("HADOOP_CONF_DIR", _configDir.getAbsolutePath())
        pb.environment().put("SPARK_CONF_DIR", _sparkConfigDir.getAbsolutePath())
        pb.environment().put("SPARK_LOCAL_IP", "127.0.0.1")

        Process child = pb.start();

        // Wait for the config file to show up before returning, so that dependent services
        // can see the configuration. Exit early if process dies.
        eventually(timeout(30seconds), interval(100 millis)) {
            assert (configFile.isFile(),s "$simpleName hasn't started yet.")

            try {
                int exitCode = child.exitValue();
                throw new IOException("Child process exited unexpectedly (exit code " + exitCode + ")");
            } catch (IllegalThreadStateException its) {
                // Try again.
            }
        }

        return new ProcessInfo(child, logFile);
    }

    private void stop(ProcessInfo svc) {
        svc.process.destroy();
        svc.process.waitFor();
    }
}

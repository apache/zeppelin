package com.nflabs.zeppelin.util;
import static org.apache.hadoop.hive.metastore.MetaStoreUtils.DEFAULT_DATABASE_NAME;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.zookeeper.MiniZooKeeperCluster;
import org.apache.hadoop.hive.cli.CliDriver;
import org.apache.hadoop.hive.cli.CliSessionState;
import org.apache.hadoop.hive.common.io.CachingPrintStream;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.MetaStoreUtils;
import org.apache.hadoop.hive.metastore.api.Index;
import org.apache.hadoop.hive.ql.CommandNeedRetryException;
import org.apache.hadoop.hive.ql.Context;
import org.apache.hadoop.hive.ql.Driver;
import org.apache.hadoop.hive.ql.exec.FunctionRegistry;
import org.apache.hadoop.hive.ql.exec.Task;
import org.apache.hadoop.hive.ql.exec.Utilities;
import org.apache.hadoop.hive.ql.exec.Utilities.StreamPrinter;
import org.apache.hadoop.hive.ql.io.IgnoreKeyTextOutputFormat;
import org.apache.hadoop.hive.ql.lockmgr.zookeeper.ZooKeeperHiveLockManager;
import org.apache.hadoop.hive.ql.metadata.Hive;
import org.apache.hadoop.hive.ql.metadata.Table;
import org.apache.hadoop.hive.ql.parse.ASTNode;
import org.apache.hadoop.hive.ql.parse.ParseDriver;
import org.apache.hadoop.hive.ql.parse.ParseException;
import org.apache.hadoop.hive.ql.parse.SemanticAnalyzer;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.hive.ql.session.SessionState;
import org.apache.hadoop.hive.serde.Constants;
import org.apache.hadoop.hive.serde2.thrift.ThriftDeserializer;
import org.apache.hadoop.hive.serde2.thrift.test.Complex;
import org.apache.hadoop.hive.shims.HadoopShims;
import org.apache.hadoop.hive.shims.ShimLoader;
import org.apache.hadoop.mapred.MiniMRCluster;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;
import org.apache.hadoop.mapred.TextInputFormat;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.zookeeper.ZooKeeper;

/**
 * QTestUtil.
 *
 */
public class MiniHiveCluster {

  private static final Log LOG = LogFactory.getLog("QTestUtil");

  private String testWarehouse;
  private final String testFiles;
  protected final String outDir;
  protected final String logDir;
  private final TreeMap<String, String> qMap;
  private final Set<String> qSkipSet;
  public static final HashSet<String> srcTables = new HashSet<String>
    (Arrays.asList(new String [] {
        "src", "src1", "srcbucket", "srcbucket2", "src_json", "src_thrift",
        "src_sequencefile", "srcpart"
      }));

  private ParseDriver pd;
  private Hive db;
  protected HiveConf conf;
  private Driver drv;
  private SemanticAnalyzer sem;
  private FileSystem fs;
  protected final boolean overWrite;
  private CliDriver cliDriver;
  private MiniMRCluster mr = null;
  private HadoopShims.MiniDFSShim dfs = null;
  private boolean miniMr = false;
  private String hadoopVer = null;
  private QTestSetup setup = null;

  public boolean deleteDirectory(File path) {
    if (path.exists()) {
      File[] files = path.listFiles();
      for (File file : files) {
        if (file.isDirectory()) {
          deleteDirectory(file);
        } else {
          file.delete();
        }
      }
    }
    return (path.delete());
  }

  public void copyDirectoryToLocal(Path src, Path dest) throws Exception {

    FileSystem srcFs = src.getFileSystem(conf);
    FileSystem destFs = dest.getFileSystem(conf);
    if (srcFs.exists(src)) {
      FileStatus[] files = srcFs.listStatus(src);
      for (FileStatus file : files) {
        String name = file.getPath().getName();
        Path dfs_path = file.getPath();
        Path local_path = new Path(dest, name);

        // If this is a source table we do not copy it out
        if (srcTables.contains(name)) {
          continue;
        }

        if (file.isDir()) {
          if (!destFs.exists(local_path)) {
            destFs.mkdirs(local_path);
          }
          copyDirectoryToLocal(dfs_path, local_path);
        } else {
          srcFs.copyToLocalFile(dfs_path, local_path);
        }
      }
    }
  }

  static Pattern mapTok = Pattern.compile("(\\.?)(.*)_map_(.*)");
  static Pattern reduceTok = Pattern.compile("(.*)(reduce_[^\\.]*)((\\..*)?)");

  public void normalizeNames(File path) throws Exception {
    if (path.isDirectory()) {
      File[] files = path.listFiles();
      for (File file : files) {
        normalizeNames(file);
      }
    } else {
      // System.out.println("Trying to match: " + path.getPath());
      Matcher m = reduceTok.matcher(path.getName());
      if (m.matches()) {
        String name = m.group(1) + "reduce" + m.group(3);
        // System.out.println("Matched new name: " + name);
        path.renameTo(new File(path.getParent(), name));
      } else {
        m = mapTok.matcher(path.getName());
        if (m.matches()) {
          String name = m.group(1) + "map_" + m.group(3);
          // System.out.println("Matched new name: " + name);
          path.renameTo(new File(path.getParent(), name));
        }
      }
    }
  }

  public MiniHiveCluster(String outDir, String logDir) throws Exception {
    this(outDir, logDir, false, "0.20");
  }

  private String getHadoopMainVersion(String input) {
    if (input == null) {
      return null;
    }
    Pattern p = Pattern.compile("^(\\d+\\.\\d+).*");
    Matcher m = p.matcher(input);
    if (m.matches()) {
      return m.group(1);
    }
    return null;
  }

  public void initConf() throws Exception {
    if (miniMr) {
      assert dfs != null;
      assert mr != null;
      // set fs.default.name to the uri of mini-dfs
      conf.setVar(HiveConf.ConfVars.HADOOPFS, dfs.getFileSystem().getUri().toString());
      // hive.metastore.warehouse.dir needs to be set relative to the mini-dfs
      conf.setVar(HiveConf.ConfVars.METASTOREWAREHOUSE,
                  (new Path(dfs.getFileSystem().getUri().toString(),
                            "/build/ql/test/data/warehouse/")).toString());
      int port = 0;

      try {
        // Hadoop20 MiniMRCluster will return a proper port.
        // Hadoop23 MiniMRCluster does not implement this method so use the default RM port.
        port = mr.getJobTrackerPort();
      } catch (UnsupportedOperationException e) {
        String address =
            StringUtils.substringAfterLast(conf.get("yarn.resourcemanager.address"), ":");

        if (StringUtils.isBlank(address)) {
          throw new IllegalArgumentException("Invalid YARN resource manager port.");
        }

        port = Integer.parseInt(address);
      }

      
      //ShimLoader.getHadoopShims().setJobLauncherRpcAddress(conf, "localhost:" + port);
    }
  }

  public MiniHiveCluster(String outDir, String logDir, boolean miniMr, String hadoopVer)
    throws Exception {
    this.outDir = outDir;
    this.logDir = logDir;
    conf = new HiveConf(Driver.class);
    this.miniMr = miniMr;
    this.hadoopVer = getHadoopMainVersion(hadoopVer);
    qMap = new TreeMap<String, String>();
    qSkipSet = new HashSet<String>();

    if (miniMr) {
      dfs = ShimLoader.getHadoopShims().getMiniDfs(conf, 4, true, null);
      FileSystem fs = dfs.getFileSystem();
      mr = new MiniMRCluster(4, fs.getUri().toString(), 1);
    }

    initConf();

    // Use the current directory if it is not specified
    String dataDir = conf.get("test.data.files");
    if (dataDir == null) {
      dataDir = new File(".").getAbsolutePath() + "/data/files";
    }

    testFiles = dataDir.replace('\\', '/')
        .replace("c:", "");

    String ow = System.getProperty("test.output.overwrite");
    if ((ow != null) && ow.equalsIgnoreCase("true")) {
      overWrite = true;
    } else {
      overWrite = false;
    }

    setup = new QTestSetup();
    setup.preTest(conf);
    init();
  }

  public void shutdown() throws Exception {
    cleanUp();
    setup.tearDown();
    if (dfs != null) {
      dfs.shutdown();
      dfs = null;
    }

    if (mr != null) {
      mr.shutdown();
      mr = null;
    }
  }

  public void addFile(String qFile) throws Exception {

    File qf = new File(qFile);
    addFile(qf);
  }

  public void addFile(File qf) throws Exception {

    FileInputStream fis = new FileInputStream(qf);
    BufferedInputStream bis = new BufferedInputStream(fis);
    DataInputStream dis = new DataInputStream(bis);
    StringBuilder qsb = new StringBuilder();

    // Look for a hint to not run a test on some Hadoop versions
    Pattern pattern = Pattern.compile("-- (EX|IN)CLUDE_HADOOP_MAJOR_VERSIONS\\((.*)\\)");


    // Read the entire query
    boolean excludeQuery = false;
    boolean includeQuery = false;
    Set<String> versionSet = new HashSet<String>();
    String hadoopVer = ShimLoader.getMajorVersion();
    while (dis.available() != 0) {
      String line = dis.readLine();

      // Each qfile may include at most one INCLUDE or EXCLUDE directive.
      //
      // If a qfile contains an INCLUDE directive, and hadoopVer does
      // not appear in the list of versions to include, then the qfile
      // is skipped.
      //
      // If a qfile contains an EXCLUDE directive, and hadoopVer is
      // listed in the list of versions to EXCLUDE, then the qfile is
      // skipped.
      //
      // Otherwise, the qfile is included.
      Matcher matcher = pattern.matcher(line);
      if (matcher.find()) {
        if (excludeQuery || includeQuery) {
          String message = "QTestUtil: qfile " + qf.getName()
            + " contains more than one reference to (EX|IN)CLUDE_HADOOP_MAJOR_VERSIONS";
          throw new UnsupportedOperationException(message);
        }

        String prefix = matcher.group(1);
        if ("EX".equals(prefix)) {
          excludeQuery = true;
        } else {
          includeQuery = true;
        }

        String versions = matcher.group(2);
        for (String s : versions.split("\\,")) {
          s = s.trim();
          versionSet.add(s);
        }
      }
      qsb.append(line + "\n");
    }
    qMap.put(qf.getName(), qsb.toString());

    if (excludeQuery && versionSet.contains(hadoopVer)) {
      System.out.println("QTestUtil: " + qf.getName()
        + " EXCLUDE list contains Hadoop Version " + hadoopVer + ". Skipping...");
      qSkipSet.add(qf.getName());
    } else if (includeQuery && !versionSet.contains(hadoopVer)) {
      System.out.println("QTestUtil: " + qf.getName()
        + " INCLUDE list does not contain Hadoop Version " + hadoopVer + ". Skipping...");
      qSkipSet.add(qf.getName());
    }
    dis.close();
  }

  /**
   * Clear out any side effects of running tests
   */
  public void clearPostTestEffects () throws Exception {
    setup.postTest(conf);
  }

  /**
   * Clear out any side effects of running tests
   */
  public void clearTestSideEffects () throws Exception {
    // Delete any tables other than the source tables
    // and any databases other than the default database.
    for (String dbName : db.getAllDatabases()) {
      db.setCurrentDatabase(dbName);
      for (String tblName : db.getAllTables()) {
        if (!DEFAULT_DATABASE_NAME.equals(dbName) || !srcTables.contains(tblName)) {
          Table tblObj = db.getTable(tblName);
          // dropping index table can not be dropped directly. Dropping the base
          // table will automatically drop all its index table
          if(tblObj.isIndexTable()) {
            continue;
          }
          db.dropTable(dbName, tblName);
        } else {
          // this table is defined in srcTables, drop all indexes on it
         List<Index> indexes = db.getIndexes(dbName, tblName, (short)-1);
          if (indexes != null && indexes.size() > 0) {
            for (Index index : indexes) {
              db.dropIndex(dbName, tblName, index.getIndexName(), true);
            }
          }
        }
      }
      if (!DEFAULT_DATABASE_NAME.equals(dbName)) {
        db.dropDatabase(dbName);
      }
    }
    Hive.get().setCurrentDatabase(DEFAULT_DATABASE_NAME);

    List<String> roleNames = db.getAllRoleNames();
      for (String roleName : roleNames) {
        db.dropRole(roleName);
    }
    // allocate and initialize a new conf since a test can
    // modify conf by using 'set' commands
    conf = new HiveConf (Driver.class);
    initConf();
    setup.preTest(conf);
  }

  public void cleanUp() throws Exception {
    // Drop any tables that remain due to unsuccessful runs
    for (String s : new String[] {"src", "src1", "src_json", "src_thrift",
        "src_sequencefile", "srcpart", "srcbucket", "srcbucket2", "dest1",
        "dest2", "dest3", "dest4", "dest4_sequencefile", "dest_j1", "dest_j2",
        "dest_g1", "dest_g2", "fetchtask_ioexception"}) {
      db.dropTable(MetaStoreUtils.DEFAULT_DATABASE_NAME, s);
    }

    // delete any contents in the warehouse dir
    Path p = new Path(testWarehouse);
    FileSystem fs = p.getFileSystem(conf);

    try {
      FileStatus [] ls = fs.listStatus(p);
      for (int i=0; (ls != null) && (i<ls.length); i++) {
        fs.delete(ls[i].getPath(), true);
      }
    } catch (FileNotFoundException e) {
      // Best effort
    }

    FunctionRegistry.unregisterTemporaryUDF("test_udaf");
    FunctionRegistry.unregisterTemporaryUDF("test_error");
  }

  private void runLoadCmd(String loadCmd) throws Exception {
    int ecode = 0;
    ecode = drv.run(loadCmd).getResponseCode();
    drv.close();
    if (ecode != 0) {
      throw new Exception("load command: " + loadCmd
          + " failed with exit code= " + ecode);
    }
    return;
  }

  private void runCreateTableCmd(String createTableCmd) throws Exception {
    int ecode = 0;
    ecode = drv.run(createTableCmd).getResponseCode();
    if (ecode != 0) {
      throw new Exception("create table command: " + createTableCmd
          + " failed with exit code= " + ecode);
    }

    return;
  }

  public void createSources() throws Exception {

    startSessionState();
    conf.setBoolean("hive.test.init.phase", true);

    // Create a bunch of tables with columns key and value
    LinkedList<String> cols = new LinkedList<String>();
    cols.add("key");
    cols.add("value");

    LinkedList<String> part_cols = new LinkedList<String>();
    part_cols.add("ds");
    part_cols.add("hr");
    db.createTable("srcpart", cols, part_cols, TextInputFormat.class,
        IgnoreKeyTextOutputFormat.class);

    Path fpath;
    HashMap<String, String> part_spec = new HashMap<String, String>();
    for (String ds : new String[] {"2008-04-08", "2008-04-09"}) {
      for (String hr : new String[] {"11", "12"}) {
        part_spec.clear();
        part_spec.put("ds", ds);
        part_spec.put("hr", hr);
        // System.out.println("Loading partition with spec: " + part_spec);
        // db.createPartition(srcpart, part_spec);
        fpath = new Path(testFiles, "kv1.txt");
        // db.loadPartition(fpath, srcpart.getName(), part_spec, true);
        runLoadCmd("LOAD DATA LOCAL INPATH '" + fpath.toString()
            + "' OVERWRITE INTO TABLE srcpart PARTITION (ds='" + ds + "',hr='"
            + hr + "')");
      }
    }
    ArrayList<String> bucketCols = new ArrayList<String>();
    bucketCols.add("key");
    runCreateTableCmd("CREATE TABLE srcbucket(key int, value string) CLUSTERED BY (key) INTO 2 BUCKETS STORED AS TEXTFILE");
    // db.createTable("srcbucket", cols, null, TextInputFormat.class,
    // IgnoreKeyTextOutputFormat.class, 2, bucketCols);
    for (String fname : new String[] {"srcbucket0.txt", "srcbucket1.txt"}) {
      fpath = new Path(testFiles, fname);
      runLoadCmd("LOAD DATA LOCAL INPATH '" + fpath.toString()
          + "' INTO TABLE srcbucket");
    }

    runCreateTableCmd("CREATE TABLE srcbucket2(key int, value string) "
        + "CLUSTERED BY (key) INTO 4 BUCKETS STORED AS TEXTFILE");
    // db.createTable("srcbucket", cols, null, TextInputFormat.class,
    // IgnoreKeyTextOutputFormat.class, 2, bucketCols);
    for (String fname : new String[] {"srcbucket20.txt", "srcbucket21.txt",
        "srcbucket22.txt", "srcbucket23.txt"}) {
      fpath = new Path(testFiles, fname);
      runLoadCmd("LOAD DATA LOCAL INPATH '" + fpath.toString()
          + "' INTO TABLE srcbucket2");
    }

    for (String tname : new String[] {"src", "src1"}) {
      db.createTable(tname, cols, null, TextInputFormat.class,
          IgnoreKeyTextOutputFormat.class);
    }
    db.createTable("src_sequencefile", cols, null,
        SequenceFileInputFormat.class, SequenceFileOutputFormat.class);

    Table srcThrift = new Table(db.getCurrentDatabase(), "src_thrift");
    srcThrift.setInputFormatClass(SequenceFileInputFormat.class.getName());
    srcThrift.setOutputFormatClass(SequenceFileOutputFormat.class.getName());
    srcThrift.setSerializationLib(ThriftDeserializer.class.getName());
    srcThrift.setSerdeParam(Constants.SERIALIZATION_CLASS, Complex.class
        .getName());
    srcThrift.setSerdeParam(Constants.SERIALIZATION_FORMAT,
        TBinaryProtocol.class.getName());
    db.createTable(srcThrift);

    LinkedList<String> json_cols = new LinkedList<String>();
    json_cols.add("json");
    db.createTable("src_json", json_cols, null, TextInputFormat.class,
        IgnoreKeyTextOutputFormat.class);

    // load the input data into the src table
    fpath = new Path(testFiles, "kv1.txt");
    runLoadCmd("LOAD DATA LOCAL INPATH '" + fpath.toString() + "' INTO TABLE src");

    // load the input data into the src table
    fpath = new Path(testFiles, "kv3.txt");
    runLoadCmd("LOAD DATA LOCAL INPATH '" + fpath.toString() + "' INTO TABLE src1");

    // load the input data into the src_sequencefile table
    fpath = new Path(testFiles, "kv1.seq");
    runLoadCmd("LOAD DATA LOCAL INPATH '" + fpath.toString()
        + "' INTO TABLE src_sequencefile");

    // load the input data into the src_thrift table
    fpath = new Path(testFiles, "complex.seq");
    runLoadCmd("LOAD DATA LOCAL INPATH '" + fpath.toString()
        + "' INTO TABLE src_thrift");

    // load the json data into the src_json table
    fpath = new Path(testFiles, "json.txt");
    runLoadCmd("LOAD DATA LOCAL INPATH '" + fpath.toString()
        + "' INTO TABLE src_json");

    conf.setBoolean("hive.test.init.phase", false);
  }

  public void init() throws Exception {
    // System.out.println(conf.toString());
    testWarehouse = conf.getVar(HiveConf.ConfVars.METASTOREWAREHOUSE);
    // conf.logVars(System.out);
    // System.out.flush();

    SessionState.start(conf);
    db = Hive.get(conf);
    fs = FileSystem.get(conf);
    drv = new Driver(conf);
    drv.init();
    pd = new ParseDriver();
    sem = new SemanticAnalyzer(conf);
  }

  public void init(String tname) throws Exception {
    cleanUp();
    createSources();

    LinkedList<String> cols = new LinkedList<String>();
    cols.add("key");
    cols.add("value");

    LinkedList<String> part_cols = new LinkedList<String>();
    part_cols.add("ds");
    part_cols.add("hr");

    db.createTable("dest1", cols, null, TextInputFormat.class,
        IgnoreKeyTextOutputFormat.class);
    db.createTable("dest2", cols, null, TextInputFormat.class,
        IgnoreKeyTextOutputFormat.class);

    db.createTable("dest3", cols, part_cols, TextInputFormat.class,
        IgnoreKeyTextOutputFormat.class);
    Table dest3 = db.getTable("dest3");

    HashMap<String, String> part_spec = new HashMap<String, String>();
    part_spec.put("ds", "2008-04-08");
    part_spec.put("hr", "12");
    db.createPartition(dest3, part_spec);

    db.createTable("dest4", cols, null, TextInputFormat.class,
        IgnoreKeyTextOutputFormat.class);
    db.createTable("dest4_sequencefile", cols, null,
        SequenceFileInputFormat.class, SequenceFileOutputFormat.class);
  }

  public void cliInit(String tname) throws Exception {
    cliInit(tname, true);
  }

  public void cliInit(String tname, boolean recreate) throws Exception {
    if (recreate) {
      cleanUp();
      createSources();
    }

    HiveConf.setVar(conf, HiveConf.ConfVars.HIVE_AUTHENTICATOR_MANAGER,
    "org.apache.hadoop.hive.ql.security.DummyAuthenticator");
    CliSessionState ss = new CliSessionState(conf);
    assert ss != null;
    ss.in = System.in;

    File qf = new File(outDir, tname);
    File outf = null;
    outf = new File(logDir);
    outf = new File(outf, qf.getName().concat(".out"));
    FileOutputStream fo = new FileOutputStream(outf);
    ss.out = new PrintStream(fo, true, "UTF-8");
    ss.err = new CachingPrintStream(fo, true, "UTF-8");
    ss.setIsSilent(true);
    SessionState oldSs = SessionState.get();
    if (oldSs != null && oldSs.out != null && oldSs.out != System.out) {
      oldSs.out.close();
    }
    SessionState.start(ss);

    cliDriver = new CliDriver();
    if (tname.equals("init_file.q")) {
      ss.initFiles.add("../data/scripts/test_init_file.sql");
    }
    cliDriver.processInitFiles(ss);
  }

  private CliSessionState startSessionState()
      throws FileNotFoundException, UnsupportedEncodingException {

    HiveConf.setVar(conf, HiveConf.ConfVars.HIVE_AUTHENTICATOR_MANAGER,
        "org.apache.hadoop.hive.ql.security.DummyAuthenticator");

    CliSessionState ss = new CliSessionState(conf);
    assert ss != null;

    SessionState.start(ss);
    return ss;
  }

  public int executeOne(String tname) {
    String q = qMap.get(tname);

    if (q.indexOf(";") == -1) {
      return -1;
    }

    String q1 = q.substring(0, q.indexOf(";") + 1);
    String qrest = q.substring(q.indexOf(";") + 1);
    qMap.put(tname, qrest);

    System.out.println("Executing " + q1);
    return cliDriver.processLine(q1);
  }

  public int execute(String tname) {
    try {
      return drv.run(qMap.get(tname)).getResponseCode();
    } catch (CommandNeedRetryException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return -1;
    }
  }

  public int executeClient(String tname) {
    return cliDriver.processLine(qMap.get(tname));
  }

  public boolean shouldBeSkipped(String tname) {
    return qSkipSet.contains(tname);
  }

  public void convertSequenceFileToTextFile() throws Exception {
    // Create an instance of hive in order to create the tables
    testWarehouse = conf.getVar(HiveConf.ConfVars.METASTOREWAREHOUSE);
    db = Hive.get(conf);
    // Create dest4 to replace dest4_sequencefile
    LinkedList<String> cols = new LinkedList<String>();
    cols.add("key");
    cols.add("value");

    // Move all data from dest4_sequencefile to dest4
    drv
        .run("FROM dest4_sequencefile INSERT OVERWRITE TABLE dest4 SELECT dest4_sequencefile.*");

    // Drop dest4_sequencefile
    db.dropTable(MetaStoreUtils.DEFAULT_DATABASE_NAME, "dest4_sequencefile",
        true, true);
  }

  public int checkNegativeResults(String tname, Exception e) throws Exception {

    File qf = new File(outDir, tname);
    String expf = outPath(outDir.toString(), tname.concat(".out"));

    File outf = null;
    outf = new File(logDir);
    outf = new File(outf, qf.getName().concat(".out"));

    FileWriter outfd = new FileWriter(outf);
    if (e instanceof ParseException) {
      outfd.write("Parse Error: ");
    } else if (e instanceof SemanticException) {
      outfd.write("Semantic Exception: \n");
    } else {
      throw e;
    }

    outfd.write(e.getMessage());
    outfd.close();

    String cmdLine = "diff " + outf.getPath() + " " + expf;
    System.out.println(cmdLine);

    Process executor = Runtime.getRuntime().exec(cmdLine);

    StreamPrinter outPrinter = new StreamPrinter(
        executor.getInputStream(), null, SessionState.getConsole().getChildOutStream());
    StreamPrinter errPrinter = new StreamPrinter(
        executor.getErrorStream(), null, SessionState.getConsole().getChildErrStream());

    outPrinter.start();
    errPrinter.start();

    int exitVal = executor.waitFor();

    if (exitVal != 0 && overWrite) {
      System.out.println("Overwriting results");
      cmdLine = "cp " + outf.getPath() + " " + expf;
      executor = Runtime.getRuntime().exec(cmdLine);
      exitVal = executor.waitFor();
    }

    return exitVal;
  }

  public int checkParseResults(String tname, ASTNode tree) throws Exception {

    if (tree != null) {
      File parseDir = new File(outDir, "parse");
      String expf = outPath(parseDir.toString(), tname.concat(".out"));

      File outf = null;
      outf = new File(logDir);
      outf = new File(outf, tname.concat(".out"));

      FileWriter outfd = new FileWriter(outf);
      outfd.write(tree.toStringTree());
      outfd.close();

      String cmdLine = "diff " + outf.getPath() + " " + expf;
      System.out.println(cmdLine);

      Process executor = Runtime.getRuntime().exec(cmdLine);

      StreamPrinter outPrinter = new StreamPrinter(
          executor.getInputStream(), null, SessionState.getConsole().getChildOutStream());
      StreamPrinter errPrinter = new StreamPrinter(
          executor.getErrorStream(), null, SessionState.getConsole().getChildErrStream());

      outPrinter.start();
      errPrinter.start();

      int exitVal = executor.waitFor();

      if (exitVal != 0 && overWrite) {
        System.out.println("Overwriting results");
        cmdLine = "cp " + outf.getPath() + " " + expf;
        executor = Runtime.getRuntime().exec(cmdLine);
        exitVal = executor.waitFor();
      }

      return exitVal;
    } else {
      throw new Exception("Parse tree is null");
    }
  }

  public int checkPlan(String tname, List<Task<? extends Serializable>> tasks) throws Exception {

    if (tasks != null) {
      File planDir = new File(outDir, "plan");
      String planFile = outPath(planDir.toString(), tname + ".xml");

      File outf = null;
      outf = new File(logDir);
      outf = new File(outf, tname.concat(".xml"));

      FileOutputStream ofs = new FileOutputStream(outf);
      for (Task<? extends Serializable> plan : tasks) {
        Utilities.serializeTasks(plan, ofs);
      }

      String[] patterns = new String[] {
          "<java version=\".*\" class=\"java.beans.XMLDecoder\">",
          "<string>.*/tmp/.*</string>",
          "<string>file:.*</string>",
          "<string>pfile:.*</string>",
          "<string>[0-9]{10}</string>",
          "<string>/.*/warehouse/.*</string>"
      };
      maskPatterns(patterns, outf.getPath());

      String[] cmdArray = new String[] {
          "diff",
          "-b",
          outf.getPath(),
          planFile
      };
      System.out.println(org.apache.commons.lang.StringUtils.join(cmdArray, ' '));

      Process executor = Runtime.getRuntime().exec(cmdArray);

      StreamPrinter outPrinter = new StreamPrinter(
          executor.getInputStream(), null, SessionState.getConsole().getChildOutStream());
      StreamPrinter errPrinter = new StreamPrinter(
          executor.getErrorStream(), null, SessionState.getConsole().getChildErrStream());

      outPrinter.start();
      errPrinter.start();

      int exitVal = executor.waitFor();

      if (exitVal != 0 && overWrite) {
        System.out.println("Overwriting results");
        String cmdLine = "cp " + outf.getPath() + " " + planFile;
        executor = Runtime.getRuntime().exec(cmdLine);
        exitVal = executor.waitFor();
      }

      return exitVal;
    } else {
      throw new Exception("Plan is null");
    }

  }

  /**
   * Given the current configurations (e.g., hadoop version and execution mode), return
   * the correct file name to compare with the current test run output.
   * @param outDir The directory where the reference log files are stored.
   * @param testName The test file name (terminated by ".out").
   * @return The file name appended with the configuration values if it exists.
   */
  public String outPath(String outDir, String testName) {
    String ret = (new File(outDir, testName)).getPath();
    // List of configurations. Currently the list consists of hadoop version and execution mode only
    List<String> configs = new ArrayList<String>();
    configs.add(this.hadoopVer);

    Deque<String> stack = new LinkedList<String>();
    StringBuilder sb = new StringBuilder();
    sb.append(testName);
    stack.push(sb.toString());

    // example file names are input1.q.out_0.20.0_minimr or input2.q.out_0.17
    for (String s: configs) {
      sb.append('_');
      sb.append(s);
      stack.push(sb.toString());
    }
    while (stack.size() > 0) {
      String fileName = stack.pop();
      File f = new File(outDir, fileName);
      if (f.exists()) {
        ret = f.getPath();
        break;
      }
    }
   return ret;
  }

  private void maskPatterns(String[] patterns, String fname) throws Exception {
    String maskPattern = "#### A masked pattern was here ####";

    String line;
    BufferedReader in;
    BufferedWriter out;

    in = new BufferedReader(new FileReader(fname));
    out = new BufferedWriter(new FileWriter(fname + ".orig"));
    while (null != (line = in.readLine())) {
      out.write(line);
      out.write('\n');
    }
    in.close();
    out.close();

    in = new BufferedReader(new FileReader(fname + ".orig"));
    out = new BufferedWriter(new FileWriter(fname));

    boolean lastWasMasked = false;
    while (null != (line = in.readLine())) {
      for (String pattern : patterns) {
        line = line.replaceAll(pattern, maskPattern);
      }

      if (line.equals(maskPattern)) {
        // We're folding multiple masked lines into one.
        if (!lastWasMasked) {
          out.write(line);
          out.write("\n");
          lastWasMasked = true;
        }
      } else {
        out.write(line);
        out.write("\n");
        lastWasMasked = false;
      }
    }

    in.close();
    out.close();
  }

  public int checkCliDriverResults(String tname) throws Exception {
    String[] cmdArray;
    String[] patterns;
    assert(qMap.containsKey(tname));

    String outFileName = outPath(outDir, tname + ".out");

    patterns = new String[] {
        ".*file:.*",
        ".*pfile:.*",
        ".*hdfs:.*",
        ".*/tmp/.*",
        ".*invalidscheme:.*",
        ".*lastUpdateTime.*",
        ".*lastAccessTime.*",
        ".*lastModifiedTime.*",
        ".*[Oo]wner.*",
        ".*CreateTime.*",
        ".*LastAccessTime.*",
        ".*Location.*",
        ".*LOCATION '.*",
        ".*transient_lastDdlTime.*",
        ".*last_modified_.*",
        ".*java.lang.RuntimeException.*",
        ".*at org.*",
        ".*at sun.*",
        ".*at java.*",
        ".*at junit.*",
        ".*Caused by:.*",
        ".*LOCK_QUERYID:.*",
        ".*LOCK_TIME:.*",
        ".*grantTime.*",
        ".*[.][.][.] [0-9]* more.*",
        ".*job_[0-9]*_[0-9]*.*",
        ".*USING 'java -cp.*",
        "^Deleted.*",
    };

    File f = new File(logDir, tname + ".out");

    maskPatterns(patterns, f.getPath());

    cmdArray = new String[] {
        "diff", "-a",
        f.getPath(),
        outFileName
    };

    System.out.println(org.apache.commons.lang.StringUtils.join(cmdArray, ' '));

    Process executor = Runtime.getRuntime().exec(cmdArray);

    StreamPrinter outPrinter = new StreamPrinter(
        executor.getInputStream(), null, SessionState.getConsole().getChildOutStream());
    StreamPrinter errPrinter = new StreamPrinter(
        executor.getErrorStream(), null, SessionState.getConsole().getChildErrStream());

    outPrinter.start();
    errPrinter.start();

    int exitVal = executor.waitFor();

    if (exitVal != 0 && overWrite) {
      System.out.println("Overwriting results");
      cmdArray = new String[3];
      cmdArray[0] = "cp";
      cmdArray[1] = f.getPath();
      cmdArray[2] = outFileName;
      executor = Runtime.getRuntime().exec(cmdArray);
      exitVal = executor.waitFor();
    }

    return exitVal;
  }

  public ASTNode parseQuery(String tname) throws Exception {
    return pd.parse(qMap.get(tname));
  }

  public void resetParser() throws SemanticException {
    drv.init();
    pd = new ParseDriver();
    sem = new SemanticAnalyzer(conf);
  }


  public List<Task<? extends Serializable>> analyzeAST(ASTNode ast) throws Exception {

    // Do semantic analysis and plan generation
    Context ctx = new Context(conf);
    while ((ast.getToken() == null) && (ast.getChildCount() > 0)) {
      ast = (ASTNode) ast.getChild(0);
    }
    sem.getOutputs().clear();
    sem.getInputs().clear();
    sem.analyze(ast, ctx);
    ctx.clear();
    return sem.getRootTasks();
  }

  public TreeMap<String, String> getQMap() {
    return qMap;
  }

  /**
   * QTestSetup defines test fixtures which are reused across testcases,
   * and are needed before any test can be run
   */
  public static class QTestSetup
  {
    private MiniZooKeeperCluster zooKeeperCluster = null;
    private int zkPort;
    private ZooKeeper zooKeeper;

    public QTestSetup() {
    }

    public void preTest(HiveConf conf) throws Exception {

      if (zooKeeperCluster == null) {
        String tmpdir =  System.getProperty("user.dir")+"/../build/ql/tmp";
        zooKeeperCluster = new MiniZooKeeperCluster();
        zkPort = zooKeeperCluster.startup(new File(tmpdir, "zookeeper"));
      }

      if (zooKeeper != null) {
        zooKeeper.close();
      }

      int sessionTimeout = conf.getIntVar(HiveConf.ConfVars.HIVE_ZOOKEEPER_SESSION_TIMEOUT);
      zooKeeper = new ZooKeeper("localhost:" + zkPort, sessionTimeout, null);

      String zkServer = "localhost";
      conf.set("hive.zookeeper.quorum", zkServer);
      conf.set("hive.zookeeper.client.port", "" + zkPort);
    }

    public void postTest(HiveConf conf) throws Exception {
      if (zooKeeperCluster == null) {
        return;
      }

      if (zooKeeper != null) {
        zooKeeper.close();
      }

      ZooKeeperHiveLockManager.releaseAllLocks(conf);
    }

    public void tearDown() throws Exception {
      if (zooKeeperCluster != null) {
        zooKeeperCluster.shutdown();
        zooKeeperCluster = null;
      }
    }
  }

  /**
   * QTRunner: Runnable class for running a a single query file.
   *
   **/
  public static class QTRunner implements Runnable {
    private final MiniHiveCluster qt;
    private final String fname;

    public QTRunner(MiniHiveCluster qt, String fname) {
      this.qt = qt;
      this.fname = fname;
    }

    public void run() {
      try {
        // assumption is that environment has already been cleaned once globally
        // hence each thread does not call cleanUp() and createSources() again
        qt.cliInit(fname, false);
        qt.executeClient(fname);
      } catch (Throwable e) {
        System.err.println("Query file " + fname + " failed with exception "
            + e.getMessage());
        e.printStackTrace();
        outputTestFailureHelpMessage();
      }
    }
  }

  /**
   * Setup to execute a set of query files. Uses QTestUtil to do so.
   *
   * @param qfiles
   *          array of input query files containing arbitrary number of hive
   *          queries
   * @param resDir
   *          output directory
   * @param logDir
   *          log directory
   * @return one QTestUtil for each query file
   */
  public static MiniHiveCluster[] queryListRunnerSetup(File[] qfiles, String resDir,
      String logDir) throws Exception
  {
	  MiniHiveCluster[] qt = new MiniHiveCluster[qfiles.length];
    for (int i = 0; i < qfiles.length; i++) {
      qt[i] = new MiniHiveCluster(resDir, logDir, false, "0.20");
      qt[i].addFile(qfiles[i]);
      qt[i].clearTestSideEffects();
    }

    return qt;
  }

  /**
   * Executes a set of query files in sequence.
   *
   * @param qfiles
   *          array of input query files containing arbitrary number of hive
   *          queries
   * @param qt
   *          array of QTestUtils, one per qfile
   * @return true if all queries passed, false otw
   */
  public static boolean queryListRunnerSingleThreaded(File[] qfiles, MiniHiveCluster[] qt)
    throws Exception
  {
    boolean failed = false;
    qt[0].cleanUp();
    qt[0].createSources();
    for (int i = 0; i < qfiles.length && !failed; i++) {
      qt[i].clearTestSideEffects();
      qt[i].cliInit(qfiles[i].getName(), false);
      qt[i].executeClient(qfiles[i].getName());
      int ecode = qt[i].checkCliDriverResults(qfiles[i].getName());
      if (ecode != 0) {
        failed = true;
        System.err.println("Test " + qfiles[i].getName()
            + " results check failed with error code " + ecode);
        outputTestFailureHelpMessage();
      }
      qt[i].clearPostTestEffects();
    }
    return (!failed);
  }

  /**
   * Executes a set of query files parallel.
   *
   * Each query file is run in a separate thread. The caller has to arrange
   * that different query files do not collide (in terms of destination tables)
   *
   * @param qfiles
   *          array of input query files containing arbitrary number of hive
   *          queries
   * @param qt
   *          array of QTestUtils, one per qfile
   * @return true if all queries passed, false otw
   *
   */
  public static boolean queryListRunnerMultiThreaded(File[] qfiles, MiniHiveCluster[] qt)
    throws Exception
  {
    boolean failed = false;

    // in multithreaded mode - do cleanup/initialization just once

    qt[0].cleanUp();
    qt[0].createSources();
    qt[0].clearTestSideEffects();

    QTRunner[] qtRunners = new MiniHiveCluster.QTRunner[qfiles.length];
    Thread[] qtThread = new Thread[qfiles.length];

    for (int i = 0; i < qfiles.length; i++) {
      qtRunners[i] = new MiniHiveCluster.QTRunner(qt[i], qfiles[i].getName());
      qtThread[i] = new Thread(qtRunners[i]);
    }

    for (int i = 0; i < qfiles.length; i++) {
      qtThread[i].start();
    }

    for (int i = 0; i < qfiles.length; i++) {
      qtThread[i].join();
      int ecode = qt[i].checkCliDriverResults(qfiles[i].getName());
      if (ecode != 0) {
        failed = true;
        System.err.println("Test " + qfiles[i].getName()
            + " results check failed with error code " + ecode);
        outputTestFailureHelpMessage();
      }
    }
    return (!failed);
  }

  public static void outputTestFailureHelpMessage() {
    System.err.println("See build/ql/tmp/hive.log, "
        + "or try \"ant test ... -Dtest.silent=false\" to get more logs.");
    System.err.flush();
  }
}
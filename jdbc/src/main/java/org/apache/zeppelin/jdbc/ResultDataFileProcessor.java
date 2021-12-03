package org.apache.zeppelin.jdbc;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static java.lang.System.getProperty;

public class ResultDataFileProcessor {

  Logger logger = LoggerFactory.getLogger(ResultDataFileProcessor.class);

  static final String RESULT_DATA_PATH = "zeppelin.result.data.path";

  String resultDataDir;
  Properties property;
  Map<Integer, File> map = new HashMap<>();

  public ResultDataFileProcessor(Properties property) {

    if (property == null) {
      property = new Properties();
    }
    this.property = property;

    prepare();
  }

  public synchronized void createFile(InterpreterContext context, int hashCode)
          throws IOException, InterruptedException {

    String resultFilePath = getFileName(context);
    File resultFile = new File(resultFilePath);
    if (resultFile.exists()) {
      if (!resultFile.delete()) {
        return;
      }
    }

    if (!resultFile.createNewFile()) {
      return;
    }

    map.put(hashCode, resultFile);
  }

  public File getFile(int hashCode) {
    File file = map.get(hashCode);
    map.remove(hashCode);
    return file;
  }

  public void deleteFile(InterpreterContext context) {
    String resultFilePath = getFileName(context);
    File resultFile = new File(resultFilePath);
    if (resultFile.exists()) {
      resultFile.delete();
    }
  }

  void prepare() {
    resultDataDir = getProperty(RESULT_DATA_PATH);
    if (resultDataDir == null) {
      resultDataDir = "/tmp/zeppelin-" + getProperty("user.name");
    }

    File file = new File(resultDataDir);
    if (!file.exists()) {
      if (!file.mkdir()) {
        logger.error("Can't make result directory: " + file);
      } else {
        logger.info("Created result directory: " + file);
      }
    }
  }

  public String getFileName(InterpreterContext context) {
    return resultDataDir + "/" + context.getNoteId() + "_" + context.getParagraphId();
  }

  public String getResultDataDir() {
    return resultDataDir;
  }
}

package org.apache.zeppelin.notebook.repo.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface RemoteStorageOperator {
  void createBucket(String bucketName) throws IOException;

  void deleteBucket(String bucketName) throws IOException;

  boolean doesObjectExist(String bucketName, String key) throws IOException;

  String getTextObject(String bucketName, String key) throws IOException;

  void putTextObject(String bucketName, String key, InputStream inputStream) throws IOException;

  void moveObject(String bucketName, String sourceKey, String destKey) throws IOException;

  void moveDir(String bucketName, String sourceDir, String destDir) throws IOException;

  void deleteDir(String bucketName, String dirname) throws IOException;

  void deleteFile(String bucketName, String objectKey) throws IOException;

  void deleteFiles(String bucketName, List<String> objectKeys) throws IOException;

  List<String> listDirObjects(String bucketName, String dirname);

  void shutdown();
}

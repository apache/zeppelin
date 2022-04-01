package org.apache.zeppelin.notebook.repo.storage;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class MockStorageOperator implements RemoteStorageOperator {

  private String mockRootFolder = "mock-storage-dir/";


  @Override
  public void createBucket(String bucketName) throws IOException {
    FileUtils.forceMkdir(new File(mockRootFolder + bucketName));
  }

  @Override
  public void deleteBucket(String bucketName) throws IOException {
    FileUtils.deleteDirectory(new File(mockRootFolder + bucketName));
  }

  @Override
  public boolean doesObjectExist(String bucketName, String key) throws IOException {
    File file = new File(mockRootFolder + bucketName + "/" + key);
    return file.exists() && !file.isDirectory();
  }

  @Override
  public String getTextObject(String bucketName, String key) throws IOException {
    if (!doesObjectExist(bucketName, key)) {
      throw new IOException("Note or its revision not found");
    }
    return FileUtils.readFileToString(new File(mockRootFolder + bucketName + "/" + key), "UTF-8");
  }

  @Override
  public void putTextObject(String bucketName, String key, InputStream inputStream) throws IOException {
    File destination = new File(mockRootFolder + bucketName + "/" + key);
    destination.getParentFile().mkdirs();
    FileUtils.copyInputStreamToFile(inputStream, destination);
  }

  @Override
  public void moveObject(String bucketName, String sourceKey, String destKey) throws IOException {
    FileUtils.moveFile(new File(mockRootFolder + bucketName + "/" + sourceKey),
            new File(mockRootFolder + bucketName + "/" + destKey));
  }

  @Override
  public void moveDir(String bucketName, String sourceDir, String destDir) throws IOException {
    List<String> objectKeys = listDirObjects(bucketName, sourceDir);
    for (String key : objectKeys) {
      moveObject(bucketName, key, destDir + key.substring(sourceDir.length()));
    }
  }

  @Override
  public void deleteDir(String bucketName, String dirname) throws IOException {
    List<String> keys = listDirObjects(bucketName, dirname);
    deleteFiles(bucketName, keys);
  }

  @Override
  public void deleteFile(String bucketName, String objectKey) throws IOException {
    FileUtils.forceDelete(new File(mockRootFolder + bucketName + "/" + objectKey));
  }

  @Override
  public void deleteFiles(String bucketName, List<String> objectKeys) throws IOException {
    if (objectKeys != null && objectKeys.size() > 0) {
      for (String objectKey : objectKeys) {
        deleteFile(bucketName, objectKey);
      }
    }
  }

  @Override
  public List<String> listDirObjects(String bucketName, String dirname) {
    File directory = new File(mockRootFolder + bucketName + "/" + dirname);
    if (!directory.isDirectory()) {
      return new ArrayList<>();
    }
    Collection<File> files = FileUtils.listFiles(directory, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
    return files.stream().map(file -> file.getPath().substring((mockRootFolder + bucketName + "/").length())).collect(Collectors.toList());
  }

  @Override
  public void shutdown() {

  }

}

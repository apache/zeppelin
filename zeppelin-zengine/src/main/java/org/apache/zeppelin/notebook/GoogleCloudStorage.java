package org.apache.zeppelin.notebook;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.amazonaws.util.StringInputStream;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.StorageScopes;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.Objects;
import com.google.api.services.storage.model.StorageObject;

/**
 * Simple wrapper around the Google Cloud Storage API
 */
public class GoogleCloudStorage {

  private static Logger LOGGER = LoggerFactory.getLogger(GoogleCloudStorage.class);

  private static GoogleCloudStorage instance;

  private ZeppelinConfiguration zConf;

  private Storage storage;

  private String defaultBucket;

  private GoogleCloudStorage(ZeppelinConfiguration zConf) throws IOException {

    this.zConf = zConf;

    try {
      this.storage = getStorage();
      LOGGER.info("Created Google Cloud Storage Connection, with Application Name: "
          + this.storage.getApplicationName());

      if (!checkBucketexists(defaultBucket)) {
        LOGGER.info("Creating Default Google Cloud Storage Bucket: " + defaultBucket);
        createBucket(defaultBucket);
      }

    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  public static synchronized GoogleCloudStorage get(ZeppelinConfiguration zConf)
      throws IOException {
    if (instance == null) {
      instance = new GoogleCloudStorage(zConf);
    }
    return instance;
  }

  private Storage getStorage() throws Exception {

    Storage storage = null;

    defaultBucket =
        zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_GOOGLE_APPLICATION_NAME);

    HttpTransport httpTransport = new NetHttpTransport();
    JsonFactory jsonFactory = new JacksonFactory();

    List<String> scopes = new ArrayList<String>();
    scopes.add(StorageScopes.DEVSTORAGE_FULL_CONTROL);

    GoogleCredential credentials;
    File credentialsPath = new File(
        zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_GOOGLE_CREDENTIALS_FILE));
    try (FileInputStream serviceAccountStream = new FileInputStream(credentialsPath)) {
      credentials = GoogleCredential.fromStream(serviceAccountStream);
    }

    credentials = credentials.createScoped(scopes);

    storage = new Storage.Builder(httpTransport, jsonFactory, credentials)
        .setApplicationName(defaultBucket).build();

    return storage;
  }

  /**
   * check if bucket exists
   * 
   * @param bucketName Name of bucket to create
   * @return true if bucket exists else false
   * @throws Exception
   */
  public boolean checkBucketexists(String bucketName) throws Exception {

    try {
      Bucket bucket = storage.buckets().get(bucketName).execute();
      if (bucket == null)
        return false;

    } catch (Exception e) {
      return false;
    }

    return true;
  }

  /**
   * Creates a bucket
   * 
   * @param bucketName Name of bucket to create
   * @throws Exception
   */
  public void createBucket(String bucketName) throws Exception {

    Bucket bucket = new Bucket();
    bucket.setName(bucketName);

    storage.buckets()
        .insert(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_GOOGLE_PROJECT_ID),
            bucket)
        .execute();
  }

  /**
   * Deletes a bucket
   * 
   * @param bucketName Name of bucket to delete
   * @throws Exception
   */
  public void deleteBucket(String bucketName) throws Exception {

    storage.buckets().delete(bucketName).execute();
  }

  /**
   * Lists the objects in a bucket
   * 
   * @param bucketName bucket name to list
   * @return Array of object names
   * @throws Exception
   */
  public List<String> listBucket(String bucketName) throws Exception {

    List<String> list = new ArrayList<String>();

    List<StorageObject> objects = storage.objects().list(bucketName).execute().getItems();
    if (objects != null) {
      for (StorageObject o : objects) {
        list.add(o.getName());
      }
    }

    return list;
  }

  /**
   * List the buckets with the project (Project is configured in properties)
   * 
   * @return
   * @throws Exception
   */
  public List<String> listBuckets() throws Exception {

    List<String> list = new ArrayList<String>();

    List<Bucket> buckets = storage.buckets()
        .list(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_GOOGLE_PROJECT_ID))
        .execute().getItems();
    if (buckets != null) {
      for (Bucket b : buckets) {
        list.add(b.getName());
      }
    }

    return list;
  }

  /**
   * Uploads a note file to a bucket. Filename and content type will be based on the original file.
   * 
   * @param notePath Note file name
   * @param noteJson Note Content
   * @throws Exception
   */
  public void putNote(String notePath, String noteJson) throws Exception {

    StorageObject object = new StorageObject();
    object.setBucket(defaultBucket);

    InputStreamContent content =
        new InputStreamContent("text/plain", new StringInputStream(noteJson));

    Storage.Objects.Insert insert = storage.objects().insert(defaultBucket, null, content);
    insert.setName(notePath);

    insert.execute();

  }

  /**
   * Get Note file within a bucket
   * 
   * @param notePath Name of note to retrieve
   * @return String Returns the note contents
   * @throws Exception
   */
  public String getNote(String notePath) throws Exception {

    Storage.Objects.Get get = storage.objects().get(defaultBucket, notePath).set("alt", "media");
    ByteArrayOutputStream stream = new ByteArrayOutputStream();

    get.executeAndDownloadTo(stream);

    byte[] buffer = stream.toByteArray();

    return new String(buffer, StandardCharsets.UTF_8);

  }

  /**
   * List all Notes within a bucket
   * 
   * @return List<String> returns the list of note names
   * @throws Exception
   */
  public List<String> listNotes() throws Exception {

    Objects objects = storage.objects().list(defaultBucket).execute();
    List<String> notes = new ArrayList<>();

    ArrayList<StorageObject> items = (ArrayList<StorageObject>) objects.get("items");
    if (items == null)
      return notes;

    for (StorageObject object : items) {
      notes.add(object.getName());
    }
    return notes;
  }

  /**
   * Deletes a note file within a bucket
   * 
   * @param notePath The note file to delete
   * @throws Exception
   */
  public void deleteNote(String notePath) throws Exception {

    storage.objects().delete(defaultBucket, notePath).execute();
  }
}

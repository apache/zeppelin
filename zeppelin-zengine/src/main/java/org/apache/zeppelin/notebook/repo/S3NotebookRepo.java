package org.apache.zeppelin.notebook.repo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.conf.Credentials;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 
 * @author root
 *
 */
public class S3NotebookRepo implements NotebookRepo{
  
  Logger logger = LoggerFactory.getLogger(S3NotebookRepo.class);
  Credentials cred = new Credentials();
  private static String bucketName = "";
  AmazonS3 s3client = new AmazonS3Client(cred.getCredentials());
  
  private ZeppelinConfiguration conf;
  String user = null;
  String userShare = null;

  public S3NotebookRepo(ZeppelinConfiguration conf) throws IOException {
    this.conf = conf;
    user = conf.getUser();
    bucketName = conf.getBucketName();
  }

  @Override
  public List<NoteInfo> list() throws IOException {
    List<NoteInfo> infos = new LinkedList<NoteInfo>();
    NoteInfo info = null;
    try {
      ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
          .withBucketName(bucketName)
          .withPrefix(user + "/" + "notebook");
      ObjectListing objectListing;            
      do {
        objectListing = s3client.listObjects(listObjectsRequest);
        
        for (S3ObjectSummary objectSummary : 
          objectListing.getObjectSummaries()) {
          if (objectSummary.getKey().contains("note.json")) {
            try {
              info = getNoteInfo(objectSummary.getKey());
              if (info != null) {
                infos.add(info);
              }
            } catch (IOException e) {
              logger.error("Can't read note ", e);
            }
          }
        }
        
        listObjectsRequest.setMarker(objectListing.getNextMarker());
      } while (objectListing.isTruncated());
    } catch (AmazonServiceException ase) {
             
    } catch (AmazonClientException ace) {
      logger.info("Caught an AmazonClientException, " +
          "which means the client encountered " +
          "an internal error while trying to communicate" +
          " with S3, " +
          "such as not being able to access the network.");
      logger.info("Error Message: " + ace.getMessage());
    }
    logger.info("NOTE NAME: " + infos.get(1).getName());
    return infos;
  }

  private Note getNote(String key) throws IOException {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    Gson gson = gsonBuilder.create();
    
    S3Object s3object = s3client.getObject(new GetObjectRequest(
        bucketName, key));
    
    InputStream ins = s3object.getObjectContent();
    String json = IOUtils.toString(ins, conf.getString(ConfVars.ZEPPELIN_ENCODING));
    ins.close();
    Note note = gson.fromJson(json, Note.class);

    for (Paragraph p : note.getParagraphs()) {
      if (p.getStatus() == Status.PENDING || p.getStatus() == Status.RUNNING) {
        p.setStatus(Status.ABORT);
      }
    }
    return note;
  }

  private NoteInfo getNoteInfo(String key) throws IOException {
    Note note = getNote(key);
    return new NoteInfo(note);
  }

  @Override
  public Note get(String noteId) throws IOException {
    return getNote(user + "/" + "notebook" + "/" + noteId + "/" + "note.json");
  }

  @Override
  public void save(Note note) throws IOException {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    Gson gson = gsonBuilder.create();
    String json = gson.toJson(note);
    String key = user + "/" + "notebook" + "/" + note.id();
    
    File file = File.createTempFile("note.json", ".txt");
    file.deleteOnExit();
    Writer writer = new OutputStreamWriter(new FileOutputStream(file));
    
    writer.write(json);
    writer.close();
    s3client.putObject(new PutObjectRequest(
        bucketName, key, file));
  }
  
  @Override
  public void remove(String noteId) throws IOException {
    s3client.deleteObject(new DeleteObjectRequest(bucketName,
      noteId));
  }
}

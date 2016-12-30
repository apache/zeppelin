package org.apache.zeppelin.notebook.repo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.type;
import static com.mongodb.client.model.Filters.in;

import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.UpdateOptions;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.NotebookImportDeserializer;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.ApplicationState;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.bson.BsonType;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Backend for storing Notebook on MongoDB
 */
public class MongoNotebookRepo implements NotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(MongoNotebookRepo.class);

  private final ZeppelinConfiguration conf;
  private final MongoClient mongo;
  private final MongoDatabase db;
  private final MongoCollection<Document> coll;

  public MongoNotebookRepo(ZeppelinConfiguration conf) throws IOException {
    this.conf = conf;

    mongo = new MongoClient(new MongoClientURI(conf.getMongoUri()));
    db = mongo.getDatabase(conf.getMongoDatabase());
    coll = db.getCollection(conf.getMongoCollection());

    if (conf.getMongoAutoimport()) {
      // import local notes into MongoDB
      insertFileSystemNotes();
    }
  }

  /**
   * If environment variable ZEPPELIN_NOTEBOOK_MONGO_AUTOIMPORT is true,
   * this method will insert local notes into MongoDB on startup.
   * If a note already exists in MongoDB, skip it.
   */
  private void insertFileSystemNotes() throws IOException {
    LinkedList<Document> docs = new LinkedList<>(); // docs to be imported
    NotebookRepo vfsRepo = new VFSNotebookRepo(this.conf);
    List<NoteInfo> infos =  vfsRepo.list(null);
    // collect notes to be imported
    for (NoteInfo info : infos) {
      Note note = vfsRepo.get(info.getId(), null);
      Document doc = noteToDocument(note);
      docs.add(doc);
    }

    /*
     * 'ordered(false)' option allows to proceed bulk inserting even though
     * there are duplicated documents. The duplicated documents will be skipped
     * and print a WARN log.
     */
    try {
      coll.insertMany(docs, new InsertManyOptions().ordered(false));
    } catch (MongoBulkWriteException e) {
      printDuplicatedException(e);  //print duplicated document warning log
    }

    vfsRepo.close();  // it does nothing for now but maybe in the future...
  }

  /**
   * MongoBulkWriteException contains error messages that inform
   * which documents were duplicated. This method catches those ID and print them.
   * @param e
   */
  private void printDuplicatedException(MongoBulkWriteException e) {
    List<BulkWriteError> errors = e.getWriteErrors();
    for (BulkWriteError error : errors) {
      String msg = error.getMessage();
      Pattern pattern = Pattern.compile("[A-Z0-9]{9}"); // regex for note ID
      Matcher matcher = pattern.matcher(msg);
      if (matcher.find()) { // if there were a note ID
        String noteId = matcher.group();
        LOG.warn("Note " + noteId + " not inserted since already exists in MongoDB");
      }
    }
  }

  @Override
  public List<NoteInfo> list(AuthenticationInfo subject) throws IOException {
    syncId();

    List<NoteInfo> infos = new LinkedList<>();
    MongoCursor<Document> cursor = coll.find().iterator();

    while (cursor.hasNext()) {
      Document doc = cursor.next();
      Note note = documentToNote(doc);
      NoteInfo info = new NoteInfo(note);
      infos.add(info);
    }

    cursor.close();

    return infos;
  }

  /**
   * Find documents of which type of _id is object ID, and change it to note ID.
   * Since updating _id field is not allowed, remove original documents and insert
   * new ones with string _id(note ID)
   */
  private void syncId() {
    // find documents whose id type is object id
    MongoCursor<Document> cursor =  coll.find(type("_id", BsonType.OBJECT_ID)).iterator();
    // if there is no such document, exit
    if (!cursor.hasNext())
      return;

    List<ObjectId> oldDocIds = new LinkedList<>();    // document ids need to update
    List<Document> updatedDocs = new LinkedList<>();  // new documents to be inserted

    while (cursor.hasNext()) {
      Document doc = cursor.next();
      // store original _id
      ObjectId oldId = doc.getObjectId("_id");
      oldDocIds.add(oldId);
      // store the document with string _id (note id)
      String noteId = doc.getString("id");
      doc.put("_id", noteId);
      updatedDocs.add(doc);
    }

    coll.insertMany(updatedDocs);
    coll.deleteMany(in("_id", oldDocIds));

    cursor.close();
  }

  /**
   * Convert document to note
   */
  private Note documentToNote(Document doc) {
    // document to JSON
    String json = doc.toJson();
    // JSON to note
    Gson gson = new GsonBuilder()
            .registerTypeAdapter(Date.class, new NotebookImportDeserializer())
            .create();
    Note note = gson.fromJson(json, Note.class);

    for (Paragraph p : note.getParagraphs()) {
      if (p.getStatus() == Job.Status.PENDING || p.getStatus() == Job.Status.RUNNING) {
        p.setStatus(Job.Status.ABORT);
      }

      List<ApplicationState> appStates = p.getAllApplicationStates();
      if (appStates != null) {
        for (ApplicationState app : appStates) {
          if (app.getStatus() != ApplicationState.Status.ERROR) {
            app.setStatus(ApplicationState.Status.UNLOADED);
          }
        }
      }
    }

    return note;
  }

  /**
   * Convert note to document
   */
  private Document noteToDocument(Note note) {
    // note to JSON
    Gson gson = new GsonBuilder().create();
    String json = gson.toJson(note);
    // JSON to document
    Document doc = Document.parse(json);
    // set object id as note id
    doc.put("_id", note.getId());
    return doc;
  }

  @Override
  public Note get(String noteId, AuthenticationInfo subject) throws IOException {
    Document doc = coll.find(eq("_id", noteId)).first();

    if (doc == null) {
      throw new IOException("Note " + noteId + "not found");
    }

    return documentToNote(doc);
  }

  @Override
  public void save(Note note, AuthenticationInfo subject) throws IOException {
    Document doc = noteToDocument(note);
    coll.replaceOne(eq("_id", note.getId()), doc, new UpdateOptions().upsert(true));
  }

  @Override
  public void remove(String noteId, AuthenticationInfo subject) throws IOException {
    coll.deleteOne(eq("_id", noteId));
  }

  @Override
  public void close() {
    mongo.close();
  }

  @Override
  public Revision checkpoint(String noteId, String checkpointMsg, AuthenticationInfo subject)
      throws IOException {
    // no-op
    LOG.warn("Checkpoint feature isn't supported in {}", this.getClass().toString());
    return Revision.EMPTY;
  }

  @Override
  public Note get(String noteId, String revId, AuthenticationInfo subject) throws IOException {
    LOG.warn("Get note revision feature isn't supported in {}", this.getClass().toString());
    return null;
  }

  @Override
  public List<Revision> revisionHistory(String noteId, AuthenticationInfo subject) {
    LOG.warn("Get Note revisions feature isn't supported in {}", this.getClass().toString());
    return Collections.emptyList();
  }

  @Override
  public Note setNoteRevision(String noteId, String revId, AuthenticationInfo subject)
      throws IOException {
    // Auto-generated method stub
    return null;
  }

  @Override
  public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
    LOG.warn("Method not implemented");
    return Collections.emptyList();
  }

  @Override
  public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {
    LOG.warn("Method not implemented");
  }
}

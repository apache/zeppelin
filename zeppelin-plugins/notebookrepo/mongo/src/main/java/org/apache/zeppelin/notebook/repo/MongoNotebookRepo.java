package org.apache.zeppelin.notebook.repo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 * Backend for storing Notebook on MongoDB.
 */
public class MongoNotebookRepo implements NotebookRepo {

  private static final Logger LOG = LoggerFactory.getLogger(MongoNotebookRepo.class);

  private ZeppelinConfiguration conf;

  private MongoClient client;

  private MongoDatabase db;

  private MongoCollection<Document> notes;

  private MongoCollection<Document> folders;

  private String folderName;

  public MongoNotebookRepo() {
  }

  @Override
  public void init(ZeppelinConfiguration zConf) throws IOException {
    this.conf = zConf;
    client = new MongoClient(new MongoClientURI(conf.getMongoUri()));
    db = client.getDatabase(conf.getMongoDatabase());
    notes = db.getCollection(conf.getMongoCollection());
    folderName = conf.getMongoFolder();
    folders = db.getCollection(folderName);

    if (conf.getMongoAutoimport()) {
      // import local notes into MongoDB
      insertFileSystemNotes();
    }
  }

  private void insertFileSystemNotes() throws IOException {
    NotebookRepo vfsRepo = new VFSNotebookRepo();
    vfsRepo.init(this.conf);
    Map<String, NoteInfo> infos = vfsRepo.list(null);
    for (NoteInfo info : infos.values()) {
      Note note = vfsRepo.get(info.getId(), info.getPath(), null);
      save(note, null);
    }

    vfsRepo.close();
  }

  @Override
  public Map<String, NoteInfo> list(AuthenticationInfo subject) throws IOException {
    LOG.debug("list repo.");
    Map<String, NoteInfo> infos = new HashMap<>();

    Document match = new Document("$match", new Document(Fields.IS_DIR, false));
    Document graphLookup = new Document("$graphLookup",
        new Document("from", folderName)
            .append("startWith", "$" + Fields.PID)
            .append("connectFromField", Fields.PID)
            .append("connectToField", Fields.ID)
            .append("as", Fields.FULL_PATH));


    ArrayList<Document> list = Lists.newArrayList(match, graphLookup);

    AggregateIterable<Document> aggregate = folders.aggregate(list);
    for (Document document : aggregate) {
      String id = document.getString(Fields.ID);
      String name = document.getString(Fields.NAME);
      List<Document> fullPath = document.get(Fields.FULL_PATH, List.class);

      StringBuilder sb = new StringBuilder();
      for (Document pathNode : fullPath) {
        sb.append("/").append(pathNode.getString(Fields.NAME));
      }

      String fullPathStr = sb.append("/").append(name).toString();

      NoteInfo noteInfo = new NoteInfo(id, fullPathStr);
      infos.put(id, noteInfo);
    }

    return infos;
  }

  @Override
  public Note get(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
    LOG.debug("get note, noteId: {}, notePath:{}", noteId, notePath);

    return getNote(noteId, notePath);
  }

  private Note getNote(String noteId, String notePath) throws IOException {
    Document doc = notes.find(eq(Fields.ID, noteId)).first();
    if (doc == null) {
      throw new IOException("Note '" + noteId + "' in path '" + notePath + "'not found");
    }

    return documentToNote(doc);
  }

  @Override
  public void save(Note note, AuthenticationInfo subject) throws IOException {
    LOG.debug("save note, note: {}", note);
    String[] pathArray = toPathArray(note.getPath(), false);
    String pId = completeFolder(pathArray);
    saveNotePath(note.getId(), note.getName(), pId);
    saveNote(note);
  }

  /**
   * create until parent folder if not exists, save note to path.
   *
   * @param noteId note id
   * @param pId    note parent folder id
   */
  private void saveNotePath(String noteId, String noteName, String pId) {
    Document filter = new Document(Fields.ID, noteId);
    Document doc = new Document(Fields.ID, noteId)
        .append(Fields.PID, pId)
        .append(Fields.IS_DIR, false)
        .append(Fields.NAME, noteName);

    folders.replaceOne(filter, doc, new UpdateOptions().upsert(true));
  }

  private void saveNote(Note note) {
    Document doc = noteToDocument(note);
    notes.replaceOne(eq(Fields.ID, note.getId()), doc, new UpdateOptions().upsert(true));
  }

  @Override
  public void move(String noteId, String notePath, String newNotePath,
                   AuthenticationInfo subject) throws IOException {
    LOG.debug("move note, noteId: {}, notePath: {}, newNotePath: {}",
        noteId, notePath, newNotePath);
    if (StringUtils.equals(notePath, newNotePath)) {
      return;
    }
    String[] pathArray = toPathArray(newNotePath, true);
    String[] parentPathArray = Arrays.copyOfRange(pathArray, 0, pathArray.length - 1);
    String noteName = pathArray[pathArray.length - 1];
    String pId = completeFolder(parentPathArray);
    moveNote(noteId, pId, noteName);
  }

  private void moveNote(String noteId, String parentId, String noteName) {
    Document doc = new Document("$set",
        new Document(Fields.PID, parentId)
            .append(Fields.NAME, noteName));

    folders.updateOne(eq(Fields.ID, noteId), doc);
    notes.updateOne(eq(Fields.ID, noteId), Updates.set(Fields.NAME, noteName));
  }

  @Override
  public void move(String folderPath, String newFolderPath,
                   AuthenticationInfo subject) throws IOException {
    LOG.debug("move folder, folderPath: {}, newFolderPath: {}", folderPath, newFolderPath);
    if (StringUtils.equals(folderPath, newFolderPath)) {
      return;
    }

    String[] pathArray = toPathArray(folderPath, true);
    String[] newPathArray = toPathArray(newFolderPath, true);
    String[] newFolderParentArray = Arrays.copyOfRange(newPathArray, 0, newPathArray.length - 1);
    String id = findFolder(pathArray);
    String newPId = completeFolder(newFolderParentArray);
    String newFolderName = newPathArray[newPathArray.length - 1];

    Document doc = new Document("$set",
        new Document(Fields.ID, id)
            .append(Fields.PID, newPId)
            .append(Fields.IS_DIR, true)
            .append(Fields.NAME, newFolderName));

    folders.updateOne(eq(Fields.ID, id), doc);
  }

  @Override
  public void remove(String noteId, String notePath,
                     AuthenticationInfo subject) throws IOException {
    LOG.debug("remove note, noteId:{}, notePath:{}", noteId, notePath);
    folders.deleteOne(eq(Fields.ID, noteId));
    notes.deleteOne(eq(Fields.ID, noteId));

    //clean empty folder
    String[] pathArray = toPathArray(notePath, false);
    for (int i = pathArray.length; i >= 0; i--) {
      String[] current = Arrays.copyOfRange(pathArray, 0, i);
      String folderId = findFolder(current);
      boolean isEmpty = folders.count(eq(Fields.PID, folderId)) <= 0;
      if (isEmpty) {
        folders.deleteOne(eq(Fields.ID, folderId));
      } else {
        break;
      }
    }
  }

  @Override
  public void remove(String folderPath, AuthenticationInfo subject) throws IOException {
    LOG.debug("remove folder, folderPath: {}", folderPath);
    String[] pathArray = toPathArray(folderPath, true);
    String id = findFolder(pathArray);
    FindIterable<Document> iter = folders.find(eq(Fields.PID, id));
    for (Document node : iter) {
      String nodeId = node.getString(Fields.ID);
      Boolean isDir = node.getBoolean(Fields.IS_DIR);
      String nodeName = node.getString(Fields.NAME);

      if (isDir) {
        StringBuilder sb = new StringBuilder();
        for (String s : pathArray) {
          sb.append("/").append(s);
        }
        sb.append("/").append(nodeName);

        String nodePath = sb.toString();
        remove(nodePath, subject);
      } else {
        folders.deleteOne(eq(Fields.ID, nodeId));
        notes.deleteOne(eq(Fields.ID, nodeId));
      }

      folders.deleteOne(eq(Fields.ID, nodeId));
    }
  }

  @Override
  public void close() {
    client.close();
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

  /**
   * create until parent folder if not exists.
   *
   * @param splitPath path to completed.
   * @return direct parent folder id
   */
  private String completeFolder(String[] splitPath) {
    String pId = "0";
    for (String currentPath : splitPath) {
      Document query = new Document(Fields.PID, pId)
          .append(Fields.IS_DIR, true)
          .append(Fields.NAME, currentPath);

      String cId = new ObjectId().toString();
      Document doc = new Document("$setOnInsert",
          new Document(Fields.ID, cId)
              .append(Fields.PID, pId)
              .append(Fields.IS_DIR, true)
              .append(Fields.NAME, currentPath));

      Document exist = folders.find(query).first();
      if (exist == null) {
        folders.updateOne(query, doc, new UpdateOptions().upsert(true));
        pId = cId;
      } else {
        pId = exist.getString(Fields.ID);
      }
    }

    return pId;
  }

  /**
   * @param splitPath folder path to find
   * @return direct parent folder id
   */
  private String findFolder(String[] splitPath) {
    String pId = "0";
    if ((splitPath.length == 1 && "".equals(splitPath[0]))
        || ArrayUtils.isEmpty(splitPath)) {
      return pId;
    }
    for (String currentPath : splitPath) {
      Bson where = and(eq(Fields.PID, pId),
          eq(Fields.IS_DIR, true),
          eq(Fields.NAME, currentPath));
      Document node = folders.find(where).first();
      if (null == node) {
        throw new IllegalStateException("folder not found in path:" + currentPath);
      }

      pId = node.getString(Fields.ID);
    }

    return pId;
  }

  /**
   * e.g. "/a/b" => [a, b].
   *
   * @param notePath    path in str
   * @param includeLast whether return file/folder name in path
   * @return path in array
   */
  private String[] toPathArray(String notePath, boolean includeLast) {
    if (null == notePath || notePath.length() == 0) {
      throw new NullPointerException("notePath is null");
    }
    //replace multiple "/" to one
    notePath = notePath.replaceAll("/+", "/");

    if ("/".equals(notePath)) {
      return ArrayUtils.EMPTY_STRING_ARRAY;
    }

    String[] arr = notePath.substring(1).split("/");

    return includeLast ? arr : Arrays.copyOfRange(arr, 0, arr.length - 1);
  }

  /**
   * Convert document to note.
   */
  private Note documentToNote(Document doc) {
    // document to JSON
    String json = doc.toJson();
    // JSON to note
    return Note.fromJson(json);
  }

  /**
   * Convert note to document.
   */
  private Document noteToDocument(Note note) {
    // note to JSON
    String json = note.toJson();
    // JSON to document
    Document doc = Document.parse(json);
    // set object id as note id
    doc.put(Fields.ID, note.getId());
    return doc;
  }

  private class Fields {

    private static final String ID = "_id";

    private static final String NAME = "name";

    private static final String IS_DIR = "isDir";

    /**
     * parent folder id.
     */
    private static final String PID = "pId";

    private static final String FULL_PATH = "fullPath";
  }
}

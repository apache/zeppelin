package org.apache.zeppelin.search;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.TextFragment;
import org.apache.lucene.search.highlight.TokenSources;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 * Service for search (indexing and query) the notebooks
 *
 * TODO(bzz): document thread-safety
 */
public class SearchService {
  private static final Logger LOG = LoggerFactory.getLogger(SearchService.class);

  static final String SEARCH_FIELD = "contents";
  static final String ID_FIELD = "id";

  Directory ramDirectory;
  Analyzer analyzer;
  IndexWriterConfig iwc;
  IndexWriter writer;

  public SearchService() {
    ramDirectory = new RAMDirectory();
    analyzer = new StandardAnalyzer();
    iwc = new IndexWriterConfig(analyzer);
    try {
      writer = new IndexWriter(ramDirectory, iwc);
    } catch (IOException e) {
      LOG.error("Failed to reate new IndexWriter", e);
    }
  }

  /**
   * Full-text search in all the notebooks
   *
   * @param queryStr a query
   * @return A list of matching paragraphs (id, text, snippet w/ highlight)
   */
  public List<Map<String, String>> search(String queryStr) {
    if (null == ramDirectory) {
      throw new IllegalStateException(
          "Something went wrong on instance creation time, index dir is null");
    }
    List<Map<String, String>> result = Collections.emptyList();
    try (IndexReader indexReader = DirectoryReader.open(ramDirectory)) {
      IndexSearcher indexSearcher = new IndexSearcher(indexReader);
      Analyzer analyzer = new StandardAnalyzer();
      QueryParser parser = new QueryParser(SEARCH_FIELD, analyzer);

      Query query = parser.parse(queryStr);
      LOG.info("Searching for: " + query.toString(SEARCH_FIELD));

      SimpleHTMLFormatter htmlFormatter = new SimpleHTMLFormatter();
      Highlighter highlighter = new Highlighter(htmlFormatter, new QueryScorer(query));

      result = doSearch(indexSearcher, query, analyzer, highlighter);
      indexReader.close();
    } catch (IOException e) {
      LOG.error("Failed to open index dir {}, make sure indexing finished OK", ramDirectory, e);
    } catch (ParseException e) {
      LOG.error("Failed to parse query " + queryStr, e);
    }
    return result;
  }

  private List<Map<String, String>> doSearch(IndexSearcher searcher, Query query,
      Analyzer analyzer, Highlighter highlighter) {
    List<Map<String, String>> matchingParagraphs = Lists.newArrayList();
    ScoreDoc[] hits;
    try {
      hits = searcher.search(query, 20).scoreDocs;
      for (int i = 0; i < hits.length; i++) {
        LOG.info("doc={} score={}", hits[i].doc, hits[i].score);

        int id = hits[i].doc;
        Document doc = searcher.doc(id);
        String path = doc.get(ID_FIELD);
        if (path != null) {
          LOG.info((i + 1) + ". " + path);
          String title = doc.get("title");
          if (title != null) {
            LOG.info("   Title: {}", doc.get("title"));
          }

          String text = doc.get(SEARCH_FIELD);
          TokenStream tokenStream = TokenSources.getTokenStream(searcher.getIndexReader(), id,
              SEARCH_FIELD, analyzer);
          TextFragment[] frag = highlighter.getBestTextFragments(tokenStream, text, true, 3);
          // TODO(bzz): remove this as too verbose
          LOG.info("    {} fragments found for query '{}' in '{}'", frag.length, query, text);
          for (int j = 0; j < frag.length; j++) {
            if ((frag[j] != null) && (frag[j].getScore() > 0)) {
              LOG.info("    Fragment: {}", frag[j].toString());
            }
          }
          String fragment = (frag != null && frag.length > 0) ? frag[0].toString() : "";

          matchingParagraphs.add(ImmutableMap.of("id", path, // <noteId>/paragraph/<paragraphId>
              "name", title, "snippet", fragment, "text", text));
        } else {
          LOG.info("{}. No {} for this document", i + 1, ID_FIELD);
        }
      }
    } catch (IOException | InvalidTokenOffsetsException e) {
      LOG.error("Exception on searching for {}", query, e);
      ;
    }
    return matchingParagraphs;
  }

  /**
   * Indexes full collection of notes: all the paragraph
   *
   * @param collection of Notes
   */
  public void index(Collection<Note> collection) {
    long start = System.nanoTime();
    try {
      indexDocs(writer, collection);
      long end = System.nanoTime();
      LOG.info("Indexing {} notebooks took {}ms",
          collection.size(), TimeUnit.NANOSECONDS.toMillis(end - start));
    } catch (IOException e) {
      LOG.error("Failed to index all Notebooks", e);
    } finally {
      try { // save what's been indexed, even if not full collection
        writer.commit();
      } catch (IOException e) {
        LOG.error("Failed to save index", e);
      }
    }
  }

  public void updateDoc(String noteId, String noteName, Paragraph p) throws IOException {
    Document doc = newDocument(noteId, noteName, p);
    try {
      writer.updateDocument(new Term(ID_FIELD, formatId(noteId, p.getId())), doc);
      writer.commit();
    } catch (Exception e) {
      LOG.error("Failed to index all Notebooks", e);
    }
  }

  /**
   * Frees the recourses used by Lucene index
   */
  public void close() {
    try {
      writer.close();
    } catch (IOException e) {
      LOG.error("Failed to .close() the notebook index", e);
    }
  }


  /**
   * Indexes the given list of notebooks
   *
   * @param writer
   *          Writer to the index where the given file/dir info will be stored
   * @param path
   *          The file to index, or the directory to recurse into to find files
   *          to index
   * @throws IOException
   *           If there is a low-level I/O error
   */
  void indexDocs(final IndexWriter writer, Collection<Note> notes) throws IOException {
    for (Note note : notes) {
      indexDoc(writer, note.getId(), note.getName());
      for (Paragraph doc : note.getParagraphs()) {
        if (doc.getText() == null) {
          LOG.debug("Skipping empty paragraph");
          continue;
        }
        indexDoc(writer, note.getId(), note.getName(), doc);
      }
    }
  }

  /**
   * Indexes a notebook name
   * @throws IOException
   */
  private void indexDoc(IndexWriter w, String noteId, String noteName) throws IOException {
    LOG.debug("Indexing Notebook {}, '{}'", noteId, noteName);
    if (null == noteName || noteName.isEmpty()) {
      LOG.debug("Skipping empty notebook name");
      return;
    }
    Document doc = newDocument(noteId, noteName);
    w.addDocument(doc);
  }

  /**
   * Indexes a single paragraph = document
   */
  void indexDoc(IndexWriter w, String noteId, String noteName, Paragraph p) throws IOException {
    Document doc = newDocument(noteId, noteName, p);
    w.addDocument(doc);
  }

  private Document newDocument(String noteId, String noteName, Paragraph p) {
    Document doc = new Document();

    String id = formatId(noteId, p.getId());
    Field pathField = new StringField(ID_FIELD, id, Field.Store.YES);
    doc.add(pathField);

    doc.add(new StringField("title", noteName, Field.Store.YES));

    Date date = p.getDateStarted() != null ? p.getDateStarted() : p.getDateCreated();
    doc.add(new LongField("modified", date.getTime(), Field.Store.NO));
    doc.add(new TextField(SEARCH_FIELD, p.getText(), Field.Store.YES));
    return doc;
  }

  //TODO(bzz): refactor and re-use code from above
  private Document newDocument(String noteId, String noteName) {
    Document doc = new Document();

    Field pathField = new StringField(ID_FIELD, noteId, Field.Store.YES);
    doc.add(pathField);

    doc.add(new StringField("title", noteName, Field.Store.YES));

    //doc.add(new LongField("modified", date.getTime(), Field.Store.NO));
    doc.add(new TextField(SEARCH_FIELD, noteName, Field.Store.YES));
    return doc;
  }

  /**
   * ID looks like '<note-id>/paragraph/<paragraph-id>'
   *
   * @param noteId If of the Note
   * @param paragraphId Id of the paragraph
   *
   * @return
   */
  private String formatId(String noteId, String paragraphId) {
    return String.format("%s/paragraph/%s", noteId, paragraphId);
  }

}

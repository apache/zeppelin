package org.apache.zeppelin.search;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
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
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 * TODO(bzz): find a better name
 */
public class SearchService {
  private static final Logger LOG = LoggerFactory.getLogger(SearchService.class);

  Directory ramDirectory;
  static final String SEARCH_FIELD = "contents";
  static final String ID_FIELD = "contents";

  public List<Map<String, String>> search(String queryStr) {
    List<Map<String, String>> result = Collections.emptyList();
    try (IndexReader indexReader = DirectoryReader.open(ramDirectory)) {
      IndexSearcher indexSearcher = new IndexSearcher(indexReader);
      Analyzer analyzer = new StandardAnalyzer();
      QueryParser parser = new QueryParser(SEARCH_FIELD, analyzer);

      Query query = parser.parse(queryStr);
      LOG.info("Searching for: " + query.toString(SEARCH_FIELD));

      result = doSearch(indexSearcher, query);

    } catch (IOException e) {
      LOG.error("Faild to open index dir", e);
    } catch (ParseException e) {
      LOG.error("Faild to parse query " + queryStr, e);
    }
    return result;
  }

  private List<Map<String, String>> doSearch(IndexSearcher searcher, Query query) {
    List<Map<String, String>> matchingParagraphs = Lists.newArrayList();
    ScoreDoc[] hits;
    try {
      hits = searcher.search(query, 20).scoreDocs;
      for (int i = 0; i < hits.length; i++) {
        LOG.info("doc={} score={}", hits[i].doc, hits[i].score);

        Document doc = searcher.doc(hits[i].doc);
        String path = doc.get(ID_FIELD);
        if (path != null) {
          LOG.info((i + 1) + ". " + path);
          String title = doc.get("title");
          if (title != null) {
            LOG.info("   Title: {}", doc.get("title"));
          }
          matchingParagraphs.add(ImmutableMap.of("id", path, "name", title));
        } else {
          LOG.info("{}. No {} for this document", i + 1, ID_FIELD);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return matchingParagraphs;
  }

  public void index(List<Note> notebooks) {
    try {
      Date start = new Date();
      ramDirectory = new RAMDirectory();
      Analyzer analyzer = new StandardAnalyzer();
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
      IndexWriter writer = new IndexWriter(ramDirectory, iwc);

      indexDocs(writer, notebooks);

      writer.close();
      Date end = new Date();
      LOG.info(end.getTime() - start.getTime() + " total milliseconds");
    } catch (Exception e) {
      LOG.error("Failed to index all Notebooks", e);
    }
  }

  /**
   * Indexes the given list of notebooks
   *
   * @param writer Writer to the index where the given file/dir info will be stored
   * @param path The file to index, or the directory to recurse into to find files to index
   * @throws IOException If there is a low-level I/O error
   */
  void indexDocs(final IndexWriter writer, List<Note> docs) throws IOException {
    for (Note note: docs) {
      for (Paragraph doc: note.getParagraphs()) {
        if (doc.getText() == null) {
          LOG.info("Skipping empty paragraph");
          continue;
        }
        indexDoc(writer, note, doc);
      }
    }
  }

  /** Indexes a single paragraph = document */
  void indexDoc(IndexWriter writer, Note note, Paragraph p) throws IOException {
      Document doc = new Document();

      //<note-id>/paragraph/<paragraph-id>
      String id = String.format("%s/paragraph/%s", note.getId(), p.getId());
      Field pathField = new StringField(ID_FIELD, id, Field.Store.YES);
      doc.add(pathField);

      doc.add(new StringField("title", note.getName(), Field.Store.YES));

      Date date = p.getDateStarted() != null ? p.getDateStarted() : p.getDateCreated();
      doc.add(new LongField("modified", date.getTime(), Field.Store.NO));
      doc.add(new TextField(SEARCH_FIELD, p.getText(), Field.Store.YES));

      writer.addDocument(doc);
  }

}

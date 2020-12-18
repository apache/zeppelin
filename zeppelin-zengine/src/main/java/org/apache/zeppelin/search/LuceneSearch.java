/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.search;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.inject.Inject;

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
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.TextFragment;
import org.apache.lucene.search.highlight.TokenSources;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Search (both, indexing and query) the notebooks using Lucene. Query is thread-safe, as creates
 * new IndexReader every time. Index is thread-safe, as re-uses single IndexWriter, which is
 * thread-safe.
 */
public class LuceneSearch extends SearchService {
  private static final Logger LOGGER = LoggerFactory.getLogger(LuceneSearch.class);

  private static final String SEARCH_FIELD_TEXT = "contents";
  private static final String SEARCH_FIELD_TITLE = "header";
  private static final String PARAGRAPH = "paragraph";
  private static final String ID_FIELD = "id";

  private Path indexPath;
  private Directory indexDirectory;
  private Analyzer analyzer;
  private IndexWriterConfig indexWriterConfig;
  private IndexWriter indexWriter;

  @Inject
  public LuceneSearch(ZeppelinConfiguration conf) {
    super("LuceneSearch-Thread");

    if (conf.isZeppelinSearchUseDisk()) {
      try {
        this.indexPath = Paths.get(conf.getZeppelinSearchIndexPath());
        this.indexDirectory = FSDirectory.open(indexPath);
        LOGGER.info("Use {} for storing lucene search index", this.indexPath);
      } catch (IOException e) {
        throw new RuntimeException(
            "Failed to create index directory for search service. Use memory instead", e);
      }
    } else {
      this.indexDirectory = new RAMDirectory();
    }
    this.analyzer = new StandardAnalyzer();
    this.indexWriterConfig = new IndexWriterConfig(analyzer);
    try {
      this.indexWriter = new IndexWriter(indexDirectory, indexWriterConfig);
    } catch (IOException e) {
      LOGGER.error("Failed to create new IndexWriter", e);
    }
  }

  /* (non-Javadoc)
   * @see org.apache.zeppelin.search.Search#query(java.lang.String)
   */
  @Override
  public List<Map<String, String>> query(String queryStr) {
    if (null == indexDirectory) {
      throw new IllegalStateException(
          "Something went wrong on instance creation time, index dir is null");
    }
    List<Map<String, String>> result = Collections.emptyList();
    try (IndexReader indexReader = DirectoryReader.open(indexDirectory)) {
      IndexSearcher indexSearcher = new IndexSearcher(indexReader);
      Analyzer analyzer = new StandardAnalyzer();
      MultiFieldQueryParser parser =
          new MultiFieldQueryParser(new String[] {SEARCH_FIELD_TEXT, SEARCH_FIELD_TITLE}, analyzer);

      Query query = parser.parse(queryStr);
      LOGGER.debug("Searching for: {}", query.toString(SEARCH_FIELD_TEXT));

      SimpleHTMLFormatter htmlFormatter = new SimpleHTMLFormatter();
      Highlighter highlighter = new Highlighter(htmlFormatter, new QueryScorer(query));

      result = doSearch(indexSearcher, query, analyzer, highlighter);
    } catch (IOException e) {
      LOGGER.error("Failed to open index dir {}, make sure indexing finished OK", indexDirectory, e);
    } catch (ParseException e) {
      LOGGER.error("Failed to parse query {}", queryStr, e);
    }
    return result;
  }

  private List<Map<String, String>> doSearch(
      IndexSearcher searcher, Query query, Analyzer analyzer, Highlighter highlighter) {
    List<Map<String, String>> matchingParagraphs = Lists.newArrayList();
    ScoreDoc[] hits;
    try {
      hits = searcher.search(query, 20).scoreDocs;
      for (int i = 0; i < hits.length; i++) {
        LOGGER.debug("doc={} score={}", hits[i].doc, hits[i].score);

        int id = hits[i].doc;
        Document doc = searcher.doc(id);
        String path = doc.get(ID_FIELD);
        if (path != null) {
          LOGGER.debug( "{}. {}", (i + 1), path);
          String title = doc.get("title");
          if (title != null) {
            LOGGER.debug("   Title: {}", doc.get("title"));
          }

          String text = doc.get(SEARCH_FIELD_TEXT);
          String header = doc.get(SEARCH_FIELD_TITLE);
          String fragment = "";

          if (text != null) {
            TokenStream tokenStream =
                TokenSources.getTokenStream(
                    searcher.getIndexReader(), id, SEARCH_FIELD_TEXT, analyzer);
            TextFragment[] frag = highlighter.getBestTextFragments(tokenStream, text, true, 3);
            LOGGER.debug("    {} fragments found for query '{}'", frag.length, query);
            for (int j = 0; j < frag.length; j++) {
              if ((frag[j] != null) && (frag[j].getScore() > 0)) {
                LOGGER.debug("    Fragment: {}", frag[j].toString());
              }
            }
            fragment = (frag != null && frag.length > 0) ? frag[0].toString() : "";
          }

          if (header != null) {
            TokenStream tokenTitle =
                TokenSources.getTokenStream(
                    searcher.getIndexReader(), id, SEARCH_FIELD_TITLE, analyzer);
            TextFragment[] frgTitle = highlighter.getBestTextFragments(tokenTitle, header, true, 3);
            header = (frgTitle != null && frgTitle.length > 0) ? frgTitle[0].toString() : "";
          } else {
            header = "";
          }
          matchingParagraphs.add(
              ImmutableMap.of(
                  "id", path, // <noteId>/paragraph/<paragraphId>
                  "name", title, "snippet", fragment, "text", text, "header", header));
        } else {
          LOGGER.info("{}. No {} for this document", i + 1, ID_FIELD);
        }
      }
    } catch (IOException | InvalidTokenOffsetsException e) {
      LOGGER.error("Exception on searching for {}", query, e);
    }
    return matchingParagraphs;
  }

  /* (non-Javadoc)
   * @see org.apache.zeppelin.search.Search#updateIndexDoc(org.apache.zeppelin.notebook.Note)
   */
  @Override
  public void updateNoteIndex(Note note) throws IOException {
    updateIndexNoteName(note);
  }

  private void updateIndexNoteName(Note note) throws IOException {
    String noteName = note.getName();
    String noteId = note.getId();
    LOGGER.debug("Update note index: {}, '{}'", noteId, noteName);
    if (null == noteName || noteName.isEmpty()) {
      LOGGER.debug("Skipping empty notebook name");
      return;
    }
    updateDoc(noteId, noteName, null);
  }

  @Override
  public void updateParagraphIndex(Paragraph p) throws IOException {
    LOGGER.debug("Update paragraph index: {}", p.getId());
    updateDoc(p.getNote().getId(), p.getNote().getName(), p);
  }

  /**
   * Updates index for the given note: either note.name or a paragraph If paragraph is <code>null
   * </code> - updates only for the note.name
   *
   * @param noteId
   * @param noteName
   * @param p
   * @throws IOException
   */
  private void updateDoc(String noteId, String noteName, Paragraph p) throws IOException {
    String id = formatId(noteId, p);
    Document doc = newDocument(id, noteName, p);
    try {
      indexWriter.updateDocument(new Term(ID_FIELD, id), doc);
      indexWriter.commit();
    } catch (IOException e) {
      LOGGER.error("Failed to update index of notebook {}", noteId, e);
    }
  }

  /**
   * If paragraph is not null, id is <noteId>/paragraphs/<paragraphId>, otherwise it's just
   * <noteId>.
   */
  static String formatId(String noteId, Paragraph p) {
    String id = noteId;
    if (null != p) {
      id = Joiner.on('/').join(id, PARAGRAPH, p.getId());
    }
    return id;
  }

  static String formatDeleteId(String noteId, Paragraph p) {
    String id = noteId;
    if (null != p) {
      id = Joiner.on('/').join(id, PARAGRAPH, p.getId());
    } else {
      id = id + "*";
    }
    return id;
  }

  /**
   * If paragraph is not null, indexes code in the paragraph, otherwise indexes the notebook name.
   *
   * @param id id of the document, different for Note name and paragraph
   * @param noteName name of the note
   * @param p paragraph
   * @return
   */
  private Document newDocument(String id, String noteName, Paragraph p) {
    Document doc = new Document();

    Field pathField = new StringField(ID_FIELD, id, Field.Store.YES);
    doc.add(pathField);
    doc.add(new StringField("title", noteName, Field.Store.YES));

    if (null != p) {
      if (p.getText() != null) {
        doc.add(new TextField(SEARCH_FIELD_TEXT, p.getText(), Field.Store.YES));
      }
      if (p.getTitle() != null) {
        doc.add(new TextField(SEARCH_FIELD_TITLE, p.getTitle(), Field.Store.YES));
      }
      Date date = p.getDateStarted() != null ? p.getDateStarted() : p.getDateCreated();
      doc.add(new LongField("modified", date.getTime(), Field.Store.NO));
    } else {
      doc.add(new TextField(SEARCH_FIELD_TEXT, noteName, Field.Store.YES));
    }
    return doc;
  }

  /* (non-Javadoc)
   * @see org.apache.zeppelin.search.Search#addIndexDoc(org.apache.zeppelin.notebook.Note)
   */
  @Override
  public void addNoteIndex(Note note) {
    try {
      addIndexDocAsync(note);
      indexWriter.commit();
    } catch (IOException e) {
      LOGGER.error("Failed to add note {} to index", note, e);
    }
  }

  @Override
  public void addParagraphIndex(Paragraph pararaph) throws IOException {
    updateDoc(pararaph.getNote().getId(), pararaph.getNote().getName(), pararaph);
  }

  /**
   * Indexes the given notebook, but does not commit changes.
   *
   * @param note
   * @throws IOException
   */
  private void addIndexDocAsync(Note note) throws IOException {
    indexNoteName(indexWriter, note.getId(), note.getName());
    for (Paragraph paragraph : note.getParagraphs()) {
      updateDoc(note.getId(), note.getName(), paragraph);
    }
  }

  /* (non-Javadoc)
   * @see org.apache.zeppelin.search.Search#deleteIndexDocs(org.apache.zeppelin.notebook.Note)
   */
  @Override
  public void deleteNoteIndex(Note note) {
    if (note == null) {
      return;
    }
    deleteDoc(note.getId(), null);
    for (Paragraph paragraph : note.getParagraphs()) {
      deleteParagraphIndex(note.getId(), paragraph);
    }
  }

  /* (non-Javadoc)
   * @see org.apache.zeppelin.search.Search
   *  #deleteIndexDoc(org.apache.zeppelin.notebook.Note, org.apache.zeppelin.notebook.Paragraph)
   */
  @Override
  public void deleteParagraphIndex(String noteId, Paragraph p) {
    deleteDoc(noteId, p);
  }

  /**
   * Delete note index of paragraph index (when p is not null).
   *
   * @param noteId
   * @param p
   */
  private void deleteDoc(String noteId, Paragraph p) {
    String fullNoteOrJustParagraph = formatDeleteId(noteId, p);
    LOGGER.debug("Deleting note {}, out of: {}", noteId, indexWriter.numDocs());
    try {
      indexWriter.deleteDocuments(new WildcardQuery(new Term(ID_FIELD, fullNoteOrJustParagraph)));
      indexWriter.commit();
    } catch (IOException e) {
      LOGGER.error("Failed to delete {} from index by '{}'", noteId, fullNoteOrJustParagraph, e);
    }
    LOGGER.debug("Done, index contains {} docs now {}", indexWriter.numDocs());
  }

  /* (non-Javadoc)
   * @see org.apache.zeppelin.search.Search#close()
   */
  @Override
  public void close() {
    try {
      indexWriter.close();
    } catch (IOException e) {
      LOGGER.error("Failed to .close() the notebook index", e);
    }
  }

  /**
   * Indexes a notebook name
   *
   * @throws IOException
   */
  private void indexNoteName(IndexWriter w, String noteId, String noteName) throws IOException {
    LOGGER.debug("Indexing Notebook {}, '{}'", noteId, noteName);
    if (null == noteName || noteName.isEmpty()) {
      LOGGER.debug("Skipping empty notebook name");
      return;
    }
    updateDoc(noteId, noteName, null);
  }

  @Override
  public void startRebuildIndex(Stream<Note> notes) {
    Thread thread = new Thread(() -> {
      LOGGER.info("Starting rebuild index");
      notes.forEach(note -> {
        addNoteIndex(note);
        note.unLoad();
      });
      LOGGER.info("Finish rebuild index");
    });
    thread.setName("LuceneSearch-RebuildIndex-Thread");
    thread.start();
    try {
      thread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}

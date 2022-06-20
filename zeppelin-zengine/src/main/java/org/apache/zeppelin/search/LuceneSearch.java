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

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
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

  private final Directory indexDirectory;
  private final IndexWriter indexWriter;
  private final Notebook notebook;

  @Inject
  public LuceneSearch(ZeppelinConfiguration conf, Notebook notebook) throws IOException {
    super("LuceneSearch");
    this.notebook = notebook;
    if (conf.isZeppelinSearchUseDisk()) {
      try {
        final Path indexPath = Paths.get(conf.getZeppelinSearchIndexPath());
        this.indexDirectory = FSDirectory.open(indexPath);
        LOGGER.info("Use {} for storing lucene search index", indexPath);
      } catch (IOException e) {
        throw new IOException("Failed to create index directory for search service.", e);
      }
    } else {
      this.indexDirectory = new ByteBuffersDirectory();
    }
    final Analyzer analyzer = new StandardAnalyzer();
    final IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
    try {
      this.indexWriter = new IndexWriter(indexDirectory, indexWriterConfig);
    } catch (IOException e) {
      throw new IOException("Failed to create new IndexWriter", e);
    }
    if (conf.isIndexRebuild()) {
      notebook.addInitConsumer(this::addNoteIndex);
    }
    this.notebook.addNotebookEventListener(this);
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

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Searching for: {}", query.toString(SEARCH_FIELD_TEXT));
      }

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
    List<Map<String, String>> matchingParagraphs = new ArrayList<>();
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
            TextFragment[] frags = highlighter.getBestTextFragments(tokenStream, text, true, 3);
            LOGGER.debug("    {} fragments found for query '{}'", frags.length, query);
            for (TextFragment frag : frags) {
              if ((frag != null) && (frag.getScore() > 0)) {
                LOGGER.debug("    Fragment: {}", frag);
              }
            }
            fragment = (frags != null && frags.length > 0) ? frags[0].toString() : "";
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
  public void updateNoteIndex(String noteId) {
    updateIndexNoteName(noteId);
  }

  private void updateIndexNoteName(String noteId) {
     try {
      notebook.processNote(noteId,
        note -> {
          if (note != null) {
            String noteName = note.getName();
            LOGGER.debug("Update note index: {}, '{}'", noteId, noteName);
            if (null == noteName || noteName.isEmpty()) {
              LOGGER.debug("Skipping empty notebook name");
              return null;
            }
            updateDoc(noteId, noteName, null);
          }
          return null;
        });
    } catch (IOException e) {
      LOGGER.error("Unable to update note {}", noteId, e);
    }
  }

  @Override
  public void updateParagraphIndex(String noteId, String paragraphId) {
    try {
      notebook.processNote(noteId,
        note -> {
          if (note != null) {
            Paragraph p = note.getParagraph(paragraphId);
            LOGGER.debug("Update paragraph index: {}", paragraphId);
            updateDoc(noteId, note.getName(), p);
          }
          return null;
        });
    } catch (IOException e) {
      LOGGER.error("Unable to update paragraph {} of note {}", paragraphId, noteId, e);
    }
  }

  /**
   * Updates index for the given note: either note.name or a paragraph If paragraph is <code>null
   * </code> - updates only for the note.name
   *
   * @param noteId id of the note
   * @param noteName name of the note
   * @param p paragraph
   * @throws IOException if there is a low-level IO error
   */
  private void updateDoc(String noteId, String noteName, Paragraph p) throws IOException {
    String id = formatId(noteId, p);
    Document doc = newDocument(id, noteName, p);
    try {
      indexWriter.updateDocument(new Term(ID_FIELD, id), doc);
      indexWriter.commit();
    } catch (IOException e) {
      throw new IOException("Failed to update index of notebook " + noteId, e);
    }
  }

  /**
   * If paragraph is not null, id is <noteId>/paragraphs/<paragraphId>, otherwise it's just
   * <noteId>.
   */
  static String formatId(String noteId, Paragraph p) {
    String id = noteId;
    if (null != p) {
      id = String.join("/", id, PARAGRAPH, p.getId());
    }
    return id;
  }

  static String formatDeleteId(String noteId, String paragraphId) {
    String id = noteId;
    if (null != paragraphId) {
      id = String.join("/", id, PARAGRAPH, paragraphId);
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
   * @return a document
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
      doc.add(new LongPoint("modified", date.getTime()));
    } else {
      doc.add(new TextField(SEARCH_FIELD_TEXT, noteName, Field.Store.YES));
    }
    return doc;
  }

  /* (non-Javadoc)
   * @see org.apache.zeppelin.search.Search#addIndexDoc(org.apache.zeppelin.notebook.Note)
   */
  @Override
  public void addNoteIndex(String noteId) {
    try {
      notebook.processNote(noteId,
        note -> {
          if (note != null) {
            addIndexDocAsync(note);
          }
          return null;
        });
      indexWriter.commit();
    } catch (IOException e) {
      LOGGER.error("Failed to add note {} to index", noteId, e);
    }
  }

  @Override
  public void addParagraphIndex(String noteId, String paragraphId) {
    try {
      notebook.processNote(noteId,
        note -> {
          if (note != null) {
            Paragraph p = note.getParagraph(paragraphId);
            updateDoc(noteId, note.getName(), p);
          }
          return null;
        });
    } catch (IOException e) {
      LOGGER.error("Failed to add paragraph {} of note {} to index", paragraphId, noteId, e);
    }
  }

  /**
   * Indexes the given notebook, but does not commit changes.
   *
   * @param note Note to add the index
   * @throws IOException if there is a low-level IO error
   */
  private void addIndexDocAsync(Note note) throws IOException {
    indexNoteName(note.getId(), note.getName());
    for (Paragraph paragraph : note.getParagraphs()) {
      updateDoc(note.getId(), note.getName(), paragraph);
    }
  }

  /* (non-Javadoc)
   * @see org.apache.zeppelin.search.Search#deleteIndexDocs(org.apache.zeppelin.notebook.Note)
   */
  @Override
  public void deleteNoteIndex(String noteId) {
    try {
      deleteDoc(noteId, null);
      deleteParagraphIndex(noteId, null);
    } catch (IOException e) {
      LOGGER.error("Unable to delete note {}", noteId, e);
    }

  }

  /* (non-Javadoc)
   * @see org.apache.zeppelin.search.Search
   *  #deleteIndexDoc(org.apache.zeppelin.notebook.Note, org.apache.zeppelin.notebook.Paragraph)
   */
  @Override
  public void deleteParagraphIndex(String noteId, String paragraphId){
    try {
      deleteDoc(noteId, paragraphId);
    } catch (IOException e) {
      LOGGER.error("Unable to delete paragraph {} of note {}", paragraphId, noteId, e);
    }
  }

  /**
   * Delete note index of paragraph index (when p is not null).
   *
   * @param noteId id of the note
   * @param p paragraph
   */
  private void deleteDoc(String noteId, String paragraphId) throws IOException {
    String fullNoteOrJustParagraph = formatDeleteId(noteId, paragraphId);
    LOGGER.debug("Deleting note {}, out of: {}", noteId, indexWriter.getDocStats().numDocs);
    try {
      indexWriter.deleteDocuments(new WildcardQuery(new Term(ID_FIELD, fullNoteOrJustParagraph)));
      indexWriter.commit();
    } catch (IOException e) {
      throw new IOException("Failed to delete " + noteId + " from index by '" + fullNoteOrJustParagraph + "'", e);
    }
    LOGGER.debug("Done, index contains {} docs now", indexWriter.getDocStats().numDocs);
  }

  /* (non-Javadoc)
   * @see org.apache.zeppelin.search.Search#close()
   */
  @Override
  @PreDestroy
  public void close() {
    // First interrupt the LuceneSearch-Thread
    super.close();
    try {
      // Second close the indexWriter
      indexWriter.close();
    } catch (IOException e) {
      LOGGER.error("Failed to close the notebook index", e);
    }
  }

  /**
   * Indexes a notebook name
   *
   * @throws IOException if there is a low-level IO error
   */
  private void indexNoteName(String noteId, String noteName) throws IOException {
    LOGGER.debug("Indexing Notebook {}, '{}'", noteId, noteName);
    if (StringUtils.isBlank(noteName)) {
      LOGGER.debug("Skipping empty or blank notebook name");
      return;
    }
    updateDoc(noteId, noteName, null);
  }
}

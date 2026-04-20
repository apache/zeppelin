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

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import com.google.common.collect.ImmutableMap;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Semantic search for Zeppelin notebooks using ONNX-based sentence embeddings.
 *
 * <p>Uses the all-MiniLM-L6-v2 model to generate 384-dimensional embeddings for each
 * paragraph's text, title, and output. Queries are embedded with the same model and
 * matched via cosine similarity, enabling natural language search like
 * "yesterday's spend query" to find {@code WHERE date = current_date - 1}.
 *
 * <p>The embedding index is held in memory (float[][] + metadata) and persisted to a
 * single binary file on disk. For typical Zeppelin deployments (< 50K paragraphs),
 * brute-force cosine similarity completes in under 50ms.
 *
 * <p>Model files are downloaded on first use to {@code zeppelin.search.index.path}
 * and cached for subsequent starts.
 */
public class EmbeddingSearch extends SearchService {
  private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingSearch.class);

  private static final String MODEL_NAME = "all-MiniLM-L6-v2";
  private static final String MODEL_URL =
      "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx";
  private static final String TOKENIZER_URL =
      "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json";
  private static final int EMBEDDING_DIM = 384;
  private static final int MAX_SEQ_LENGTH = 256;
  private static final int MAX_RESULTS = 20;
  private static final float MIN_SIMILARITY = 0.25f;
  private static final int MAX_TEXT_LENGTH = 1500;

  static final String ID_FIELD = "id";
  private static final String PARAGRAPH = "paragraph";
  /** Regex to extract qualified table names from SQL (e.g. schema.table). */
  private static final Pattern TABLE_RE =
      Pattern.compile("(?:FROM|JOIN)\\s+([a-zA-Z_]\\w*\\.[a-zA-Z_]\\w*)", Pattern.CASE_INSENSITIVE);
  private static final float TABLE_BOOST = 0.05f;

  private final Notebook notebook;
  private final Path indexPath;

  // ONNX inference
  private OrtEnvironment ortEnv;
  private OrtSession ortSession;
  private HuggingFaceTokenizer tokenizer;

  // In-memory vector index: docId -> (embedding, metadata)
  private final ConcurrentHashMap<String, IndexEntry> index = new ConcurrentHashMap<>();
  private final ReadWriteLock indexLock = new ReentrantReadWriteLock();

  /** A single indexed document (paragraph or note name). */
  private static class IndexEntry {
    final float[] embedding;
    final String noteName;
    final String text;
    final String title;
    final String tables;
    final String output;

    IndexEntry(float[] embedding, String noteName, String text, String title,
               String tables, String output) {
      this.embedding = embedding;
      this.noteName = noteName;
      this.text = text;
      this.title = title;
      this.tables = tables;
      this.output = output;
    }
  }

  @Inject
  public EmbeddingSearch(ZeppelinConfiguration zConf, Notebook notebook) throws IOException {
    super("EmbeddingSearch");
    this.notebook = notebook;
    this.indexPath = Paths.get(zConf.getZeppelinSearchIndexPath());
    Files.createDirectories(indexPath);

    try {
      initModel();
    } catch (Exception e) {
      throw new IOException("Failed to initialize embedding model", e);
    }

    if (zConf.isIndexRebuild()) {
      notebook.addInitConsumer(this::addNoteIndex);
    }
    loadIndex();
    this.notebook.addNotebookEventListener(this);
  }

  /** Package-private constructor for testing without DI. */
  EmbeddingSearch(ZeppelinConfiguration zConf, Notebook notebook, boolean skipModel)
      throws IOException {
    super("EmbeddingSearch");
    this.notebook = notebook;
    this.indexPath = Paths.get(zConf.getZeppelinSearchIndexPath());
    Files.createDirectories(indexPath);
    if (!skipModel) {
      try {
        initModel();
      } catch (Exception e) {
        throw new IOException("Failed to initialize embedding model", e);
      }
    }
    if (zConf.isIndexRebuild()) {
      notebook.addInitConsumer(this::addNoteIndex);
    }
    this.notebook.addNotebookEventListener(this);
  }

  // ---- Model initialization ----

  private void initModel() throws OrtException, IOException {
    Path modelDir = indexPath.resolve("models").resolve(MODEL_NAME);
    Files.createDirectories(modelDir);

    Path modelFile = modelDir.resolve("model.onnx");
    Path tokenizerFile = modelDir.resolve("tokenizer.json");

    if (!Files.exists(modelFile)) {
      LOGGER.info("Downloading embedding model {} ...", MODEL_NAME);
      downloadFile(MODEL_URL, modelFile);
      LOGGER.info("Model downloaded to {}", modelFile);
    }
    if (!Files.exists(tokenizerFile)) {
      LOGGER.info("Downloading tokenizer for {} ...", MODEL_NAME);
      downloadFile(TOKENIZER_URL, tokenizerFile);
    }

    ortEnv = OrtEnvironment.getEnvironment();
    OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
    opts.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());
    ortSession = ortEnv.createSession(modelFile.toString(), opts);
    tokenizer = HuggingFaceTokenizer.newInstance(tokenizerFile);
    LOGGER.info("Embedding model loaded: {}, dim={}", MODEL_NAME, EMBEDDING_DIM);
  }

  private static void downloadFile(String urlStr, Path dest) throws IOException {
    URL url = new URL(urlStr);
    java.net.URLConnection conn = url.openConnection();
    conn.setConnectTimeout(30_000);
    conn.setReadTimeout(60_000);
    try (InputStream in = new BufferedInputStream(conn.getInputStream());
         FileOutputStream out = new FileOutputStream(dest.toFile())) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) != -1) {
        out.write(buf, 0, n);
      }
    }
  }

  // ---- Embedding computation ----

  /**
   * Compute a normalized embedding for the given text.
   * Uses mean pooling over token embeddings with attention mask.
   */
  float[] embed(String text) {
    if (ortSession == null || tokenizer == null) {
      return new float[EMBEDDING_DIM];
    }
    try {
      Encoding encoding = tokenizer.encode(text, true, true);
      long[] inputIds = encoding.getIds();
      long[] attentionMask = encoding.getAttentionMask();

      // Truncate to max sequence length
      int seqLen = Math.min(inputIds.length, MAX_SEQ_LENGTH);
      long[] ids = new long[seqLen];
      long[] mask = new long[seqLen];
      long[] tokenTypeIds = new long[seqLen];
      System.arraycopy(inputIds, 0, ids, 0, seqLen);
      System.arraycopy(attentionMask, 0, mask, 0, seqLen);

      long[] shape = {1, seqLen};
      OnnxTensor idsTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(ids), shape);
      OnnxTensor maskTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(mask), shape);
      OnnxTensor typeTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(tokenTypeIds), shape);

      Map<String, OnnxTensor> inputs = new HashMap<>();
      inputs.put("input_ids", idsTensor);
      inputs.put("attention_mask", maskTensor);
      inputs.put("token_type_ids", typeTensor);

      try (OrtSession.Result result = ortSession.run(inputs)) {
        // Output shape: [1, seqLen, 384] — mean pool over sequence dim
        float[][][] output = (float[][][]) result.get(0).getValue();
        float[] pooled = meanPool(output[0], mask, seqLen);
        normalize(pooled);
        return pooled;
      } finally {
        idsTensor.close();
        maskTensor.close();
        typeTensor.close();
      }
    } catch (OrtException e) {
      LOGGER.error("Embedding failed for text length {}", text.length(), e);
      return new float[EMBEDDING_DIM];
    }
  }

  /** Mean pooling: average token embeddings weighted by attention mask. */
  private static float[] meanPool(float[][] tokenEmbeddings, long[] mask, int seqLen) {
    float[] result = new float[EMBEDDING_DIM];
    float maskSum = 0;
    for (int i = 0; i < seqLen; i++) {
      if (mask[i] == 1) {
        maskSum++;
        for (int j = 0; j < EMBEDDING_DIM; j++) {
          result[j] += tokenEmbeddings[i][j];
        }
      }
    }
    if (maskSum > 0) {
      for (int j = 0; j < EMBEDDING_DIM; j++) {
        result[j] /= maskSum;
      }
    }
    return result;
  }

  /** L2-normalize in place. */
  private static void normalize(float[] vec) {
    float norm = 0;
    for (float v : vec) {
      norm += v * v;
    }
    norm = (float) Math.sqrt(norm);
    if (norm > 0) {
      for (int i = 0; i < vec.length; i++) {
        vec[i] /= norm;
      }
    }
  }

  /** Cosine similarity between two normalized vectors (= dot product). */
  private static float cosineSimilarity(float[] a, float[] b) {
    float dot = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
    }
    return dot;
  }

  // ---- Text extraction ----

  /**
   * Strip interpreter prefix like {@code %spark.sql}, {@code %athena} from paragraph text.
   * Handles both {@code %name\ncode} and {@code %name code} formats.
   */
  static String stripInterpreterPrefix(String text) {
    if (text == null || !text.startsWith("%")) {
      return text;
    }
    // Find end of interpreter directive: first newline or first space after %word
    int newlineIdx = text.indexOf('\n');
    if (newlineIdx >= 0) {
      return text.substring(newlineIdx + 1);
    }
    // Single-line: "%interpreter some code" — strip up to first space
    int spaceIdx = text.indexOf(' ');
    if (spaceIdx >= 0) {
      return text.substring(spaceIdx + 1);
    }
    // Just "%interpreter" with no content
    return "";
  }

  /**
   * Extract qualified table names (schema.table) from SQL text.
   */
  static String extractTables(String text) {
    if (text == null) {
      return "";
    }
    Set<String> tables = new HashSet<>();
    Matcher m = TABLE_RE.matcher(text);
    while (m.find()) {
      tables.add(m.group(1).toLowerCase());
    }
    return String.join(" ", tables);
  }

  /**
   * Extract searchable output text from paragraph results (TABLE headers, TEXT).
   */
  static String extractOutput(Paragraph p) {
    InterpreterResult result = p.getReturn();
    if (result == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (InterpreterResultMessage msg : result.message()) {
      if (msg.getType() == InterpreterResult.Type.TEXT
          || msg.getType() == InterpreterResult.Type.TABLE) {
        String data = msg.getData();
        if (StringUtils.isNotBlank(data)) {
          sb.append(data, 0, Math.min(data.length(), 500));
          sb.append("\n");
        }
      }
    }
    return sb.toString().trim();
  }

  /**
   * Build a rich text representation of a paragraph for embedding.
   * Includes code/text, title, table names, and output (table headers, text results).
   */
  private String buildParagraphText(String noteName, Paragraph p) {
    StringBuilder sb = new StringBuilder();
    if (StringUtils.isNotBlank(noteName)) {
      sb.append("Notebook: ").append(noteName).append("\n");
    }
    if (StringUtils.isNotBlank(p.getTitle())) {
      sb.append(p.getTitle()).append("\n");
    }
    if (StringUtils.isNotBlank(p.getText())) {
      String text = p.getText();
      // Strip interpreter prefix (e.g. "%spark.sql", "%athena\n")
      text = stripInterpreterPrefix(text);
      // Include extracted table names for better semantic matching
      String tables = extractTables(text);
      if (StringUtils.isNotBlank(tables)) {
        sb.append("Tables: ").append(tables).append("\n");
      }
      sb.append(text, 0, Math.min(text.length(), MAX_TEXT_LENGTH));
    }
    // Include output for richer semantic matching
    InterpreterResult result = p.getReturn();
    if (result != null) {
      for (InterpreterResultMessage msg : result.message()) {
        if (msg.getType() == InterpreterResult.Type.TEXT
            || msg.getType() == InterpreterResult.Type.TABLE) {
          String data = msg.getData();
          if (StringUtils.isNotBlank(data)) {
            sb.append("\n").append(data, 0, Math.min(data.length(), 500));
          }
        }
      }
    }
    return sb.toString();
  }

  // ---- SearchService implementation ----

  @Override
  public List<Map<String, String>> query(String queryStr) {
    if (StringUtils.isBlank(queryStr) || index.isEmpty()) {
      return Collections.emptyList();
    }

    float[] queryEmbedding = embed(queryStr);

    // Phase 1: find top-N results and discover relevant tables
    List<Map.Entry<String, Float>> scored = new ArrayList<>();
    indexLock.readLock().lock();
    try {
      for (Map.Entry<String, IndexEntry> entry : index.entrySet()) {
        float sim = cosineSimilarity(queryEmbedding, entry.getValue().embedding);
        scored.add(Map.entry(entry.getKey(), sim));
      }
    } finally {
      indexLock.readLock().unlock();
    }
    scored.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));

    // Collect tables from top-20 results, weighted by rank
    Map<String, Float> tableWeights = new HashMap<>();
    for (int i = 0; i < Math.min(scored.size(), 20); i++) {
      IndexEntry entry = index.get(scored.get(i).getKey());
      if (entry != null && StringUtils.isNotBlank(entry.tables)) {
        float weight = 1.0f / (i + 1);
        for (String t : entry.tables.split(" ")) {
          tableWeights.merge(t, weight, Float::sum);
        }
      }
    }
    // Keep tables with weight > 20% of top table's weight
    Set<String> relevantTables = new HashSet<>();
    if (!tableWeights.isEmpty()) {
      float maxWeight = Collections.max(tableWeights.values());
      float threshold = maxWeight * 0.2f;
      tableWeights.forEach((t, w) -> {
        if (w >= threshold) {
          relevantTables.add(t);
        }
      });
    }

    // Phase 2: re-score with table boost, collect candidates with boosted scores
    List<Map.Entry<Map<String, String>, Float>> candidates = new ArrayList<>();
    for (int i = 0; i < scored.size() && candidates.size() < MAX_RESULTS; i++) {
      float sim = scored.get(i).getValue();
      if (sim < MIN_SIMILARITY) {
        break;
      }
      String docId = scored.get(i).getKey();
      IndexEntry entry = index.get(docId);
      if (entry == null || StringUtils.isBlank(entry.text)) {
        continue;
      }
      if (!relevantTables.isEmpty() && StringUtils.isNotBlank(entry.tables)) {
        for (String t : entry.tables.split(" ")) {
          if (relevantTables.contains(t)) {
            sim += TABLE_BOOST;
          }
        }
      }
      StringBuilder header = new StringBuilder();
      if (StringUtils.isNotBlank(entry.title)) {
        header.append(entry.title).append("\n");
      }
      if (StringUtils.isNotBlank(entry.tables)) {
        header.append("📊 ").append(entry.tables).append("\n");
      }
      if (StringUtils.isNotBlank(entry.output)) {
        String out = entry.output;
        if (out.length() > 300) {
          out = out.substring(0, 300);
        }
        header.append("\n").append(out);
      }
      candidates.add(Map.entry(ImmutableMap.of(
          "id", docId,
          "name", entry.noteName != null ? entry.noteName : "",
          "snippet", entry.text,
          "text", entry.text,
          "header", header.toString()), sim));
    }
    // Re-sort by boosted score
    candidates.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
    List<Map<String, String>> results = new ArrayList<>();
    for (Map.Entry<Map<String, String>, Float> c : candidates) {
      results.add(c.getKey());
    }
    return results;
  }

  @Override
  public void addNoteIndex(String noteId) {
    try {
      notebook.processNote(noteId, note -> {
        if (note != null) {
          indexNote(note);
        }
        return null;
      });
      saveIndex();
    } catch (IOException e) {
      LOGGER.error("Failed to add note {} to index", noteId, e);
    }
  }

  @Override
  public void addParagraphIndex(String noteId, String paragraphId) {
    try {
      notebook.processNote(noteId, note -> {
        if (note != null) {
          Paragraph p = note.getParagraph(paragraphId);
          if (p != null) {
            indexParagraph(note.getId(), note.getName(), p);
          }
        }
        return null;
      });
      saveIndex();
    } catch (IOException e) {
      LOGGER.error("Failed to add paragraph {} of note {}", paragraphId, noteId, e);
    }
  }

  @Override
  public void updateNoteIndex(String noteId) {
    try {
      notebook.processNote(noteId, note -> {
        if (note != null) {
          indexNote(note);
        }
        return null;
      });
      saveIndex();
    } catch (IOException e) {
      LOGGER.error("Failed to update note index {}", noteId, e);
    }
  }

  @Override
  public void updateParagraphIndex(String noteId, String paragraphId) {
    try {
      notebook.processNote(noteId, note -> {
        if (note != null) {
          Paragraph p = note.getParagraph(paragraphId);
          if (p != null) {
            indexParagraph(noteId, note.getName(), p);
          }
        }
        return null;
      });
      saveIndex();
    } catch (IOException e) {
      LOGGER.error("Failed to update paragraph {} of note {}", paragraphId, noteId, e);
    }
  }

  @Override
  public void deleteNoteIndex(String noteId) {
    if (noteId == null) {
      return;
    }
    indexLock.writeLock().lock();
    try {
      index.entrySet().removeIf(e -> e.getKey().startsWith(noteId));
    } finally {
      indexLock.writeLock().unlock();
    }
    try {
      saveIndex();
    } catch (IOException e) {
      LOGGER.error("Failed to save index after deleting note {}", noteId, e);
    }
  }

  @Override
  public void deleteParagraphIndex(String noteId, String paragraphId) {
    if (noteId == null) {
      return;
    }
    String docId = paragraphId != null
        ? String.join("/", noteId, PARAGRAPH, paragraphId)
        : noteId;
    index.remove(docId);
    try {
      saveIndex();
    } catch (IOException e) {
      LOGGER.error("Failed to save index after deleting paragraph {}", docId, e);
    }
  }

  @Override
  @PreDestroy
  public void close() {
    super.close();
    try {
      if (ortSession != null) {
        ortSession.close();
      }
      if (tokenizer != null) {
        tokenizer.close();
      }
    } catch (OrtException e) {
      LOGGER.error("Failed to close ONNX session", e);
    }
  }

  // ---- Internal indexing ----

  private void indexNote(Note note) {
    String noteName = note.getName();
    // Index each paragraph (note name is included in paragraph embedding text)
    for (Paragraph p : note.getParagraphs()) {
      indexParagraph(note.getId(), noteName, p);
    }
  }

  private void indexParagraph(String noteId, String noteName, Paragraph p) {
    String text = buildParagraphText(noteName, p);
    if (StringUtils.isBlank(text)) {
      return;
    }
    float[] emb = embed(text);
    String docId = String.join("/", noteId, PARAGRAPH, p.getId());
    String title = p.getTitle() != null ? p.getTitle() : "";
    String pText = p.getText() != null ? stripInterpreterPrefix(p.getText()) : "";
    String tables = extractTables(pText);
    String output = extractOutput(p);

    indexLock.writeLock().lock();
    try {
      index.put(docId, new IndexEntry(emb, noteName, pText, title, tables, output));
    } finally {
      indexLock.writeLock().unlock();
    }
  }

  static String formatId(String noteId, Paragraph p) {
    if (p != null) {
      return String.join("/", noteId, PARAGRAPH, p.getId());
    }
    return noteId;
  }

  // ---- Persistence ----

  /**
   * Save index to a binary file.
   * Format: [int:version=3][int:count] then for each entry:
   *   [utf:docId] [utf:noteName] [utf:text] [utf:title] [utf:tables] [utf:output] [float[384]:embedding]
   */
  private void saveIndex() throws IOException {
    Path file = indexPath.resolve("embedding_index.bin");
    Path tmpFile = indexPath.resolve("embedding_index.bin.tmp");
    indexLock.readLock().lock();
    try {
      try (DataOutputStream out = new DataOutputStream(new FileOutputStream(tmpFile.toFile()))) {
        out.writeInt(3); // version 3: includes output field
        out.writeInt(index.size());
        for (Map.Entry<String, IndexEntry> e : index.entrySet()) {
          out.writeUTF(e.getKey());
          out.writeUTF(e.getValue().noteName != null ? e.getValue().noteName : "");
          String text = e.getValue().text != null ? e.getValue().text : "";
          if (text.length() > 2000) {
            text = text.substring(0, 2000);
          }
          out.writeUTF(text);
          out.writeUTF(e.getValue().title != null ? e.getValue().title : "");
          out.writeUTF(e.getValue().tables != null ? e.getValue().tables : "");
          String output = e.getValue().output != null ? e.getValue().output : "";
          if (output.length() > 1000) {
            output = output.substring(0, 1000);
          }
          out.writeUTF(output);
          for (float v : e.getValue().embedding) {
            out.writeFloat(v);
          }
        }
      }
      Files.move(tmpFile, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
          java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    } finally {
      indexLock.readLock().unlock();
    }
  }

  /** Load index from disk if it exists. Supports v1/v2/v3 formats. */
  private void loadIndex() {
    Path file = indexPath.resolve("embedding_index.bin");
    if (!Files.exists(file)) {
      return;
    }
    try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
      int first = in.readInt();
      int version;
      int count;
      if (first >= 2 && first <= 3) {
        version = first;
        count = in.readInt();
      } else {
        version = 1;
        count = first;
      }
      LOGGER.info("Loading {} embedding index entries (v{}) from {}", count, version, file);
      for (int i = 0; i < count; i++) {
        String docId = in.readUTF();
        String noteName = in.readUTF();
        String text = in.readUTF();
        String title = in.readUTF();
        String tables = version >= 2 ? in.readUTF() : "";
        String output = version >= 3 ? in.readUTF() : "";
        float[] emb = new float[EMBEDDING_DIM];
        for (int j = 0; j < EMBEDDING_DIM; j++) {
          emb[j] = in.readFloat();
        }
        index.put(docId, new IndexEntry(emb, noteName, text, title, tables, output));
      }
      LOGGER.info("Loaded {} entries into embedding index", index.size());
    } catch (IOException e) {
      LOGGER.warn("Failed to load embedding index, will rebuild on next indexing", e);
    }
  }
}

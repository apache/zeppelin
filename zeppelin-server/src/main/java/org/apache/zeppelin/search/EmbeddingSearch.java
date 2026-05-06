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

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
  private static final int EMBEDDING_DIM = 384;
  private static final int MAX_SEQ_LENGTH = 256;
  /** Maximum number of candidates returned from {@link #query(String)}. */
  private static final int MAX_RESULTS = 20;
  /**
   * Cosine similarity floor for a candidate to be considered a match.
   * Tuned empirically against all-MiniLM-L6-v2: values below this are effectively noise
   * for short-query / long-paragraph comparisons. See embedding-search.md for details.
   */
  private static final float MIN_SIMILARITY = 0.25f;
  private static final int MAX_TEXT_LENGTH = 1500;

  static final String ID_FIELD = "id";
  private static final String PARAGRAPH = "paragraph";
  /** Regex to extract qualified table names from SQL (e.g. schema.table). */
  private static final Pattern TABLE_RE =
      Pattern.compile("(?:FROM|JOIN)\\s+([a-zA-Z_]\\w*\\.[a-zA-Z_]\\w*)", Pattern.CASE_INSENSITIVE);
  /**
   * Additive score boost applied to a candidate for each relevant table it references.
   * Chosen small enough that it only breaks ties among already-similar candidates
   * and cannot promote semantically unrelated results past {@link #MIN_SIMILARITY}.
   */
  private static final float TABLE_BOOST = 0.05f;
  /**
   * Fraction of the top table's weight used as the cutoff for "relevant" tables in Phase 1
   * of {@link #query(String)}. Tables below this share are dropped from the boost set
   * to avoid amplifying incidental mentions.
   */
  private static final float TABLE_WEIGHT_THRESHOLD_RATIO = 0.2f;
  private static final long FLUSH_INTERVAL_SECONDS = 5;
  /**
   * Hard upper bound on deserialized entry count to protect against a corrupted/tampered
   * index file causing unbounded allocation on startup. 10M paragraphs is well beyond any
   * plausible deployment (~18 GB of vectors alone at 384 floats/entry).
   */
  private static final int MAX_INDEX_ENTRIES = 10_000_000;
  private static final String INDEX_FILE_NAME = "embedding_index.bin";
  /** Binary format version written by {@link #saveIndex()} and required by {@link #loadIndex()}. */
  private static final int INDEX_VERSION = 3;
  private static final String EXPECTED_MODEL_SHA256 =
      "6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452";

  private final Notebook notebook;
  private final Path indexPath;

  // ONNX inference
  private OrtEnvironment ortEnv;
  private OrtSession ortSession;
  private HuggingFaceTokenizer tokenizer;

  // In-memory vector index: docId -> (embedding, metadata)
  private final ConcurrentHashMap<String, IndexEntry> index = new ConcurrentHashMap<>();
  private final ReadWriteLock indexLock = new ReentrantReadWriteLock();
  private final AtomicBoolean indexDirty = new AtomicBoolean(false);
  private final ScheduledExecutorService flushScheduler =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "EmbeddingSearch-flush");
        t.setDaemon(true);
        return t;
      });

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
    restrictPermissions(indexPath);

    try {
      initModel();
    } catch (Exception e) {
      throw new IOException("Failed to initialize embedding model", e);
    }

    boolean indexLoaded = loadIndex();
    if (shouldBootstrapIndex(zConf, indexLoaded)) {
      notebook.addInitConsumer(this::addNoteIndex);
    }
    flushScheduler.scheduleWithFixedDelay(this::flushIfDirty,
        FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    this.notebook.addNotebookEventListener(this);
  }

  /** Package-private constructor for testing without DI. */
  EmbeddingSearch(ZeppelinConfiguration zConf, Notebook notebook, boolean skipModel)
      throws IOException {
    super("EmbeddingSearch");
    this.notebook = notebook;
    this.indexPath = Paths.get(zConf.getZeppelinSearchIndexPath());
    Files.createDirectories(indexPath);
    restrictPermissions(indexPath);
    if (!skipModel) {
      try {
        initModel();
      } catch (Exception e) {
        throw new IOException("Failed to initialize embedding model", e);
      }
    }
    boolean indexLoaded = loadIndex();
    if (shouldBootstrapIndex(zConf, indexLoaded)) {
      notebook.addInitConsumer(this::addNoteIndex);
    }
    flushScheduler.scheduleWithFixedDelay(this::flushIfDirty,
        FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    this.notebook.addNotebookEventListener(this);
  }

  private static void restrictPermissions(Path dir) {
    try {
      if (Files.getFileStore(dir).supportsFileAttributeView("posix")) {
        Files.setPosixFilePermissions(dir,
            PosixFilePermissions.fromString("rwx------"));
      }
    } catch (IOException e) {
      LOGGER.warn("Could not restrict permissions on {}", dir, e);
    }
    if (dir.toAbsolutePath().startsWith("/tmp")) {
      LOGGER.warn("zeppelin.search.index.path is under /tmp ({}); "
          + "paragraph text and output will be readable by other local users. "
          + "Consider setting it to a private directory.", dir);
    }
  }

  // ---- Model initialization ----

  private void initModel() throws OrtException, IOException {
    Path modelDir = indexPath.resolve("models").resolve(MODEL_NAME);
    Files.createDirectories(modelDir);

    Path modelFile = modelDir.resolve("model.onnx");
    Path tokenizerFile = modelDir.resolve("tokenizer.json");

    if (!Files.exists(modelFile) || !Files.exists(tokenizerFile)) {
      throw new IOException(
          "Embedding model not found at " + modelDir + ". "
              + "Run bin/install-search-model.sh before enabling semantic search.");
    }

    verifyModelSha256(modelFile);

    ortEnv = OrtEnvironment.getEnvironment();
    OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
    opts.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());
    ortSession = ortEnv.createSession(modelFile.toString(), opts);
    tokenizer = HuggingFaceTokenizer.newInstance(tokenizerFile);
    LOGGER.info("Embedding model loaded: {}, dim={}", MODEL_NAME, EMBEDDING_DIM);
  }

  private static void verifyModelSha256(Path modelFile) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] fileBytes = Files.readAllBytes(modelFile);
      byte[] hash = digest.digest(fileBytes);
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      String actual = sb.toString();
      if (!EXPECTED_MODEL_SHA256.equals(actual)) {
        throw new IOException("model.onnx SHA256 mismatch — expected "
            + EXPECTED_MODEL_SHA256 + " but got " + actual
            + ". Re-run bin/install-search-model.sh");
      }
      LOGGER.info("Model SHA256 verified: {}", modelFile);
    } catch (NoSuchAlgorithmException e) {
      LOGGER.warn("SHA-256 not available, skipping model integrity check", e);
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
      OnnxTensor idsTensor = null;
      OnnxTensor maskTensor = null;
      OnnxTensor typeTensor = null;
      try {
        idsTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(ids), shape);
        maskTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(mask), shape);
        typeTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(tokenTypeIds), shape);

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
        }
      } finally {
        if (idsTensor != null) {
          idsTensor.close();
        }
        if (maskTensor != null) {
          maskTensor.close();
        }
        if (typeTensor != null) {
          typeTensor.close();
        }
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

    // Collect tables from the top candidates, weighted by rank
    Map<String, Float> tableWeights = new HashMap<>();
    for (int i = 0; i < Math.min(scored.size(), MAX_RESULTS); i++) {
      IndexEntry entry = index.get(scored.get(i).getKey());
      if (entry != null && StringUtils.isNotBlank(entry.tables)) {
        float weight = 1.0f / (i + 1);
        for (String t : entry.tables.split(" ")) {
          tableWeights.merge(t, weight, Float::sum);
        }
      }
    }
    // Keep tables with weight >= TABLE_WEIGHT_THRESHOLD_RATIO of top table's weight
    Set<String> relevantTables = new HashSet<>();
    if (!tableWeights.isEmpty()) {
      float maxWeight = Collections.max(tableWeights.values());
      float threshold = maxWeight * TABLE_WEIGHT_THRESHOLD_RATIO;
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
        header.append("[TABLES]").append(entry.tables).append("\n");
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
      markDirty();
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
      markDirty();
    } catch (IOException e) {
      LOGGER.error("Failed to add paragraph {} of note {}", paragraphId, noteId, e);
    }
  }

  @Override
  public void updateNoteIndex(String noteId) {
    // Mirror LuceneSearch.updateNoteIndex: this event path is invoked for note-metadata
    // changes (rename, cron config, etc.) — paragraph edits come through the
    // add/updateParagraphIndex path. Re-embedding every paragraph here was pure waste for
    // cron changes and heavy even for renames. Just refresh the noteName field on existing
    // entries; the embedding slightly drifts (note name contributes to buildParagraphText)
    // but self-heals on the next paragraph touch.
    if (noteId == null) {
      return;
    }
    try {
      notebook.processNote(noteId, note -> {
        if (note == null) {
          return null;
        }
        String newName = note.getName();
        if (newName == null) {
          return null;
        }
        indexLock.writeLock().lock();
        try {
          boolean mutated = false;
          String notePrefix = noteId + "/";
          for (Map.Entry<String, IndexEntry> e : index.entrySet()) {
            String docId = e.getKey();
            if (!docId.equals(noteId) && !docId.startsWith(notePrefix)) {
              continue;
            }
            IndexEntry old = e.getValue();
            if (newName.equals(old.noteName)) {
              continue;
            }
            e.setValue(new IndexEntry(old.embedding, newName, old.text, old.title,
                old.tables, old.output));
            mutated = true;
          }
          if (mutated) {
            markDirty();
          }
        } finally {
          indexLock.writeLock().unlock();
        }
        return null;
      });
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
      markDirty();
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
      index.entrySet().removeIf(e ->
          e.getKey().equals(noteId) || e.getKey().startsWith(noteId + "/"));
    } finally {
      indexLock.writeLock().unlock();
    }
    markDirty();
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
    markDirty();
  }

  @Override
  @PreDestroy
  public void close() {
    super.close();
    flushScheduler.shutdown();
    flushIfDirty();
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

  private void markDirty() {
    indexDirty.set(true);
  }

  /**
   * Decide whether to register the initial-indexing consumer.
   *
   * @param zConf   Zeppelin configuration (for {@code isIndexRebuild})
   * @param loaded  whether {@link #loadIndex()} completed successfully
   * @return {@code true} if the index needs to be (re)built from notebooks. Triggers when
   *         config requests rebuild, the index file is missing, or it was present but
   *         failed to load (corrupt/partial). A failed load also deletes the bad file so
   *         the rebuilt index is written fresh.
   */
  private boolean shouldBootstrapIndex(ZeppelinConfiguration zConf, boolean loaded) {
    Path indexFile = indexPath.resolve(INDEX_FILE_NAME);
    boolean fileMissing = !Files.exists(indexFile);
    boolean corrupt = !loaded;
    if (corrupt && !fileMissing) {
      try {
        Files.deleteIfExists(indexFile);
        LOGGER.warn("Deleted corrupt embedding index file {}; will rebuild", indexFile);
      } catch (IOException e) {
        LOGGER.warn("Failed to delete corrupt embedding index file {}; will rebuild anyway",
            indexFile, e);
      }
    }
    return zConf.isIndexRebuild() || fileMissing || corrupt;
  }

  private void flushIfDirty() {
    if (indexDirty.compareAndSet(true, false)) {
      try {
        saveIndex();
      } catch (IOException e) {
        // Re-set dirty so the next scheduled tick retries the flush
        // instead of silently dropping the failed write until the next mutation.
        indexDirty.set(true);
        LOGGER.error("Failed to flush embedding index to disk; will retry on next tick", e);
      }
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
   * Format: [int:version=INDEX_VERSION][int:count] then for each entry:
   *   [utf:docId] [utf:noteName] [utf:text] [utf:title] [utf:tables] [utf:output] [float[384]:embedding]
   */
  private void saveIndex() throws IOException {
    Path file = indexPath.resolve(INDEX_FILE_NAME);
    Path tmpFile = indexPath.resolve(INDEX_FILE_NAME + ".tmp");

    // Serialize to buffer under lock
    byte[] data;
    indexLock.readLock().lock();
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (DataOutputStream out = new DataOutputStream(baos)) {
        out.writeInt(INDEX_VERSION);
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
      data = baos.toByteArray();
    } finally {
      indexLock.readLock().unlock();
    }

    // Write to disk outside lock
    Files.write(tmpFile, data);
    Files.move(tmpFile, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    // Restrict file permissions
    try {
      if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
        Files.setPosixFilePermissions(file,
            PosixFilePermissions.fromString("rw-------"));
      }
    } catch (IOException e) {
      LOGGER.warn("Could not restrict permissions on {}", file, e);
    }
  }

  /** Load index from disk if it exists. Supports v1/v2/v3 formats. */
  /**
   * Load the index from disk.
   *
   * @return {@code true} if the index loaded successfully (or file was absent);
   *         {@code false} if the file was present but failed to load or was corrupt,
   *         signalling the caller to trigger a bootstrap rebuild.
   */
  private boolean loadIndex() {
    Path file = indexPath.resolve("embedding_index.bin");
    if (!Files.exists(file)) {
      return true;
    }
    try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
      int version = in.readInt();
      if (version != INDEX_VERSION) {
        LOGGER.warn("Index file version {} does not match expected {}; treating as corrupt "
            + "and rebuilding", version, INDEX_VERSION);
        return false;
      }
      int count = in.readInt();
      LOGGER.info("Loading {} embedding index entries (v{}) from {}", count, version, file);
      if (count < 0 || count > MAX_INDEX_ENTRIES) {
        LOGGER.error("Index entry count {} exceeds sanity bound ({}), treating as corrupt",
            count, MAX_INDEX_ENTRIES);
        return false;
      }
      for (int i = 0; i < count; i++) {
        String docId = in.readUTF();
        String noteName = in.readUTF();
        String text = in.readUTF();
        String title = in.readUTF();
        String tables = in.readUTF();
        String output = in.readUTF();
        float[] emb = new float[EMBEDDING_DIM];
        for (int j = 0; j < EMBEDDING_DIM; j++) {
          emb[j] = in.readFloat();
        }
        index.put(docId, new IndexEntry(emb, noteName, text, title, tables, output));
      }
      LOGGER.info("Loaded {} entries into embedding index", index.size());
      return true;
    } catch (IOException e) {
      LOGGER.warn("Failed to load embedding index from {}; will rebuild on init", file, e);
      // Clear any partially-loaded state so we start from a clean slate on rebuild.
      index.clear();
      return false;
    }
  }
}

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
import java.io.File;
import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
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
 * <p>The embedding index is held in memory (float[][] + metadata) and persisted to disk
 * as one binary shard per note (see {@link #saveNoteShard(String)}), so editing a
 * paragraph in one note only rewrites that note's shard. For typical Zeppelin
 * deployments (< 50K paragraphs), brute-force cosine similarity completes in under 50ms.
 *
 * <p>Model files must be installed under {@code zeppelin.search.index.path} with
 * {@code bin/install-search-model.sh} before semantic search is enabled.
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
  /**
   * Truncation limit applied to the text field when an entry is written to the index file.
   * Distinct from {@link #MAX_TEXT_LENGTH}, which bounds in-memory processing before embedding.
   */
  private static final int MAX_PERSISTED_TEXT_LENGTH = 2000;
  /**
   * Truncation limit applied to the output field when an entry is written to the index file.
   */
  private static final int MAX_PERSISTED_OUTPUT_LENGTH = 1000;

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
   * Additive score boost when the query string appears literally in the indexed text.
   * Ensures exact keyword matches surface even when the embedding similarity is low
   * (e.g. searching "TETRIS" in SQL containing TETRIS_VIDEO_SINGLE_MEDIA).
   */
  private static final float KEYWORD_BOOST = 0.30f;
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
  /** Legacy single-file index, from before persistence was sharded by note (ZEPPELIN-6412). */
  private static final String LEGACY_INDEX_FILE_NAME = "embedding_index.bin";
  /** Binary format version of {@link #LEGACY_INDEX_FILE_NAME}, read only during migration. */
  private static final int INDEX_VERSION = 3;
  /** Subdirectory holding one binary shard per note. */
  private static final String NOTES_SHARD_DIR_NAME = "notes";
  private static final String SHARD_FILE_SUFFIX = ".bin";
  private static final String SHARD_TMP_SUFFIX = ".bin.tmp";
  /**
   * Staging directory migration writes every note's shard into before anything touches
   * {@link #NOTES_SHARD_DIR_NAME}. Published by a single atomic directory rename once every
   * note has been staged successfully, so a reader can never observe a half-migrated
   * {@code notes/} directory — see {@link #migrateLegacyIndexIfPresent()}.
   */
  private static final String MIGRATION_STAGING_DIR_NAME = "notes.migrating";
  /**
   * Binary format version written by {@link #saveNoteShard(String)} and required by
   * {@link #loadNoteShard(String, Path)}. Independent of {@link #INDEX_VERSION}: these are
   * different file formats at different paths, so conflating them would force migration
   * code to special-case "right version, wrong location".
   */
  private static final int SHARD_VERSION = 1;
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
  /** One lock per note, created on demand; never removed (see {@link #lockFor(String)}). */
  private final ConcurrentHashMap<String, ReentrantReadWriteLock> noteLocks =
      new ConcurrentHashMap<>();
  /** noteIds with in-memory changes not yet flushed to their shard file. */
  private final Set<String> dirtyNoteIds = ConcurrentHashMap.newKeySet();
  private final ScheduledExecutorService flushScheduler =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "EmbeddingSearch-flush");
        t.setDaemon(true);
        return t;
      });

  /** A single indexed document (paragraph or note name). */
  // TODO(ZEPPELIN-6413): Reduce in-memory duplication by keeping only {embedding, docId} here
  // and rehydrating text/title/output from Notebook.processNote() at query time. Needs a perf
  // comparison against the current in-memory path and a consistency story on LRU eviction.
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

    migrateLegacyIndexIfPresent();
    // Checked after migration (so a corrupt legacy file that migration deleted counts as
    // "nothing persisted") but before loadAllShards() (so one individually-corrupt shard
    // doesn't retroactively make this look like an empty-index deployment).
    boolean hadPersistedIndex = shardsDirHasAnyShard();
    Set<String> noteIdsToRebuild = loadAllShards();
    registerInitialRebuild(zConf, hadPersistedIndex, noteIdsToRebuild);
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
    migrateLegacyIndexIfPresent();
    boolean hadPersistedIndex = shardsDirHasAnyShard();
    Set<String> noteIdsToRebuild = loadAllShards();
    registerInitialRebuild(zConf, hadPersistedIndex, noteIdsToRebuild);
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

  /** Restrict a regular file (as opposed to a directory, see {@link #restrictPermissions}) to
   *  owner-only read/write, mirroring the permissions already applied to {@link #indexPath}. */
  private static void restrictFilePermissions(Path file) {
    try {
      if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
        Files.setPosixFilePermissions(file,
            PosixFilePermissions.fromString("rw-------"));
      }
    } catch (IOException e) {
      LOGGER.warn("Could not restrict permissions on {}", file, e);
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

  /**
   * Wrap occurrences of each query word in {@code <B>} tags (case-insensitive)
   * to match Lucene's highlighting convention.
   */
  static String highlightTerms(String text, String queryStr) {
    if (StringUtils.isBlank(text) || StringUtils.isBlank(queryStr)) {
      return text;
    }
    String[] words = queryStr.split("\\s+");
    for (String word : words) {
      if (word.isEmpty()) {
        continue;
      }
      String escaped = Pattern.quote(word);
      text = text.replaceAll("(?i)(" + escaped + ")", "<B>$1</B>");
    }
    return text;
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
  public List<Map<String, String>> query(String queryStr, Predicate<String> readable) {
    if (StringUtils.isBlank(queryStr) || index.isEmpty()) {
      return Collections.emptyList();
    }
    Map<String, Boolean> readableNotes = new HashMap<>();

    float[] queryEmbedding = embed(queryStr);
    String queryLower = queryStr.toLowerCase(Locale.ROOT);

    // Phase 1: find top-N results and discover relevant tables
    // No lock is taken here: IndexEntry is immutable (all fields final) and
    // ConcurrentHashMap gives weakly-consistent iteration — put()/remove() swap
    // references atomically per key, so a concurrent write is seen either fully
    // or not at all, never torn. Per-note locks (see lockFor) only need to protect
    // the save path's per-note snapshot, not this read.
    List<Map.Entry<String, Float>> scored = new ArrayList<>();
    for (Map.Entry<String, IndexEntry> entry : index.entrySet()) {
      // Dropping the entries here keeps them out of the table weights below and out of
      // the cutoff, so the caller is served its own top results and not what is left of
      // everyone's top results.
      if (!readableNotes.computeIfAbsent(noteIdOf(entry.getKey()), readable::test)) {
        continue;
      }
      float sim = cosineSimilarity(queryEmbedding, entry.getValue().embedding);
      IndexEntry ie = entry.getValue();
      if (ie.text != null && ie.text.toLowerCase(Locale.ROOT).contains(queryLower)) {
        sim += KEYWORD_BOOST;
      }
      scored.add(Map.entry(entry.getKey(), sim));
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
      String title = entry.title != null ? entry.title : "";
      String tables = entry.tables != null ? entry.tables : "";
      String output = "";
      if (StringUtils.isNotBlank(entry.output)) {
        output = entry.output;
        if (output.length() > 300) {
          output = output.substring(0, 300);
        }
      }
      String snippet = highlightTerms(entry.text, queryStr);
      String highlightedTitle = highlightTerms(title, queryStr);
      candidates.add(Map.entry(ImmutableMap.<String, String>builder()
          .put("id", docId)
          .put("name", entry.noteName != null ? entry.noteName : "")
          .put("snippet", snippet)
          .put("text", entry.text)
          .put("header", highlightedTitle)
          .put("title", highlightedTitle)
          .put("tables", tables)
          .put("output", output)
          .build(), sim));
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
      markDirty(noteId);
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
      markDirty(noteId);
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
        lockFor(noteId).writeLock().lock();
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
            markDirty(noteId);
          }
        } finally {
          lockFor(noteId).writeLock().unlock();
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
      markDirty(noteId);
    } catch (IOException e) {
      LOGGER.error("Failed to update paragraph {} of note {}", paragraphId, noteId, e);
    }
  }

  @Override
  public void deleteNoteIndex(String noteId) {
    if (noteId == null) {
      return;
    }
    lockFor(noteId).writeLock().lock();
    try {
      index.entrySet().removeIf(e ->
          e.getKey().equals(noteId) || e.getKey().startsWith(noteId + "/"));
    } finally {
      lockFor(noteId).writeLock().unlock();
    }
    // Don't delete the shard file here. saveNoteShard() already deletes a note's shard
    // when it finds no remaining entries for it, and it does that under this same note's
    // lock for its *entire* save (snapshot + write) — so routing the delete through the
    // normal dirty/flush path (instead of racing an out-of-band file delete against an
    // in-flight save) is what keeps a concurrent flush from resurrecting a deleted note's
    // shard with a stale pre-delete snapshot. A failed delete-on-flush is retried the same
    // way any other failed flush is: flushIfDirty() re-marks the note dirty on IOException.
    markDirty(noteId);
  }

  @Override
  public void deleteParagraphIndex(String noteId, String paragraphId) {
    if (noteId == null) {
      return;
    }
    String docId = paragraphId != null
        ? String.join("/", noteId, PARAGRAPH, paragraphId)
        : noteId;
    lockFor(noteId).writeLock().lock();
    try {
      index.remove(docId);
    } finally {
      lockFor(noteId).writeLock().unlock();
    }
    markDirty(noteId);
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

  /** Lock guarding {@code index} entries belonging to {@code noteId}. Created on first use
   *  and never removed — noteIds aren't reused, and removing an entry while another thread
   *  might hold a reference to the same lock instance (from a concurrent computeIfAbsent)
   *  would risk two threads ending up on two different lock objects for the same note. */
  private ReentrantReadWriteLock lockFor(String noteId) {
    return noteLocks.computeIfAbsent(noteId, k -> new ReentrantReadWriteLock());
  }

  private void markDirty(String noteId) {
    dirtyNoteIds.add(noteId);
  }

  /**
   * Decide whether to register the initial-indexing consumer for an operator-requested
   * full rebuild. Per-note rebuilds triggered by a corrupt/missing shard are handled
   * directly in {@link #loadAllShards()}, and a from-scratch bootstrap (no persisted index
   * at all) is handled by the {@code hadPersistedIndex} check at the constructors' call
   * sites — neither goes through this method.
   *
   * @param zConf Zeppelin configuration (for {@code isIndexRebuild})
   * @return {@code true} if {@code zeppelin.search.index.rebuild} requests a full rebuild
   */
  private boolean shouldBootstrapIndex(ZeppelinConfiguration zConf) {
    return zConf.isIndexRebuild();
  }

  /** Register either a full bootstrap or the smallest per-note repair required at startup. */
  private void registerInitialRebuild(ZeppelinConfiguration zConf, boolean hadPersistedIndex,
                                      Set<String> noteIdsToRebuild) {
    if (shouldBootstrapIndex(zConf) || !hadPersistedIndex) {
      notebook.addInitConsumer(this::addNoteIndex);
    } else if (!noteIdsToRebuild.isEmpty()) {
      notebook.addInitConsumer(noteId -> {
        if (noteIdsToRebuild.contains(noteId)) {
          addNoteIndex(noteId);
        }
      });
    }
  }

  /**
   * @return {@code true} if the notes shard directory exists and holds at least one shard
   *         file. Used right after migration (and before {@link #loadAllShards()} can delete
   *         an individually-corrupt one) to tell "nothing has ever been persisted" — which
   *         should bootstrap the whole notebook, same as the old single-file code did when
   *         its one file was simply missing — apart from "something was persisted and a
   *         piece of it happens to be corrupt," which only needs a per-note rebuild.
   */
  private boolean shardsDirHasAnyShard() {
    Path notesDir = indexPath.resolve(NOTES_SHARD_DIR_NAME);
    if (!Files.exists(notesDir)) {
      return false;
    }
    File[] shardFiles = notesDir.toFile().listFiles((d, name) -> name.endsWith(SHARD_FILE_SUFFIX));
    return shardFiles != null && shardFiles.length > 0;
  }

  /**
   * Flush dirty note shards serially. The scheduled task and {@link #close()} can invoke this
   * method concurrently; without synchronization both callers could drain the same weakly
   * consistent dirty-set iterator and write/move the same {@code .bin.tmp} file at once.
   */
  private synchronized void flushIfDirty() {
    Set<String> toFlush = new HashSet<>();
    Iterator<String> it = dirtyNoteIds.iterator();
    while (it.hasNext()) {
      toFlush.add(it.next());
      it.remove();
    }
    for (String noteId : toFlush) {
      try {
        saveNoteShard(noteId);
      } catch (IOException e) {
        // Re-mark dirty so the next scheduled tick retries the flush
        // instead of silently dropping the failed write until the next mutation.
        dirtyNoteIds.add(noteId);
        LOGGER.error("Failed to flush embedding shard for note {}; will retry on next tick",
            noteId, e);
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

    lockFor(noteId).writeLock().lock();
    try {
      index.put(docId, new IndexEntry(emb, noteName, pText, title, tables, output));
    } finally {
      lockFor(noteId).writeLock().unlock();
    }
  }

  static String formatId(String noteId, Paragraph p) {
    if (p != null) {
      return String.join("/", noteId, PARAGRAPH, p.getId());
    }
    return noteId;
  }

  // ---- Persistence ----

  private Path shardFile(String noteId) {
    return indexPath.resolve(NOTES_SHARD_DIR_NAME).resolve(noteId + SHARD_FILE_SUFFIX);
  }

  /**
   * Save one note's entries to its own binary shard.
   * Format: [int:shardVersion=SHARD_VERSION][utf:noteId][int:count] then for each entry:
   *   [utf:docId] [utf:noteName] [utf:text] [utf:title] [utf:tables] [utf:output]
   *   [float[384]:embedding]
   *
   * <p>A single paragraph edit only rewrites the shard of the note it belongs to —
   * every other note's shard is untouched (ZEPPELIN-6412).
   *
   * <p>The note's read lock is held for the <em>entire</em> save — snapshot and disk write
   * alike — not just the snapshot. Releasing it in between would let a concurrent
   * {@code deleteNoteIndex}/mutator run after the snapshot was taken but before the file
   * write landed, so the write could recreate a shard the delete had just removed from
   * memory (and expected removed on disk) with stale, pre-delete content. Holding the lock
   * the whole time forces every mutator for this note to wait until the save is fully done,
   * so a save always reflects a state that was current at some point and is never undone
   * by a mutation it couldn't have known about.
   */
  private void saveNoteShard(String noteId) throws IOException {
    lockFor(noteId).readLock().lock();
    try {
      List<Map.Entry<String, IndexEntry>> entries = index.entrySet().stream()
          .filter(e -> e.getKey().equals(noteId) || e.getKey().startsWith(noteId + "/"))
          .collect(Collectors.toList());

      Path file = shardFile(noteId);
      if (entries.isEmpty()) {
        // No entries left for this note (e.g. deleteNoteIndex ran first) — remove the shard
        // rather than leave an empty file. If this delete itself fails, the IOException
        // propagates out and flushIfDirty() re-marks the note dirty to retry.
        Files.deleteIfExists(file);
        return;
      }

      byte[] data = serializeShard(noteId, entries);

      Files.createDirectories(indexPath.resolve(NOTES_SHARD_DIR_NAME));
      Path tmpFile = indexPath.resolve(NOTES_SHARD_DIR_NAME).resolve(noteId + SHARD_TMP_SUFFIX);
      Files.write(tmpFile, data);
      Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
      restrictFilePermissions(file);
    } finally {
      lockFor(noteId).readLock().unlock();
    }
  }

  private static byte[] serializeShard(String noteId, List<Map.Entry<String, IndexEntry>> entries)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(baos)) {
      out.writeInt(SHARD_VERSION);
      out.writeUTF(noteId);
      out.writeInt(entries.size());
      for (Map.Entry<String, IndexEntry> e : entries) {
        out.writeUTF(e.getKey());
        out.writeUTF(e.getValue().noteName != null ? e.getValue().noteName : "");
        String text = e.getValue().text != null ? e.getValue().text : "";
        if (text.length() > MAX_PERSISTED_TEXT_LENGTH) {
          text = text.substring(0, MAX_PERSISTED_TEXT_LENGTH);
        }
        out.writeUTF(text);
        out.writeUTF(e.getValue().title != null ? e.getValue().title : "");
        out.writeUTF(e.getValue().tables != null ? e.getValue().tables : "");
        String output = e.getValue().output != null ? e.getValue().output : "";
        if (output.length() > MAX_PERSISTED_OUTPUT_LENGTH) {
          output = output.substring(0, MAX_PERSISTED_OUTPUT_LENGTH);
        }
        out.writeUTF(output);
        for (float v : e.getValue().embedding) {
          out.writeFloat(v);
        }
      }
    }
    return baos.toByteArray();
  }

  /**
   * Load every note's shard from {@code {indexPath}/notes/}. There's no manifest file —
   * like {@code LocalRecoveryStorage}, the shard directory is scanned directly and each
   * note's id is recovered from its filename.
   *
   * @return IDs of notes whose shard is corrupt or missing. A corrupt shard does not fail the
   *         whole load: it's deleted and returned for per-note rebuilding. Likewise, comparing
   *         the persisted shard IDs with {@link Notebook#getNotesInfo()} lets a single vanished
   *         shard self-heal without forcing a full rebuild.
   */
  private Set<String> loadAllShards() {
    Set<String> noteIdsToRebuild = new HashSet<>();
    Set<String> currentNoteIds = notebook.getNotesInfo().stream()
        .map(NoteInfo::getId)
        .collect(Collectors.toSet());
    Path notesDir = indexPath.resolve(NOTES_SHARD_DIR_NAME);
    if (!Files.exists(notesDir)) {
      return noteIdsToRebuild;
    }
    File[] shardFiles = notesDir.toFile().listFiles((d, name) -> name.endsWith(SHARD_FILE_SUFFIX));
    if (shardFiles == null) {
      return noteIdsToRebuild;
    }
    Set<String> persistedNoteIds = new HashSet<>();
    for (File f : shardFiles) {
      String noteId = f.getName().substring(0, f.getName().length() - SHARD_FILE_SUFFIX.length());
      if (!currentNoteIds.contains(noteId)) {
        LOGGER.info("Deleting orphan embedding shard {} for note {} that no longer exists",
            f, noteId);
        try {
          Files.deleteIfExists(f.toPath());
        } catch (IOException e) {
          // Do not load stale entries into memory. Route the failed file deletion through
          // the normal dirty flush path so the scheduler retries it after startup.
          markDirty(noteId);
          LOGGER.warn("Failed to delete orphan shard {}; will retry on next flush", f, e);
        }
        continue;
      }
      persistedNoteIds.add(noteId);
      if (!loadNoteShard(noteId, f.toPath())) {
        LOGGER.warn("Corrupt embedding shard for note {} ({}); deleting and rebuilding "
            + "just that note", noteId, f);
        try {
          Files.deleteIfExists(f.toPath());
        } catch (IOException e) {
          LOGGER.warn("Failed to delete corrupt shard {}; will attempt rebuild anyway", f, e);
        }
        noteIdsToRebuild.add(noteId);
      }
    }

    for (String noteId : currentNoteIds) {
      if (!persistedNoteIds.contains(noteId)) {
        LOGGER.warn("Embedding shard for note {} is missing; rebuilding just that note",
            noteId);
        noteIdsToRebuild.add(noteId);
      }
    }
    return noteIdsToRebuild;
  }

  /**
   * Load a single note's shard into {@link #index}. Returns {@code false} on any
   * corruption (bad version, filename/content noteId mismatch, bad count, an entry whose
   * docId doesn't belong to this note, or truncated/malformed data partway through),
   * signalling the caller to discard the file and rebuild just this note.
   *
   * <p>Every entry is read into a local map first; {@link #index} is only touched once, at
   * the end, via {@link #commitNoteEntries}, and only if every entry the header promised
   * was read and validated successfully. A shard that fails partway through — even after
   * successfully reading one or more entries — contributes nothing: there's no path where
   * some of a failed shard's entries end up live in {@link #index} while others don't.
   */
  private boolean loadNoteShard(String noteId, Path file) {
    Map<String, IndexEntry> loaded = new HashMap<>();
    try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
      int version = in.readInt();
      if (version != SHARD_VERSION) {
        LOGGER.warn("Shard {} version {} does not match expected {}", file, version,
            SHARD_VERSION);
        return false;
      }
      String headerNoteId = in.readUTF();
      if (!headerNoteId.equals(noteId)) {
        LOGGER.warn("Shard {} noteId {} does not match filename-derived noteId {}",
            file, headerNoteId, noteId);
        return false;
      }
      int count = in.readInt();
      if (count < 0 || count > MAX_INDEX_ENTRIES) {
        LOGGER.error("Shard {} entry count {} exceeds sanity bound ({})", file, count,
            MAX_INDEX_ENTRIES);
        return false;
      }
      for (int i = 0; i < count; i++) {
        String docId = in.readUTF();
        if (!docId.equals(noteId) && !docId.startsWith(noteId + "/")) {
          LOGGER.warn("Shard {} entry {} has a docId that doesn't belong to note {}; "
              + "treating the whole shard as corrupt", file, docId, noteId);
          return false;
        }
        String noteName = in.readUTF();
        String text = in.readUTF();
        String title = in.readUTF();
        String tables = in.readUTF();
        String output = in.readUTF();
        float[] emb = new float[EMBEDDING_DIM];
        for (int j = 0; j < EMBEDDING_DIM; j++) {
          emb[j] = in.readFloat();
        }
        loaded.put(docId, new IndexEntry(emb, noteName, text, title, tables, output));
      }
    } catch (IOException e) {
      LOGGER.warn("Failed to load embedding shard {}; discarding {} entry/entries read "
          + "before the failure", file, loaded.size(), e);
      return false;
    }
    commitNoteEntries(noteId, loaded);
    return true;
  }

  /** Atomically replace this note's entries in {@link #index} with exactly {@code entries} —
   *  the commit point a successful {@link #loadNoteShard} uses, so a shard's entries become
   *  visible all at once or not at all. */
  private void commitNoteEntries(String noteId, Map<String, IndexEntry> entries) {
    lockFor(noteId).writeLock().lock();
    try {
      index.entrySet().removeIf(e ->
          e.getKey().equals(noteId) || e.getKey().startsWith(noteId + "/"));
      index.putAll(entries);
    } finally {
      lockFor(noteId).writeLock().unlock();
    }
  }

  /**
   * One-time migration from the pre-ZEPPELIN-6412 single-file index. If
   * {@code embedding_index.bin} is present, load it with the legacy format, stage every
   * note's shard in {@link #MIGRATION_STAGING_DIR_NAME}, and only if every single one of
   * them is staged successfully, publish the whole staging directory as {@link
   * #NOTES_SHARD_DIR_NAME} with one atomic rename — then delete the legacy file so this
   * never runs again.
   *
   * <p>This is all-or-nothing on purpose: writing shards straight into the real {@code
   * notes/} directory one at a time would let a mid-migration failure (disk full, a bad
   * noteId, anything) leave some notes migrated and others not, and {@link
   * #loadAllShards()} has no way to tell that apart from a complete, correct index — it
   * would just load the partial set and serve it. Staging first and publishing with a
   * single rename means a reader only ever sees "no {@code notes/} directory yet" or "a
   * fully-migrated one," never something in between. If staging fails, the legacy file is
   * left in place (so the next restart retries from scratch; shard writes are keyed by
   * noteId and idempotent, so repeating this is safe) and the staging directory is cleared.
   */
  private void migrateLegacyIndexIfPresent() {
    Path legacyFile = indexPath.resolve(LEGACY_INDEX_FILE_NAME);
    if (!Files.exists(legacyFile)) {
      return;
    }
    // A published shard set is the newer format and therefore the source of truth. This state
    // can legitimately occur when a previous migration published notes/ successfully but failed
    // to delete the legacy file. Re-running migration here would replace potentially newer shards
    // with the stale legacy snapshot on every restart.
    if (shardsDirHasAnyShard()) {
      LOGGER.info("Per-note embedding shards already exist; ignoring and deleting stale legacy "
          + "index {}", legacyFile);
      deleteLegacyFile(legacyFile);
      return;
    }
    LOGGER.info("Migrating legacy single-file embedding index {} to per-note shards",
        legacyFile);
    if (!loadLegacyIndex(legacyFile)) {
      LOGGER.warn("Legacy index {} failed to load during migration; deleting and "
          + "bootstrapping a fresh index", legacyFile);
      deleteLegacyFile(legacyFile);
      index.clear();
      return;
    }

    Path stagingDir = indexPath.resolve(MIGRATION_STAGING_DIR_NAME);
    Path notesDir = indexPath.resolve(NOTES_SHARD_DIR_NAME);
    try {
      // Clear any half-written staging debris left by a migration attempt that crashed
      // (as opposed to failing cleanly through the catch block below) before this one.
      deleteDirectoryRecursively(stagingDir);
      Files.createDirectories(stagingDir);

      Set<String> noteIds = index.keySet().stream()
          .map(SearchService::noteIdOf)
          .collect(Collectors.toSet());
      for (String noteId : noteIds) {
        writeShardToDir(stagingDir, noteId);
      }

      // Publish: this is the only point that touches the real notes/ directory, and it's a
      // single rename — so a reader can never observe a partially-migrated one.
      if (Files.exists(notesDir)) {
        // Not expected (migration always runs before loadAllShards() creates/uses notes/),
        // but don't let a stray pre-existing directory fail the whole migration.
        deleteDirectoryRecursively(notesDir);
      }
      Files.move(stagingDir, notesDir, StandardCopyOption.ATOMIC_MOVE);

      LOGGER.info("Migrated {} notes ({} entries) to per-note shards", noteIds.size(),
          index.size());
    } catch (IOException e) {
      LOGGER.error("Failed to stage per-note shards during migration; legacy index kept "
          + "for retry on next restart", e);
      try {
        deleteDirectoryRecursively(stagingDir);
      } catch (IOException cleanup) {
        LOGGER.warn("Failed to clean up migration staging directory {}", stagingDir, cleanup);
      }
      index.clear();
      return;
    }
    // index.clear() here (success path only) so loadAllShards() is the one source of truth
    // for what ends up in memory, instead of trusting this migration pass's copy.
    index.clear();
    deleteLegacyFile(legacyFile);
  }

  /** Write one note's entries, if any, as a shard file directly under {@code dir} — used by
   *  migration to stage into {@link #MIGRATION_STAGING_DIR_NAME}, never the real shard
   *  directory. Unlike {@link #saveNoteShard}, there's no existing file to delete when a
   *  note has no entries: the staging directory starts empty, so there's simply nothing to
   *  write for it. No note-level lock is needed here — migration runs synchronously in the
   *  constructor, before {@code notebook.addNotebookEventListener}/{@code addInitConsumer}
   *  are registered, so nothing else can be mutating {@link #index} concurrently yet. */
  private void writeShardToDir(Path dir, String noteId) throws IOException {
    List<Map.Entry<String, IndexEntry>> entries = index.entrySet().stream()
        .filter(e -> e.getKey().equals(noteId) || e.getKey().startsWith(noteId + "/"))
        .collect(Collectors.toList());
    if (entries.isEmpty()) {
      return;
    }
    byte[] data = serializeShard(noteId, entries);
    Path file = dir.resolve(noteId + SHARD_FILE_SUFFIX);
    Files.write(file, data);
    restrictFilePermissions(file);
  }

  /** Recursively delete {@code dir} if it exists. Used to clear migration staging debris —
   *  never called on {@link #NOTES_SHARD_DIR_NAME} except right before it's about to be
   *  replaced by a freshly-published staging directory. */
  private static void deleteDirectoryRecursively(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return;
    }
    Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        // Propagate the first failure so migration cannot publish a directory containing
        // debris from an earlier attempt.
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path directory, IOException error)
          throws IOException {
        if (error != null) {
          throw error;
        }
        Files.delete(directory);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private static void deleteLegacyFile(Path legacyFile) {
    try {
      Files.delete(legacyFile);
    } catch (IOException e) {
      LOGGER.warn("Failed to delete legacy embedding index {} after migration", legacyFile, e);
    }
  }

  /**
   * Read the legacy (pre-ZEPPELIN-6412) single-file index format. Used only by
   * {@link #migrateLegacyIndexIfPresent()} — once that file is deleted, this never runs
   * again.
   *
   * @return {@code true} if the file loaded successfully; {@code false} if corrupt.
   */
  private boolean loadLegacyIndex(Path file) {
    try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
      int version = in.readInt();
      if (version != INDEX_VERSION) {
        LOGGER.warn("Legacy index file version {} does not match expected {}; treating as "
            + "corrupt", version, INDEX_VERSION);
        return false;
      }
      int count = in.readInt();
      LOGGER.info("Loading {} embedding index entries (v{}) from legacy file {}", count,
          version, file);
      if (count < 0 || count > MAX_INDEX_ENTRIES) {
        LOGGER.error("Legacy index entry count {} exceeds sanity bound ({}), treating as "
            + "corrupt", count, MAX_INDEX_ENTRIES);
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
      LOGGER.info("Loaded {} entries from legacy index", index.size());
      return true;
    } catch (IOException e) {
      LOGGER.warn("Failed to load legacy embedding index from {}", file, e);
      index.clear();
      return false;
    }
  }
}

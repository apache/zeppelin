# ZEPPELIN-6411: Semantic Search for Notebooks using Sentence Embeddings

## Summary

Add `EmbeddingSearch` — a new `SearchService` implementation that enables natural language
search across Zeppelin notebooks using ONNX-based sentence embeddings. This is a drop-in
replacement for `LuceneSearch` that understands meaning, not just keywords.

**Example**: Searching "yesterday's spending" finds paragraphs containing
`SELECT sum(cost) FROM click_funnel WHERE date = current_date - interval '1' day`
— something keyword search cannot do.

## Motivation

Zeppelin's current search (`LuceneSearch`) uses keyword-based full-text search with
Lucene's `StandardAnalyzer`. This has several limitations for notebook search:

1. **No semantic understanding** — "yesterday's spend" won't find `current_date - 1`
2. **Poor SQL tokenization** — `StandardAnalyzer` breaks on underscores and dots in
   table names like `eq_analytics_prod.click_funnel_raw`
3. **No output indexing** — query results (table data, text output) are not searchable
4. **Exact match only** — users must guess the exact terms used in notebooks

For teams with hundreds or thousands of notebooks (common in data/analytics teams),
finding the right query becomes a significant productivity bottleneck.

## Architecture

```
                    SearchService (abstract)
                    ├── LuceneSearch        (existing, keyword-based)
                    ├── EmbeddingSearch     (new, semantic)
                    └── NoSearchService     (existing, no-op)

┌─────────────────────────────────────────────────────────────┐
│  EmbeddingSearch                                            │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ HuggingFace  │  │ ONNX Runtime │  │ In-Memory Index  │  │
│  │ Tokenizer    │→ │ Inference    │→ │ float[][] + meta │  │
│  │ (DJL)        │  │ (CPU)        │  │ ConcurrentHashMap│  │
│  └──────────────┘  └──────────────┘  └────────┬─────────┘  │
│                                                │            │
│  Query: embed → brute-force cosine sim → top-20│            │
│  Index: embed paragraph text+title+output      │            │
│                                                ▼            │
│                                    embedding_index.bin      │
│                                    (persisted to disk)      │
└─────────────────────────────────────────────────────────────┘
```

### Model

- **all-MiniLM-L6-v2**: 384-dimensional sentence embeddings
- 22MB ONNX model (86MB fp32, quantized version available)
- Downloaded on first use to `zeppelin.search.index.path/models/`
- Runs on CPU via ONNX Runtime (~5ms per paragraph)

### Index

- In-memory `ConcurrentHashMap<String, IndexEntry>` with `ReadWriteLock`
- Each entry: 384 floats (1.5KB) + metadata strings
- 10K paragraphs ≈ 15MB RAM, 50K paragraphs ≈ 75MB RAM
- Persisted as single binary file (`embedding_index.bin`)
- Brute-force cosine similarity: < 50ms for 50K paragraphs

### What gets indexed (vs. LuceneSearch)

| Content | LuceneSearch | EmbeddingSearch |
|---------|:---:|:---:|
| Paragraph text | ✓ | ✓ |
| Paragraph title | ✓ | ✓ |
| Notebook name | ✓ | ✓ |
| Paragraph output (TABLE, TEXT) | ✗ | ✓ |
| Interpreter prefix stripped | ✗ | ✓ |

## Configuration

Disabled by default. Enable with a single property:

```properties
# In zeppelin-site.xml or zeppelin-env.sh
zeppelin.search.semantic.enable = true
```

Requires `zeppelin.search.enable = true` (already the default).

### Configuration matrix

| `search.enable` | `search.semantic.enable` | Result |
|:---:|:---:|---|
| true | false (default) | LuceneSearch (existing behavior) |
| true | true | EmbeddingSearch (semantic) |
| false | any | NoSearchService |

## Changes

### New files
- `zeppelin-zengine/.../search/EmbeddingSearch.java` — Core implementation
- `zeppelin-zengine/.../search/EmbeddingSearchTest.java` — Tests (gated behind env var)

### Modified files
- `zeppelin-zengine/pom.xml` — Add `onnxruntime` and `djl-tokenizers` dependencies
- `zeppelin-zengine/.../conf/ZeppelinConfiguration.java` — Add `ZEPPELIN_SEARCH_SEMANTIC_ENABLE`
- `zeppelin-server/.../server/ZeppelinServer.java` — Wire `EmbeddingSearch` based on config

### Dependencies added
- `com.microsoft.onnxruntime:onnxruntime:1.18.0` (~50MB, Apache 2.0 compatible)
- `ai.djl.huggingface:tokenizers:0.28.0` (~2MB, Apache 2.0)

## Design Decisions

### Why ONNX Runtime instead of a Java ML library?

ONNX Runtime is the standard inference engine for transformer models. It supports
the exact same model files used by Python (HuggingFace, ChromaDB, etc.), ensuring
embedding compatibility. DJL and other Java ML libraries either don't support
sentence-transformers or require significantly more code.

### Why brute-force instead of HNSW/ANN?

For Zeppelin's scale (typically < 50K paragraphs), brute-force cosine similarity
on normalized vectors is:
- **Fast enough**: < 50ms for 50K entries (384-dim dot product)
- **Exact**: No approximation error
- **Zero complexity**: No graph construction, no tuning parameters
- **Tiny memory**: Just a flat float array

HNSW would add ~3x memory overhead and code complexity for negligible latency gain.

### Why download model on first use instead of bundling?

The ONNX model is 86MB (fp32). Bundling it would bloat the Zeppelin distribution.
Downloading on first use keeps the distribution lean and allows users to swap models.

### Why not use Lucene's vector search (since 9.0)?

Zeppelin uses Lucene 8.7.0. Upgrading to 9.x is a separate, larger effort.
Even with Lucene 9.x vector search, you'd still need the ONNX model for embedding
generation — so the dependency footprint is similar.

## Testing

```bash
# Run embedding search tests (requires model download, ~86MB first time)
ZEPPELIN_EMBEDDING_TEST=true mvn test -pl zeppelin-zengine \
  -Dtest=EmbeddingSearchTest

# Run existing Lucene tests (should still pass, no changes)
mvn test -pl zeppelin-zengine -Dtest=LuceneSearchTest
```

### Key test: `semanticSearchFindsRelatedConcepts`

This test validates the core value proposition — that a natural language query
("yesterday's spending") correctly ranks a SQL spend query above an unrelated
user count query, even though neither contains the word "spending" or "yesterday".

## Future Work

- [ ] Quantized model support (22MB INT8 vs 86MB FP32)
- [ ] Hybrid search: combine embedding similarity with keyword matching
- [ ] Frontend: show similarity scores in search results
- [ ] Configurable model path for air-gapped environments
- [ ] Batch embedding during initial index rebuild

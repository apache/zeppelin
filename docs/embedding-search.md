# ZEPPELIN-6411: Semantic Search for Notebooks using Sentence Embeddings

## Summary

Add `EmbeddingSearch` — a new `SearchService` implementation that enables natural language
search across Zeppelin notebooks using ONNX-based sentence embeddings. This is a drop-in
replacement for `LuceneSearch` that understands meaning, not just keywords.

**Example**: Searching "yesterday's spending" finds paragraphs containing
`SELECT sum(cost) FROM analytics.daily_sales WHERE date = current_date - interval '1' day`
— something keyword search cannot do (returns 0 results with LuceneSearch).

## Motivation

Zeppelin's current search (`LuceneSearch`) uses keyword-based full-text search with
Lucene's `StandardAnalyzer`. This has several limitations for notebook search:

1. **No semantic understanding** — "yesterday's spend" won't find `current_date - 1`
2. **Poor SQL tokenization** — `StandardAnalyzer` breaks on underscores and dots in
   table names like `analytics_db.daily_sales`
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
│  Two-phase query:                              │            │
│  1. Embed query → cosine sim → find tables     │            │
│  2. Re-rank with table boost → top-20          │            │
│                                                ▼            │
│  Index: text + title + output + tables   embedding_index.bin│
│         (persisted to disk, versioned)                      │
└─────────────────────────────────────────────────────────────┘
```

### Model

- **all-MiniLM-L6-v2**: 384-dimensional sentence embeddings
- 86MB ONNX model (quantized version available at 22MB)
- Downloaded on first use to `zeppelin.search.index.path/models/`
- Runs on CPU via ONNX Runtime (~5ms per paragraph)

### Index

- In-memory `ConcurrentHashMap<String, IndexEntry>` with `ReadWriteLock`
- Each entry stores: embedding (384 floats), notebook name, paragraph text,
  title, extracted SQL table names, and paragraph output
- 10K paragraphs ≈ 15MB RAM, 50K paragraphs ≈ 75MB RAM
- Persisted as versioned binary file (`embedding_index.bin`, currently v3)
- Brute-force cosine similarity: < 50ms for 50K paragraphs

### What gets indexed (vs. LuceneSearch)

| Content | LuceneSearch | EmbeddingSearch |
|---------|:---:|:---:|
| Paragraph text | ✓ | ✓ |
| Paragraph title | ✓ | ✓ |
| Notebook name | ✓ | ✓ (in embedding context) |
| Paragraph output (TABLE, TEXT) | ✗ | ✓ |
| SQL table names (FROM/JOIN) | ✗ | ✓ (extracted + boosted) |
| Interpreter prefix stripped | ✗ | ✓ |

### Two-Phase Search

1. **Phase 1 — Table Discovery**: Run cosine similarity, collect SQL table names
   from top-20 results weighted by rank
2. **Phase 2 — Table Boost**: Re-score results, boosting paragraphs that reference
   the discovered tables (+0.05 per matching table)

This helps queries like "click funnel analysis" surface all paragraphs that query
the same tables, even if their SQL text is very different.

## Configuration

Disabled by default. Enable with a single property:

```xml
<!-- In zeppelin-site.xml -->
<property>
  <name>zeppelin.search.semantic.enable</name>
  <value>true</value>
</property>
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
- `zeppelin-zengine/.../search/EmbeddingSearch.java` — Core implementation (~700 lines)
- `zeppelin-zengine/.../search/EmbeddingSearchTest.java` — 11 tests including semantic validation
- `docs/embedding-search.md` — This document

### Modified files — Backend
- `zeppelin-zengine/pom.xml` — Add `onnxruntime` and `djl-tokenizers` dependencies
- `zeppelin-zengine/.../conf/ZeppelinConfiguration.java` — Add `ZEPPELIN_SEARCH_SEMANTIC_ENABLE`
- `zeppelin-server/.../server/ZeppelinServer.java` — Wire `EmbeddingSearch` based on config
- `NOTICE` — Attribution for ONNX Runtime and DJL

### Modified files — Frontend
- `zeppelin-web-angular/.../result-item/` — Render search results with separate
  code block, output block, and table name display (replaces Monaco editor)
- `zeppelin-web/src/app/search/` — Same improvements for Classic UI
- Various TypeScript build fixes (`tsconfig`, type annotations)

### Dependencies added
- `com.microsoft.onnxruntime:onnxruntime:1.18.0` (~50MB, Apache 2.0 compatible)
- `ai.djl.huggingface:tokenizers:0.28.0` (~2MB, Apache 2.0, JNA excluded to
  avoid version conflict with Zeppelin's existing JNA 4.1.0)

## Search Result Display

Both Angular and Classic UIs now render search results with:
- **Code block**: SQL/Python code with syntax-appropriate styling
- **Output block**: Paragraph execution results (table data, text output)
- **Table names**: Extracted SQL table names highlighted with 📊 icon
- **Language badge**: `sql`, `python`, `md`, etc.

## Design Decisions

### Why ONNX Runtime instead of a Java ML library?

ONNX Runtime is the standard inference engine for transformer models. It supports
the exact same model files used by Python (HuggingFace, ChromaDB, etc.), ensuring
embedding compatibility.

### Why brute-force instead of HNSW/ANN?

For Zeppelin's scale (typically < 50K paragraphs), brute-force cosine similarity
on normalized vectors is fast enough (< 50ms), exact (no approximation error),
and adds zero complexity.

### Why download model on first use instead of bundling?

The ONNX model is 86MB. Bundling it would bloat the Zeppelin distribution.
Downloading on first use keeps the distribution lean and allows users to swap models.

### Why not use Lucene's vector search (since 9.0)?

Zeppelin uses Lucene 8.7.0. Upgrading to 9.x is a separate, larger effort.

## Testing

```bash
# Run embedding search tests (requires model download, ~86MB first time)
ZEPPELIN_EMBEDDING_TEST=true mvn test -pl zeppelin-zengine \
  -Dtest=EmbeddingSearchTest

# Run existing Lucene tests (should still pass, no changes)
mvn test -pl zeppelin-zengine -Dtest=LuceneSearchTest
```

### Key tests

- `semanticSearchFindsRelatedConcepts` — validates that "yesterday's spending"
  ranks a SQL spend query above an unrelated user count query
- `newParagraphIsLiveIndexed` — validates that newly added paragraphs are
  immediately searchable without restart

## Future Work

- [ ] Quantized model support (22MB INT8 vs 86MB FP32)
- [ ] Hybrid search: combine embedding similarity with keyword matching
- [ ] Configurable model URL for air-gapped environments
- [ ] Batch embedding during initial index rebuild
- [ ] Similarity score display in search results

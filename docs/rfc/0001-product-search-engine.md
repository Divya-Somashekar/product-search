# RFC 0001: Choose a search engine for product search

| | |
|---|---|
| **Status** | Accepted |
| **Author** | Divya Somashekar |
| **Date** | 2026-09-30 |
| **Details** | [RFD 0001](../rfd/0001-product-search-with-elasticsearch.md): full analysis, query design, sync strategy |

## Problem

Customers must be able to find products by searching across name, description, category and
brand, even when they misspell words ("wireles headphons" must find wireless headphones).
They also need to filter by price and availability, sort by relevance or price, page through
results, and see a highlighted snippet of where each result matched. The product catalogue lives
in Postgres, and a plain SQL `LIKE` query cannot tolerate typos, rank results by relevance or
produce highlights, and it gets slower as the catalogue grows. Filters must be exact: an item
priced above €100 must never appear under "≤ €100". We need a search solution that meets these
requirements and that a small team can run on Kubernetes.

## Options

### Option 1: Postgres only (full-text search and trigrams)

Search inside the existing database, using `tsvector` full-text indexes for matching and ranking,
and the `pg_trgm` extension for typo tolerance.

- ✅ No new system to run, and search sees every write immediately.
- ✅ One technology (SQL), with no data to keep in sync.
- ❌ Typo tolerance, relevance and facets have to be hand-built and tuned in SQL, with much less
  control than a search engine.
- ❌ Highlighting is slow, and search load competes with the main database workload.

### Option 2: Elasticsearch next to Postgres

Postgres stays the source of truth. Every product change is copied into an Elasticsearch index
used only for search, which can be rebuilt from Postgres at any time.

- ✅ Meets every requirement with built-in features: fuzzy matching, BM25 relevance, exact
  filters, sorting, highlighting and facets.
- ✅ Fine-grained relevance tuning, horizontal scaling, and a mature ecosystem (official Java
  client, the ECK operator for Kubernetes).
- ❌ Another stateful, memory-hungry system to operate.
- ❌ Two copies of the data: search lags writes by about a second and needs a sync mechanism plus
  a repair path (reindex).

### Option 3: Meilisearch next to Postgres

Like option 2, but with a lightweight search engine built for "search box" use cases, where typo
tolerance works out of the box.

- ✅ Excellent product-search results with almost no tuning.
- ✅ Much lighter to run than Elasticsearch (a single binary, low memory).
- ❌ Less control over relevance, and fewer query features for complex cases.
- ❌ Smaller ecosystem and more limited scaling, and it still needs the same Postgres → search sync.

| | Typo tolerance | Relevance control | Ops cost | Consistency | Scale |
|---|---|---|---|---|---|
| 1: Postgres only | 🟡 hand-tuned | 🟡 | ✅ none added | ✅ immediate | 🟡 |
| 2: Elasticsearch | ✅ | ✅ | ❌ high | 🟡 ~1 s | ✅ |
| 3: Meilisearch | ✅ | 🟡 | 🟡 low | 🟡 ~1 s | 🟡 |

## Recommendation

Adopt **Option 2: Elasticsearch next to Postgres**, with Postgres as the source of truth and the
index kept in sync after each committed write. Reads and writes go through an alias, so the index
can be rebuilt with zero downtime whenever it drifts or the mapping changes. It is the only option
that meets every requirement with built-in features while keeping full control over relevance,
which decides whether customers find what they are looking for. Its operational cost is reduced
by running it with the ECK operator. The sync risk stays small because the index is disposable:
a missed update is repaired with a reindex, and a transactional outbox can make indexing
guaranteed when needed.

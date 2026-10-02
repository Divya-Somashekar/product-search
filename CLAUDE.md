# CLAUDE.md

## Project Overview

Product search API for an online store: a Kotlin / Spring Boot service that keeps products in
**Postgres** (source of truth) and serves search from **Elasticsearch** (a rebuildable index).
Backend and ops only — there is no frontend.

A personal PoC, but the code is meant to be production quality: validated input, RFC 9457
errors, health probes, metrics, structured logs, hardened containers, real integration tests.

Search supports typo-tolerant matching over name, description, category and brand; filters for
price, availability, category and brand; sorting by relevance or price; pagination; highlighted
snippets; and category/brand facets.

## Repository Layout

This repo holds the service only. **Deployment config lives in a separate repo**,
`Divya-Somashekar/deploy`, shared with the other projects — see [Deployment](#deployment).

```text
product-search/
├── app/                     # Kotlin / Spring Boot service (Gradle, own wrapper)
│   ├── src/                 # the assembly: @SpringBootApplication, application*.yml,
│   │                        #   LocalProductSource, and every integration test
│   ├── catalog-contract/    # what leaves the catalog; no dependencies at all
│   ├── catalog/             # Product aggregate, CRUD, Flyway migrations (db/)
│   ├── search/              # Elasticsearch adapters, products-index.json
│   └── shared/              # problem(), PageResponse, X-Request-Id filter, Clock
├── local/docker-compose.yml # Postgres + Elasticsearch for local development
├── docs/                    # rfd/0001 (engine choice), rfc/0001 (summary), architecture.md
└── .github/workflows/       # ci.yaml (PRs), release.yaml (main)
```

## Architecture

```text
push to main (app/**) ─► release.yaml: test ─► build image ─► push to GHCR
                                        └─► bot commit into the DEPLOY REPO:
                                            services/product-search/overlays/local
                                                          │
                       ArgoCD (in minikube) watches the deploy repo's main, path apps
                                                          ▼
          ECK ─► Elasticsearch "search"   CloudNativePG ─► Postgres "products-db"
                                   ▲                         ▲
                                   └──── product-search pods ┘  (image from ghcr.io)
```

- **Writes** go to Postgres. After the transaction commits, `ProductIndexer` indexes the product
  in Elasticsearch (`@TransactionalEventListener(AFTER_COMMIT)`), so a rolled-back write never
  reaches the index. If Elasticsearch is down, the write still succeeds, the failure is logged
  and counted (`product.indexing.failures`), and `POST /api/v1/admin/reindex` repairs the index.
- **Reads** for search go to Elasticsearch only; CRUD reads go to Postgres.

## The Service (`app/`)

Stack: Kotlin 2.3, Spring Boot 4.1, Java 25 toolchain, Gradle 9.8 (wrapper), Spring Data JPA +
Flyway, the official Elasticsearch Java client 9.x, springdoc OpenAPI, Micrometer/Prometheus,
JUnit 5 + Testcontainers, ktlint, Kover.

The build was written by hand: start.spring.io returned HTTP 500 for every Kotlin + Gradle project
when this was set up. Versions were taken from the Boot 4.1.1 BOM.

Package `com.example.productsearch`, split into two bounded contexts plus `shared`, each context
layered `api` → `application` → `domain` ← `infrastructure` (DDD-lite: the JPA entity *is* the
aggregate, and the Spring Data repository *is* the persistence port — no extra mapping layer).

Each context is also a **Gradle module** (`:catalog-contract`, `:catalog`, `:search`, `:shared`),
so the layering rules below are compile errors rather than review comments: `search` cannot reach
into `catalog.api` because that package is not on its compile classpath at all.

The root project is the **assembly** and nothing else — the `@SpringBootApplication`, the
`application*.yml` files, the concerns that belong to the running service rather than to either
context (actuator, OpenAPI, the Prometheus registry), and the integration tests. It is still what
builds the single executable jar, so the Dockerfile and `release.yaml` are unaffected by the split;
each module ships as a `BOOT-INF/lib/*.jar` inside it.

| Package | What lives there |
|---|---|
| `catalog.contract` (`:catalog-contract`) | `ProductSnapshot`, `ProductChange` (one change log entry) and the in-process change events — everything that leaves the catalog. Deliberately dependency-free: no Spring, no JPA, no Jackson, so it stays publishable as the wire contract |
| `catalog.domain` (`:catalog`) | `Product` (aggregate, `@Entity`), `ProductRepository`, `ProductCommand`, `ProductPage`, `ProductNotFoundException`, `OutboxEntry`/`OutboxRepository` (the change log) |
| `catalog.application` (`:catalog`) | `ProductService` — the CRUD use cases; commits, then publishes a change event |
| `catalog.api` (`:catalog`) | `ProductController`, `InternalProductController` (the cross-service feed), request/response DTOs, mappers, `CatalogExceptionHandler` |
| `catalog.config` (`:catalog`) | `CatalogProperties` |
| `search.domain` (`:search`) | search model (`SearchCriteria`, `SearchResult`, …) and the three ports: `ProductIndex` (query/index/delete via the alias), `IndexLifecycle` (create/bulk-load/swap/drop) and `ProductSource` (keyset reads of the catalog for a rebuild) |
| `search.application` (`:search`) | `ProductSearchService` (validate, measure), `ProductIndexer` (after-commit listener), `ReindexService` |
| `search.infrastructure` (`:search`) | the Elasticsearch adapters: `ElasticsearchProductIndex`, `IndexManager`, `SearchQueryBuilder`, `ProductDocument` |
| `search.api` (`:search`) | `SearchController`, `AdminReindexController`, response DTOs, mappers, `SortOptionConverter`, `SearchExceptionHandler` |
| `search.config` (`:search`) | `SearchProperties`, `SearchConfiguration` (index initialiser) |
| `shared.web` (`:shared`) | `ApiExceptionHandler` (framework errors), `problem()`, `PageResponse`, `X-Request-Id` correlation filter |
| `shared.config` (`:shared`) | the application `Clock` |

Layering rules worth keeping:

- **No Elasticsearch type leaves `search.infrastructure`.** The adapter rewrites client failures
  as `SearchUnavailableException`, which `search.api` renders as 503.
- **Nothing below `api` knows about HTTP DTOs.** Writes enter as a `ProductCommand`; everything
  that leaves the catalog — responses, change events, index documents — travels as a
  `ProductSnapshot`, so `search` never imports `catalog.api`.
- **`search` imports nothing from `catalog` but `catalog.contract`**, and `catalog` never depends
  on `search`. Neither module is on the other's compile classpath, so this is a compile error, not
  a convention. A rebuild reads the catalog through the `ProductSource` port; the composition root
  (`assembly.LocalProductSource`) is the only place that knows both contexts, and it is the one
  thing a service split replaces — with an adapter in the search service calling
  `GET /internal/products` over the same keyset contract.
- Each context maps its own failures in its `api` package; only framework-level errors live in
  `shared.web`.

### API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/products/search` | Search. Params: `q`, `category` (repeatable), `brand` (repeatable), `minPrice`, `maxPrice`, `inStock`, `sort` = `relevance` \| `price_asc` \| `price_desc`, `page` (≥0), `size` (1–100) |
| `POST/GET/PUT/DELETE` | `/api/v1/products[/{id}]` | CRUD in Postgres; each write is mirrored to the index |
| `POST` | `/api/v1/admin/reindex` | Rebuild the index from Postgres (409 if one is already running) |
| `GET` | `/internal/products` | Keyset page of `ProductSnapshot` for services that rebuild a projection of the catalog. Params: `after` (last id seen), `size` (1–1000). A short page is the last one. Unauthenticated, like `/admin/reindex` |
| `GET` | `/internal/changes` | The catalog's change log, oldest first, for keeping a projection in step. Params: `after` (highest `seq` applied), `size` (1–1000). `product` is `null` when the product is gone. Entries newer than `catalog.outbox.visibility-lag` are held back |

Errors are `application/problem+json`. Swagger UI: `/swagger-ui.html`. Actuator (health,
prometheus) is on the **management port 8081**, not on 8080.

### Elasticsearch index

Design rationale, the alternatives considered (Postgres-only, OpenSearch, Meilisearch/Typesense,
SaaS) and the sync strategy trade-offs: `docs/rfd/0001-product-search-with-elasticsearch.md`.

- Definition: `app/src/main/resources/elasticsearch/products-index.json` (settings + strict mapping).
- The app only talks to the **alias `products`**. Concrete indices are named
  `products-<yyyyMMdd-HHmmssSSS>`.
- Reindex is zero-downtime: build a new index, bulk-load it from Postgres, swap the alias
  atomically, delete the old index. Writes that land *during* a reindex go to the old index and
  are not copied — rerun, or add an outbox, if that matters.
- On startup `ReindexService.initialize()` creates the first index if the alias does not exist;
  concurrent replicas are safe because index creation is atomic.
- To change the mapping: edit the JSON, deploy, call `POST /api/v1/admin/reindex`.

Mapping highlights: `name`/`description` use a custom `product_text` analyzer (lowercase +
asciifolding); `category`/`brand` are lowercase-normalized `keyword`s with a `.text` subfield;
`price` is `scaled_float` (factor 100).

### Query (`SearchQueryBuilder`)

- `multi_match` over `name^3, brand.text^2, category.text^2, description`, `best_fields`,
  `fuzziness: AUTO`, `prefix_length: 1`, `minimum_should_match: "2<75%"`, plus a `match_phrase`
  boost on `name`. Empty `q` → `match_all`.
- `"2<75%"` is deliberate: with plain `75%`, a two-word query needed only one word, so
  "wireless headphones" matched a wireless keyboard. Don't loosen it without re-running the tests.
- Filters (price range, `inStock`, category, brand) go in `bool.filter` — no scoring, cached.
- Sort tie-breakers: `_score`, then `id`, so pagination is stable.
- Highlights use `encoder: html` so product data can never inject markup.
- Paging is `from`/`size`, capped by `search.max-result-window` (10 000).

### Configuration

- `application.yml` defaults contain **no connection details or secrets**; they come from the
  environment (`SPRING_DATASOURCE_*`, `SPRING_ELASTICSEARCH_*`).
- Profiles: `local` (localhost Postgres/ES from docker-compose, plain logs, pulls in `demo`);
  `demo` (adds `db/demo` to Flyway locations → ~40 demo products). Kubernetes runs with `demo`.
- `search.refresh`: `false` in production, `wait_for` locally and in tests (writes visible to
  search immediately).
- `spring.jpa.hibernate.ddl-auto=validate`: the entity must match the Flyway schema exactly
  (e.g. `currency` is `char(3)`, mapped with `@JdbcTypeCode(SqlTypes.CHAR)`).
- Prices are in one currency (`catalog.currency`, EUR); that is what makes price filters valid.

## Commands

Run from `app/` unless noted.

```bash
./gradlew check                 # ktlint + unit + Testcontainers integration tests + Kover (≥70%)
./gradlew test                  # tests only
./gradlew ktlintFormat          # fix formatting
./gradlew koverLog              # print coverage (one aggregated number across all modules)
./gradlew bootTestRun           # run the app against throwaway containers, with demo data

./gradlew :search:test          # one module's own tests (integration tests are in the root project)
./gradlew projects              # list the modules

# against local/docker-compose.yml (run compose from the repo root)
docker compose -f local/docker-compose.yml up -d
./gradlew bootRun --args='--spring.profiles.active=local'

./gradlew bootJar && docker build -t product-search:dev .   # local image
```

Tests mirror the main source tree. Unit tests live in their own module —
`search/infrastructure/SearchQueryBuilderTest` is in `:search`. **Integration tests live in the
root project**, not in the module they exercise: they are `@SpringBootTest` against the assembled
application with real Postgres 18 and Elasticsearch 9.4.5, so they belong to the assembly, and
keeping them together is what lets all of them share one context and one set of containers via the
`@IntegrationTest` annotation. Splitting them per module would mean a context and a container pair
each, and `ProductSearchIntegrationTest` needs Postgres anyway to test the CRUD-to-search path.

Coverage is aggregated across every module (`kover(project(...))` in the root build), so the root's
integration tests still count towards the classes they cover in `:catalog` and `:search`; a module
does not report or verify its own coverage, and `./gradlew koverLog` stays one number. The
acceptance cases — "wireles headphons" still finds wireless headphones, and `maxPrice=100` excludes
pricier items — are in `ProductSearchIntegrationTest`.

## CI/CD

| Workflow | Trigger | Does |
|---|---|---|
| `ci.yaml` | every pull request | `./gradlew check`. Manifest validation moved to the deploy repo's own CI, which renders every service's overlay rather than just this one |
| `release.yaml` | push to `main` touching `app/**`, or manual (`workflow_dispatch`) | `check` + `bootJar`, multi-arch image (`linux/amd64,linux/arm64`) to `ghcr.io/divya-somashekar/product-search:<12-char sha>`, then checks out the **deploy repo** with `DEPLOY_REPO_TOKEN`, runs `kustomize edit set image` in `services/product-search/overlays/local`, and pushes a bot commit there |

- Registry is **GHCR**, authenticated with the built-in `GITHUB_TOKEN` (`packages: write`); no
  registry secrets exist. JFrog was the original plan but needs a company account.
- The image is public, so the cluster needs no pull secret.
- Image names must be **lowercase** (`divya-somashekar`, not `Divya-Somashekar`).
- The tag bump lands in the **deploy repo**, so a release can never re-trigger this repo's
  workflows. It needs `DEPLOY_REPO_TOKEN` (a GitHub App installation token, or a fine-grained PAT
  with `Contents: read/write` on the deploy repo) — `GITHUB_TOKEN` cannot write to another repo.
- Note the token's pushes **do** trigger workflows in the deploy repo, unlike `GITHUB_TOKEN`. That
  repo's CI is `on: pull_request` only for exactly this reason.
- Several app repos push to the same deploy repo, and `concurrency` only serialises within one repo,
  so the bump step retries its rebase-and-push up to five times.
- Changes to only `.github/` do **not** release. To release without an app change:
  `gh workflow run release.yaml --ref main`.
- Multi-arch matters: the Mac/minikube is arm64, GitHub runners are amd64.

**A merge to `main` that touches `app/` is a deploy** — but the resulting bot commit lands in the
deploy repo, not here. `git log` in this repo no longer tells you what is deployed; pull the deploy
repo for that.

## Deployment

Deployment config is **not in this repo**. It lives in `Divya-Somashekar/deploy`, which ArgoCD
watches, and which also serves the other projects. Read that repo's `README.md` and `CLAUDE.md`
for the bootstrap, the Application/ApplicationSet layout, and how to onboard a service.

What matters from this side:

- This service's manifests are at `services/product-search/{base,overlays/local}` in that repo.
- `release.yaml` here rewrites the image in that overlay with
  `kustomize edit set image product-search=…`. **The `product-search` image name in the overlay's
  `images[]` is a contract**: rename it there and this repo's deploys silently stop updating the tag.
- Credentials come from operator-created Secrets in the cluster: `products-db-app`
  (`username`/`password`, CloudNativePG) and `search-es-elastic-user` (`elastic`, ECK). Service
  names: `products-db-rw:5432`, `search-es-http:9200`.
- Pods: 2 replicas, PodDisruptionBudget `minAvailable: 1`, rolling update with
  `maxUnavailable: 0`, non-root UID 10001, read-only root filesystem (`/tmp` is an emptyDir),
  all capabilities dropped, probes on port 8081 (`/actuator/health/{liveness,readiness}`),
  readiness includes Postgres and Elasticsearch.

Bootstrap and day-two operations live in the deploy repo. To reach a running instance from here:

```bash
kubectl -n product-search port-forward svc/product-search 8080:80
```

## Testing a Running Deployment

`README.md` is the operator guide: health checks, port-forwards, and copy-paste `curl` checks for
search, filters, validation, CRUD-to-search, reindex, metrics and logs (section 6), plus the
ship/rollback loop and troubleshooting. Keep it in sync when endpoints, ports or deploy layout
change. The acceptance check:

```bash
kubectl -n product-search port-forward svc/product-search 8080:80
curl -s 'localhost:8080/api/v1/products/search?q=wireles%20headphons&maxPrice=100' | jq '.items[] | {name, price}'
# → 4 wireless headphones ≤ €100; the €349 Sony WH-1000XM5 must not appear
```

## Local Environment Gotchas

- **Docker is Colima.** Testcontainers needs, in `~/.zshrc`:
  ```bash
  export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
  ```
- **Memory.** Colima defaults to 2 CPU / 2 GB. Enough for tests or docker-compose *if no cluster
  is running*; the full minikube stack needs ~5.5 GB (`colima stop && colima start --cpu 4 --memory 8`,
  then recreate minikube). Elasticsearch exiting with code 137 means it ran out of memory.
- **Docker Compose** is not bundled with Homebrew's `docker`: `brew install docker-compose` and
  symlink it into `~/.docker/cli-plugins/`.
- **YAML edits.** Several manifests were once broken by pasted indentation and stray terminal
  text. Manifests now live in the deploy repo — render them there before pushing
  (`kustomize build services/product-search/overlays/local`). In this repo the same care applies to
  `.github/workflows/`. Quote URLs inside `{ … }` flow mappings.

## Known PoC Shortcuts

Not production yet; fix before anything real:

- Elasticsearch runs with TLS disabled and the app uses the `elastic` superuser.
- `POST /api/v1/admin/reindex` has no authentication.
- No NetworkPolicy (minikube's default CNI doesn't enforce one anyway) — see the deploy repo.
- No image scanning or dependency scanning in CI.
- Indexing is still synchronous after commit with a few retries. The outbox that makes it
  guaranteed now exists (`product_outbox`, written in the same transaction, served by
  `GET /internal/changes`) but **nothing consumes it yet** — the search side still relies on the
  in-process `@TransactionalEventListener`. Wiring a consumer to the feed is what replaces it.
- The change log holds entries back for `catalog.outbox.visibility-lag` (1s) because `seq` is
  assigned on insert while rows become visible on commit, so a reader could otherwise step past a
  `seq` still in flight. A write that takes longer than the lag to commit can still be missed;
  `POST /api/v1/admin/reindex` is the repair.
- One environment (`overlays/local` in the deploy repo); stg/prod overlays would sit beside it.
- Demo data is loaded by Flyway under the `demo` profile — never enable it in a real environment.

## Conventions

- Conventional commits (`feat:`, `fix:`, `chore:`). `chore(deploy):` is the release bot, and those
  commits now appear in the deploy repo rather than here.
- Match the surrounding Kotlin style; ktlint is enforced and warnings are errors.
- New behavior gets a test; search relevance changes get an integration test with a concrete query.

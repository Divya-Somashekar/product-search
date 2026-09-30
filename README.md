# product-search

A product search API for an online store: search products by name, description, category and
brand, tolerant of typos, with price/availability filters, sorting, pagination and highlighted
snippets.

Kotlin + Spring Boot service, **Postgres** as the source of truth, **Elasticsearch** for search.
Built and released by **GitHub Actions** to **GitHub Container Registry**, deployed to a local
**minikube** Kubernetes cluster by **ArgoCD** (GitOps).

This README is written for future me, coming back with no memory of any of it. It explains what
was built, how the pieces connect, and exactly how to bring it up and test it again.
`CLAUDE.md` has the deeper technical notes (code structure, index design, conventions).

---

## 1. What this is, in one picture

```text
  You push code to GitHub (main)
          │
          ▼
  GitHub Actions  "release" workflow
    1. runs all tests
    2. builds a Docker image  ──────────────►  ghcr.io/divya-somashekar/product-search:<git-sha>
    3. writes the new image tag into deploy/…/overlays/local/kustomization.yaml and commits it
          │
          ▼
  ArgoCD (running inside minikube) notices the new commit on main and applies deploy/
          │
          ▼
  minikube (Kubernetes on your Mac)
    ├── ECK operator            → runs Elasticsearch  (pod search-es-default-0)
    ├── CloudNativePG operator  → runs Postgres       (pod products-db-1)
    └── product-search app      → 2 pods, pulls the image from ghcr.io
```

Nobody runs `kubectl apply` for deployments. **Git is the source of truth**: what is in `deploy/`
on `main` is what runs. To change what runs, change Git.

### Where everything physically runs

```text
Mac  →  Colima (Linux VM that provides Docker)  →  Docker
                                                    ├── minikube container  →  Kubernetes  →  all pods above
                                                    └── (optional) docker-compose Postgres + Elasticsearch for local dev
```

There are two independent environments — they do **not** share data:

| | Local dev (docker-compose) | Kubernetes (minikube) |
|---|---|---|
| Start | `docker compose … up` + `./gradlew bootRun` | ArgoCD, automatically |
| Postgres | container `local-postgres-1`, `localhost:5432` | pod `products-db-1`, only inside the cluster |
| Elasticsearch | container `local-elasticsearch-1`, `localhost:9200` | pod `search-es-default-0`, only inside the cluster |
| Data survives restart | no | yes, until `minikube delete` |
| Use for | writing code, quick checks | testing the real deployment |

Both start with the same ~40 demo products (headphones, speakers, keyboards…).

---

## 2. Repository map

```text
app/                 the Kotlin/Spring Boot service (has its own Gradle wrapper)
  src/main/…/product/    Postgres side: entity, CRUD API
  src/main/…/search/     Elasticsearch side: index, indexing, search API
  src/main/resources/    application.yml, Flyway SQL (db/migration, db/demo), index mapping JSON
  Dockerfile
deploy/              what ArgoCD deploys
  bootstrap/root-app.yaml   the ONE file applied by hand; points ArgoCD at deploy/apps
  apps/                     4 ArgoCD Applications (below)
  platform/data/            "please create an Elasticsearch and a Postgres"
  services/product-search/  the app's Deployment/Service; overlays/local holds the image tag
local/docker-compose.yml    Postgres + Elasticsearch for local dev
.github/workflows/          ci.yaml (pull requests), release.yaml (main)
```

### The 4 ArgoCD apps (all children of `root`)

| App | What it does |
|---|---|
| `eck-operator` | Installs Elastic's operator. It knows how to run Elasticsearch on Kubernetes. |
| `cnpg-operator` | Installs the CloudNativePG operator. It knows how to run Postgres on Kubernetes. |
| `data` | Two small files asking those operators for 1 Elasticsearch node and 1 Postgres database. The operators create the pods, services and password secrets. |
| `product-search` | The app itself: 2 pods using the image tag CI committed. |

They sync in that order (operators → data → app) via sync waves.

---

## 3. Prerequisites (macOS)

```bash
brew install colima docker docker-compose kubectl helm minikube argocd gh jq
mkdir -p ~/.docker/cli-plugins
ln -sfn "$(brew --prefix)/opt/docker-compose/bin/docker-compose" ~/.docker/cli-plugins/docker-compose
```

Add to `~/.zshrc` (Testcontainers can't find Colima's Docker otherwise), then open a new terminal:

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

Java: nothing to install — Gradle downloads JDK 25 itself.

**Memory is the #1 problem.** Colima's default (2 CPU / 2 GB) is enough for tests *or*
docker-compose, not for minikube. For the Kubernetes setup:

```bash
colima start --cpu 4 --memory 8        # or: colima stop && colima start --cpu 4 --memory 8
```

---

## 4. Develop and test locally (no Kubernetes)

All from `app/`.

### Automated tests

```bash
./gradlew check        # lint + all tests + coverage gate. Must end with BUILD SUCCESSFUL.
```

What that covers: 19 tests, run against **real** Postgres and Elasticsearch containers (started
automatically by Testcontainers — first run downloads images, takes a few minutes). Including the
two acceptance criteria:

- searching `wireles headphons` (typos) still finds the wireless headphones, with highlights
- `maxPrice=100` never returns anything over €100

Reports: `app/build/reports/tests/test/index.html`, coverage with `./gradlew koverLog`.

### Run the app by hand

Option A — throwaway containers, zero setup:

```bash
./gradlew bootTestRun
```

Option B — docker-compose (from the repo root, then `app/`):

```bash
docker compose -f local/docker-compose.yml up -d
curl -s localhost:9200 | head -3          # wait until Elasticsearch answers (~30 s)
cd app && ./gradlew bootRun --args='--spring.profiles.active=local'
```

Either way the API is on **http://localhost:8080**, Swagger UI on
http://localhost:8080/swagger-ui.html, health/metrics on port **8081**. Then run the checks in
[section 6](#6-how-to-test-it). Stop compose afterwards: `docker compose -f local/docker-compose.yml down`.

---

## 5. The Kubernetes deployment

### Is it still running?

```bash
minikube status                                   # if "Stopped": minikube start
kubectl -n argocd get applications                # expect all 5 Synced / Healthy
kubectl -n product-search get elasticsearch,cluster,pods
```

Expected:

```text
root, eck-operator, cnpg-operator, data, product-search   Synced   Healthy
elasticsearch/search      green   1   9.4.5   Ready
cluster/products-db       Cluster in healthy state
pods: products-db-1, search-es-default-0, 2× product-search-…   Running
```

### ArgoCD UI

```bash
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d; echo
kubectl -n argocd port-forward svc/argocd-server 8443:443
```

Open https://localhost:8443 (accept the self-signed certificate), user `admin`.

- **Sync** status = does the cluster match Git? **Health** = is it actually working?
  "Synced but Degraded" means Git was applied but something is failing.
- Click an app → tree of resources → click a pod → **Logs** / **Events**. Fastest way to find errors.
- **Refresh** re-reads Git now instead of waiting ~3 minutes.

### Rebuild everything from scratch

If minikube was deleted, or on a new machine:

```bash
colima start --cpu 4 --memory 8
minikube start --driver=docker --cpus 4 --memory 6g

kubectl create namespace argocd
kubectl apply -n argocd --server-side -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl -n argocd rollout status deploy/argocd-server

kubectl apply -f deploy/bootstrap/root-app.yaml   # from the repo root — the only manual apply
kubectl -n argocd get applications -w             # ~5–10 min until everything is Healthy
```

On the very first start the app pods restart a few times with
`secret "products-db-app" not found`. That is normal: the app starts before Postgres has finished
creating its password secret. It fixes itself within minutes.

---

## 6. How to test it

These work against either environment. For **Kubernetes**, first forward the ports and leave this
terminal open (use any free local port — the examples use 8080 for the API, 9081 for health):

```bash
kubectl -n product-search port-forward svc/product-search 8080:80
kubectl -n product-search port-forward deploy/product-search 9081:8081    # second terminal, optional
```

For **local dev** the API is already on 8080 and health on 8081.

### 6.1 Search — the acceptance criteria

```bash
# Typos still match; only items ≤ €100
curl -s 'localhost:8080/api/v1/products/search?q=wireles%20headphons&maxPrice=100' | jq '.items[] | {name, price}'
```

Expected (verified 2026-09-30): the four wireless headphones under €100 — Sony WH-CH520 49.99,
JBL Tune 520BT 44.90, Anker Soundcore Life Q30 79.99, Sennheiser HD 450BT 99.00. The €349 Sony
WH-1000XM5 must **not** appear.

```bash
# Where did it match? (highlighted snippet, <em> marks the hit)
curl -s 'localhost:8080/api/v1/products/search?q=wireles%20headphons' | jq '.items[0] | {name, highlights}'
```

### 6.2 Search — filters, sorting, paging, facets

```bash
# Only in-stock audio products, cheapest first
curl -s 'localhost:8080/api/v1/products/search?category=audio&inStock=true&sort=price_asc' | jq '.items[] | {name, price, inStock}'

# Most expensive first, one brand
curl -s 'localhost:8080/api/v1/products/search?brand=Sony&sort=price_desc' | jq '.items[] | {name, price}'

# Paging: page 2 of 5-per-page, plus the full total
curl -s 'localhost:8080/api/v1/products/search?q=wireless&page=1&size=5' | jq '{total, page, size, names: [.items[].name]}'

# Facet counts (for a filter sidebar)
curl -s 'localhost:8080/api/v1/products/search?q=wireless' | jq '.facets'
```

### 6.3 Bad input is rejected (HTTP 400, problem+json)

```bash
curl -s 'localhost:8080/api/v1/products/search?minPrice=50&maxPrice=10' | jq
curl -s 'localhost:8080/api/v1/products/search?sort=cheapest' | jq '.status, .detail'
curl -s 'localhost:8080/api/v1/products/search?size=500' | jq '.status'
```

### 6.4 Writes reach search (Postgres → Elasticsearch)

```bash
# create
ID=$(curl -s -X POST localhost:8080/api/v1/products -H 'content-type: application/json' \
  -d '{"name":"Zephyr Studio Headphones","description":"Closed-back studio headphones","category":"audio","brand":"Zephyr","price":129.00,"stockQuantity":5}' | jq -r .id)
echo "$ID"

# searchable (in Kubernetes, writes can take up to ~1 s to appear in search)
curl -s 'localhost:8080/api/v1/products/search?q=zephyr' | jq '.items[] | {name, price}'

# update, then search with the new price
curl -s -X PUT "localhost:8080/api/v1/products/$ID" -H 'content-type: application/json' \
  -d '{"name":"Zephyr Studio Headphones II","description":"Closed-back studio headphones","category":"audio","brand":"Zephyr","price":99.00,"stockQuantity":0}' | jq '{name, price, inStock, version}'
curl -s 'localhost:8080/api/v1/products/search?q=zephyr&maxPrice=100' | jq '.items[] | {name, price, inStock}'

# delete: 204, then 404, and gone from search
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE "localhost:8080/api/v1/products/$ID"
curl -s -o /dev/null -w '%{http_code}\n' "localhost:8080/api/v1/products/$ID"
curl -s 'localhost:8080/api/v1/products/search?q=zephyr' | jq '.total'
```

### 6.5 Rebuild the search index from Postgres

```bash
curl -s -X POST localhost:8080/api/v1/admin/reindex | jq
```

Returns the new index name and document count. Search keeps working during and after — the
alias `products` is switched atomically. Use this if search and the database ever disagree.

### 6.6 Health and metrics

Port 8081 inside the app (9081 if you port-forwarded as above; 8081 for local dev):

```bash
curl -s localhost:9081/actuator/health/readiness       # {"status":"UP"} — includes Postgres + Elasticsearch
curl -s localhost:9081/actuator/health/liveness
curl -s localhost:9081/actuator/prometheus | grep -E '^product_(search|indexing)'
```

### 6.7 Look inside the databases

```bash
# Postgres in Kubernetes
kubectl -n product-search exec -it products-db-1 -- psql -U postgres -d products -c 'select name, price, stock_quantity from product order by price limit 5;'

# Elasticsearch in Kubernetes
PASS=$(kubectl -n product-search get secret search-es-elastic-user -o jsonpath='{.data.elastic}' | base64 -d)
kubectl -n product-search port-forward svc/search-es-http 9200:9200 &      # skip if compose ES already uses 9200
curl -s -u "elastic:$PASS" 'localhost:9200/_cat/aliases?v'
curl -s -u "elastic:$PASS" 'localhost:9200/_cat/indices/products*?v'

# docker-compose versions
docker exec -it local-postgres-1 psql -U products -d products -c 'select count(*) from product;'
curl -s 'localhost:9200/_cat/indices/products*?v'
```

### 6.8 Logs

```bash
kubectl -n product-search logs deploy/product-search --tail=50
kubectl -n product-search logs deploy/product-search -f          # follow
```

Every log line carries a `requestId`; send `-H 'X-Request-Id: my-test-1'` with a curl and grep for it.

---

## 7. Ship a change (the GitOps loop)

```bash
# edit something in app/, then
cd app && ./gradlew check && cd ..
git add -A && git commit -m "feat: …" && git push
```

What happens next, and where to watch it:

1. GitHub → **Actions → release**: tests, image build (~5–10 min).
   `gh run watch` in the terminal does the same.
2. The workflow commits `chore(deploy): product-search <sha>` to `main`. Run `git pull` afterwards.
3. ArgoCD picks it up (≤ 3 min, or press **Refresh**). Watch the rollout:
   `kubectl -n product-search get pods -w` — new pods start, old ones stop, no downtime.
4. Check the running version:
   `kubectl -n product-search get deploy product-search -o jsonpath='{.spec.template.spec.containers[0].image}'`

Things that surprised me:

- Only changes under `app/` trigger a release. Changing only `deploy/` is deployed by ArgoCD
  directly (no new image). To force a release anyway: `gh workflow run release.yaml --ref main`.
- Pull requests run `ci.yaml` (tests + manifest validation) but deploy nothing.
- Rolling back = revert the `chore(deploy)` commit (or use ArgoCD's History; but ArgoCD will
  re-sync to Git, so the Git revert is the real rollback).
- The image must be **public** on GHCR (it is), otherwise minikube can't pull it.

---

## 8. Troubleshooting

| Symptom | Likely cause → fix |
|---|---|
| `docker: unknown shorthand flag 'f'` on `docker compose` | Compose plugin not installed/linked → see Prerequisites |
| Tests: "Could not find a valid Docker environment" | The two `~/.zshrc` exports are missing in this shell |
| Elasticsearch exits with code **137** | Out of memory → give Colima more memory, stop other clusters/compose |
| Pods `Pending` | Out of memory/CPU in minikube → `kubectl describe pod …` shows why |
| `secret "products-db-app" not found` | Postgres not ready yet (first start) → wait; check `kubectl -n product-search get cluster` |
| `ImagePullBackOff` / `InvalidImageName` | No release ran yet, image private, or image name not lowercase |
| ArgoCD app `OutOfSync`/error right after a `deploy/` edit | Broken YAML → `kubectl kustomize deploy/services/product-search/overlays/local` and `kubectl kustomize deploy/platform/data` locally |
| `port-forward` fails "address already in use" | Something (bootRun, compose, another forward) already has that port → pick another local port |
| Search results look stale/wrong | `curl -X POST …/api/v1/admin/reindex` |

---

## 9. Stop / resume / delete

```bash
minikube stop                 # pause the cluster, keeps all data
minikube start                # resume; ArgoCD and everything come back
colima stop                   # stop Docker entirely

minikube delete               # wipe the cluster and its data → rebuild per section 5
docker compose -f local/docker-compose.yml down
```

---

## 10. Decisions made (and why)

Set up 2026-09-30.

| Decision | Why |
|---|---|
| One repo for code and deployment config | Simpler for a PoC. (HiveMQ, where this idea comes from, splits them: app repos push image tags into the `apiaries` config repo.) |
| Postgres as source of truth, Elasticsearch only for search | The index can always be rebuilt from Postgres (`/admin/reindex`); no data lives only in ES. |
| GitHub Container Registry instead of JFrog Artifactory | JFrog needs a company account; GHCR is free with GitHub and needs no secrets in CI. |
| minikube (not kind) | Addons, dashboard, `stop`/`start` keeps state — friendlier for learning. |
| Operators (ECK, CloudNativePG) instead of hand-written StatefulSets | 10 lines of YAML per database; the operator handles certs, passwords, failover. |
| Spring Boot 4 / Kotlin 2.3 / Java 25 / ES 9.4 / Postgres 18 | Current versions at the time. |

Shortcuts that are fine for a PoC but not for real use: Elasticsearch without TLS and with the
`elastic` superuser, no authentication on `/admin/reindex`, no NetworkPolicy, no image scanning,
demo data loaded by Flyway (`demo` profile). Details in `CLAUDE.md`.

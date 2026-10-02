# Splitting into two services — the deploy side

These manifests are **not used from this repo**. They are written here because the deploy repo
(`Divya-Somashekar/deploy`) is not checked out alongside this one. Copy them over:

```bash
cp -r docs/deploy-split/services/catalog-service /path/to/deploy/services/
cp -r docs/deploy-split/services/search-service  /path/to/deploy/services/
# then render before committing, as that repo's CLAUDE.md asks
kustomize build services/catalog-service/overlays/local
kustomize build services/search-service/overlays/local
```

Then add both to the ApplicationSet (or add two Applications) so ArgoCD picks them up, and delete
the old `services/product-search` once the two are healthy.

## What changes versus the single service

| | `catalog-service` | `search-service` |
|---|---|---|
| Image | `ghcr.io/divya-somashekar/catalog-service` | `ghcr.io/divya-somashekar/search-service` |
| Backing store | Postgres (`products-db-rw:5432`) | Elasticsearch (`search-es-http:9200`) |
| Secret | `products-db-app` (CloudNativePG) | `search-es-elastic-user` (ECK) |
| Readiness | `readinessState,db` | `readinessState,elasticsearch` |
| Talks to | nothing | the catalog, via `CATALOG_BASE_URL` |

Two things worth knowing before you apply this:

- **The `images[].name` in each overlay is the contract with this repo.** `release.yaml` runs
  `kustomize edit set image <service>=...` against it. Rename it and deploys silently stop
  updating the tag — the same trap the single-service setup had, now twice.
- **`search-service` does not have the catalog in its readiness group, on purpose.** That is the
  one real win of the split: a Postgres outage no longer makes search unready. If you add the
  catalog to that group you have paid for two services and kept one failure domain.

## First release: make both images public

The cluster has no `imagePullSecret`, which works today only because
`ghcr.io/divya-somashekar/product-search` is a public package. The first release creates two *new*
packages, and **GHCR makes a new package private by default**. There is no REST API to change that
(the packages API only has get/delete/restore), so it is a one-time click per service, and it can
only be done after the package exists:

```
https://github.com/users/divya-somashekar/packages/container/catalog-service/settings
https://github.com/users/divya-somashekar/packages/container/search-service/settings
```
→ Danger Zone → Change visibility → Public.

Miss it and CI goes green while both Deployments sit in `ImagePullBackOff`. To check:

```bash
./check-images-public.sh          # exits non-zero and prints the settings URL for each one
```

## Still missing

- No NetworkPolicy. `/internal/*` on the catalog is unauthenticated, and now it is a cross-pod
  call rather than an in-process one, so anything in the cluster can read the whole catalogue.
- `search-service` holds its change-log cursor in memory, so a restart replays the log from the
  start. Safe (entries resolve to current state) but it grows unbounded — nothing prunes
  `product_outbox` yet.

// What leaves the catalog: ProductSnapshot and the change events. Deliberately dependency-free —
// no Spring, no JPA, no HTTP — so it stays publishable as the wire contract between two services
// and so nothing can smuggle an entity or a DTO across the seam.

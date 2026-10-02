// Search owns Elasticsearch and the index, which is a projection of the catalog. The index
// definition (products-index.json) is this module's resource.
dependencies {
    implementation(project(":catalog-contract"))
    implementation(project(":shared"))

    // The one dependency a service split has to remove: ReindexService reads ProductRepository
    // directly, because rebuilding the index means reading the source of truth. It also drags
    // Spring Data JPA onto this module's classpath — persistence that a standalone search service
    // would have no use for. Replacing it with a paginated internal export on the catalog side is
    // what makes `search` deployable alone; until then this line is the honest statement that it
    // is not. Everything else search needs from the catalog comes from :catalog-contract.
    implementation(project(":catalog"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.micrometer:micrometer-core")
}

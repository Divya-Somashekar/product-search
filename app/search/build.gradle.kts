// Search owns Elasticsearch and the index, which is a projection of the catalog. The index
// definition (products-index.json) is this module's resource.
dependencies {
    implementation(project(":catalog-contract"))
    implementation(project(":shared"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.micrometer:micrometer-core")
    // Reading the catalog's feed over HTTP when search runs as its own service.
    implementation("tools.jackson.module:jackson-module-kotlin")
}

plugins {
    kotlin("plugin.jpa")
}

// The catalog owns Postgres: the Product aggregate, the CRUD use cases and the schema (its Flyway
// migrations are this module's resources). It knows nothing about search.
dependencies {
    // Both appear in this module's public signatures: ProductSnapshot is what every use case
    // returns, and `ProductRepository : JpaRepository<Product, UUID>` exposes Spring Data JPA.
    api(project(":catalog-contract"))
    api("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation(project(":shared"))

    // Product images live in S3. The BOM is needed because Spring Boot's dependency management
    // does not cover the AWS SDK, so without it every artifact would carry its own version.
    // s3 brings the presigner; nothing here uses the async client or Netty.
    implementation(platform("software.amazon.awssdk:bom:2.55.10"))
    implementation("software.amazon.awssdk:s3")

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // The schema and the demo data ship here, but nothing in this module compiles against Flyway
    // or the driver.
    runtimeOnly("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

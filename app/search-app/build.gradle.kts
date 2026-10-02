// The search service: Elasticsearch, search, and a tail of the catalog's change log.
dependencies {
    implementation(project(":search"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
}

// This module is a service, not a library: it is the one that produces an executable jar.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") { enabled = true }
tasks.named<Jar>("jar") { enabled = false }

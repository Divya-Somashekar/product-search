plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jetbrains.kotlinx.kover")
}

group = "com.example"
version = "0.1.0"
description = "Product search API backed by Postgres and Elasticsearch"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// The root project is the assembly: the @SpringBootApplication, application*.yml, the concerns
// that belong to the running service rather than to either context (actuator, OpenAPI, the
// Prometheus registry), and the integration tests. Each context's own libraries moved with it.
dependencies {
    implementation(project(":catalog"))
    implementation(project(":search"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-elasticsearch-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-elasticsearch")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        allWarningsAsErrors = true
    }
}

/**
 * Shared by the root project and every module, so a module that gains integration tests finds
 * Docker the same way this project always has.
 */
fun Test.configureForThisMachine() {
    useJUnitPlatform()

    // Testcontainers only probes DOCKER_HOST, ~/.testcontainers.properties and
    // /var/run/docker.sock, so it does not find colima's socket on its own.
    // Point it there when colima is the only Docker available. The override is
    // the path Ryuk mounts *inside* the VM, where the daemon socket really is.
    val colimaSocket = file("${System.getProperty("user.home")}/.colima/default/docker.sock")
    if (!file("/var/run/docker.sock").exists() && colimaSocket.exists()) {
        if (System.getenv("DOCKER_HOST") == null) {
            environment("DOCKER_HOST", "unix://${colimaSocket.absolutePath}")
        }
        if (System.getenv("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE") == null) {
            environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
        }
    }
}

tasks.withType<Test> {
    configureForThisMachine()
}

// Every module gets the same Kotlin, Boot BOM and lint setup; only dependencies differ, so each
// module's own build file stays short. The Boot plugin is applied for its dependency BOM, not to
// build a jar — the one executable jar is still the root project's.
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension> {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
            allWarningsAsErrors = true
        }
    }

    dependencies {
        "testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") { enabled = false }
    // The Boot plugin classifies a plain jar as `-plain`; a library module only ever produces the
    // plain one, so drop the classifier and keep the image's BOOT-INF/lib readable.
    tasks.named<Jar>("jar") {
        enabled = true
        archiveClassifier = ""
    }
    tasks.withType<Test> { configureForThisMachine() }

    // Coverage is only meaningful across the whole application, so a module neither reports nor
    // verifies its own — `./gradlew koverLog` stays one number. The coverage artifact each module
    // produces still feeds the root report.
    val aggregateOnly =
        setOf(
            "koverLog",
            "koverPrintCoverage",
            "koverVerify",
            "koverCachedVerify",
            "koverHtmlReport",
            "koverXmlReport",
            "koverBinaryReport",
        )
    tasks.matching { it.name.removeSuffix("Jvm") in aggregateOnly }.configureEach { enabled = false }
}

// Only the boot jar is needed; skip the plain jar so the image build picks one file.
tasks.jar {
    enabled = false
}

// Coverage is measured across every module, not just the root project, so moving a package into
// a module cannot quietly drop it out of the 70% gate.
dependencies {
    kover(project(":shared"))
    kover(project(":catalog-contract"))
    kover(project(":catalog"))
    kover(project(":search"))
}

kover {
    reports {
        verify {
            rule {
                minBound(70)
            }
        }
    }
}

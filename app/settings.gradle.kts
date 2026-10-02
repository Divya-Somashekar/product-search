pluginManagement {
    // Versions live here so every module can declare a plugin without repeating its version.
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.3.21"
        id("org.jetbrains.kotlin.plugin.spring") version "2.3.21"
        id("org.jetbrains.kotlin.plugin.jpa") version "2.3.21"
        id("org.springframework.boot") version "4.1.1"
        id("io.spring.dependency-management") version "1.1.7"
        id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
        id("org.jetbrains.kotlinx.kover") version "0.9.11"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "product-search"

// One module per bounded context, so the compiler enforces the layering rules that CLAUDE.md
// currently states as convention: `search` cannot reach into `catalog.api`, and `catalog` cannot
// depend on `search` at all — a stray import fails the build instead of needing a reviewer.
//
// `catalog-contract` holds only what leaves the catalog (ProductSnapshot and the change events).
// It is the seam a future service split would run along: everything `search` needs from `catalog`
// in steady state is in there, and nothing else.
//
// The modules are empty for now. Packages move into them one commit at a time; the root project
// keeps the sources, the boot jar and the Dockerfile's jar path until the last of them lands.
include("shared")
include("catalog-contract")
include("catalog")
include("search")

// The two deployable services. Each produces its own boot jar and its own image; the root project
// stays the single-process assembly used for local development and the integration tests.
include("catalog-app")
include("search-app")

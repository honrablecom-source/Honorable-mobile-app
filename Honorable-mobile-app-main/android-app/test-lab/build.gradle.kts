plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

sourceSets.main {
    kotlin.srcDir("../app/src/main/java")
    kotlin.include("app/honorable/search/SearchCore.kt")
    kotlin.include("app/honorable/search/SearchPipeline.kt")
    kotlin.include("app/honorable/search/SearchEvaluation.kt")
    kotlin.include("app/honorable/search/VisionEnrichment.kt")
    kotlin.include("app/honorable/testlab/**")
}

application { mainClass.set("app.honorable.testlab.TestLabKt") }
java { targetCompatibility = JavaVersion.VERSION_17; sourceCompatibility = JavaVersion.VERSION_17 }
dependencies { testImplementation("org.junit.jupiter:junit-jupiter:5.11.4") }
val repoRoot = rootProject.projectDir.parentFile
tasks.test { useJUnitPlatform(); workingDir = repoRoot }

fun cliTask(name: String, vararg args: String) = tasks.register<JavaExec>(name) {
    group = "verification"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    workingDir = repoRoot
    args(*args)
    if (name == "searchTestMedia") {
        doFirst {
            val query = providers.gradleProperty("query").orNull ?: error("Use -Pquery=\"red car in snow\"")
            setArgs(listOf("search", "--query", query, "--top", providers.gradleProperty("topK").orNull ?: "10") + (if(providers.gradleProperty("labDebug").orNull=="true")listOf("--debug")else emptyList()) + (if(providers.gradleProperty("topK").isPresent)listOf("--show-ranking")else emptyList()))
        }
    }
}

cliTask("indexTestMedia", "index")
cliTask("enrichTestMedia", "enrich")
cliTask("searchTestMedia", "search")
cliTask("evaluateSearch", "evaluate")
cliTask("serveTestMedia", "serve", "--port", providers.gradleProperty("port").orNull ?: "4174")
cliTask("listTestMedia", "list")

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.0" apply false
}

tasks.register("indexTestMedia") { dependsOn(":test-lab:indexTestMedia") }
tasks.register("searchTestMedia") { dependsOn(":test-lab:searchTestMedia") }
tasks.register("evaluateSearch") { dependsOn(":test-lab:evaluateSearch") }
tasks.register("serveTestMedia") { dependsOn(":test-lab:serveTestMedia") }

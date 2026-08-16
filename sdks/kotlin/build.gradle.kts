import java.util.zip.ZipFile
import org.gradle.api.tasks.Sync
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.Sign

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    `java-library`
    `maven-publish`
    signing
    id("org.jetbrains.dokka") version "2.2.0"
    id("com.vanniktech.maven.publish.base") version "0.37.0"
}

group = "io.github.gtechgovind"
version = "0.0.1"

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
}

dokka {
    dokkaPublications.html {
        moduleName.set("Orion Kotlin SDK")
        moduleVersion.set(project.version.toString())
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
    }
}

val dokkaJavadocJar = tasks.register<Jar>("dokkaJavadocJar") {
    description = "Packages Dokka-generated Kotlin API documentation for publication."
    group = "documentation"
    dependsOn("dokkaGeneratePublicationHtml")
    archiveClassifier.set("javadoc")
    from(layout.buildDirectory.dir("dokka/html"))
}

fun normalizedOperatingSystem(name: String): String = when {
    name.contains("mac", ignoreCase = true) -> "macos"
    name.contains("linux", ignoreCase = true) -> "linux"
    name.contains("windows", ignoreCase = true) -> "windows"
    else -> error("Unsupported host operating system: $name")
}

fun normalizedArchitecture(name: String): String = when (name.lowercase()) {
    "aarch64", "arm64" -> "aarch64"
    "amd64", "x86_64", "x64" -> "x86_64"
    else -> error("Unsupported host architecture: $name")
}

fun nativeLibraryFileName(operatingSystem: String): String = when (operatingSystem) {
    "macos" -> "liborion_kotlin.dylib"
    "linux" -> "liborion_kotlin.so"
    "windows" -> "orion_kotlin.dll"
    else -> error("Unsupported native-library operating system: $operatingSystem")
}

val hostOperatingSystem = normalizedOperatingSystem(System.getProperty("os.name"))
val hostArchitecture = normalizedArchitecture(System.getProperty("os.arch"))
val hostNativeFileName = nativeLibraryFileName(hostOperatingSystem)
val nativeResourcePrefix = "META-INF/orion/native"
val hostNativeResourcePath = "$nativeResourcePrefix/$hostOperatingSystem/$hostArchitecture/$hostNativeFileName"
val generatedNativeResources = layout.buildDirectory.dir("generated/orionNativeResources")
val prebuiltNativeDirectory = providers.gradleProperty("orion.native.prebuiltDir").orNull

val buildNative = tasks.register<Exec>("buildNative") {
    description = "Builds the Orion JNI library with Cargo."
    group = "build"
    workingDir(rootProject.projectDir.resolve("../.."))
    commandLine("cargo", "build", "-p", "orion-kotlin", "--release", "--locked")
}

val stageNativeLibraries = tasks.register<Sync>("stageNativeLibraries") {
    description = "Stages host or prebuilt JNI libraries under the release resource convention."
    group = "build"
    into(generatedNativeResources)

    if (prebuiltNativeDirectory == null) {
        dependsOn(buildNative)
        from(rootProject.projectDir.resolve("../../target/release/$hostNativeFileName")) {
            into("$nativeResourcePrefix/$hostOperatingSystem/$hostArchitecture")
        }
    } else {
        from(rootProject.file(prebuiltNativeDirectory)) {
            into(nativeResourcePrefix)
        }
    }

    doLast {
        val nativeRoot = generatedNativeResources.get().asFile.resolve(nativeResourcePrefix)
        val nativeFiles = nativeRoot.walkTopDown().filter(File::isFile).toList()
        check(nativeFiles.isNotEmpty()) { "No JNI libraries were staged under $nativeResourcePrefix" }
        nativeFiles.forEach { file ->
            val parts = file.relativeTo(nativeRoot).invariantSeparatorsPath.split('/')
            check(parts.size == 3) {
                "Native library must use <os>/<arch>/<filename>: ${file.relativeTo(nativeRoot)}"
            }
            check(parts[2] == nativeLibraryFileName(parts[0])) {
                "Native filename ${parts[2]} does not match operating system ${parts[0]}"
            }
            check(parts[1] == normalizedArchitecture(parts[1])) {
                "Native architecture directory must use canonical name aarch64 or x86_64: ${parts[1]}"
            }
        }
    }
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.11.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

sourceSets {
    test {
        kotlin.srcDir("../../examples/kotlin/weather-agent/src/main/kotlin")
    }
}

tasks.processResources {
    dependsOn(stageNativeLibraries)
    from(generatedNativeResources)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(dokkaJavadocJar)
            pom {
                name.set("Orion Kotlin SDK")
                description.set("Kotlin SDK with an in-process JNI binding to the Orion Rust kernel")
                url.set("https://github.com/GtechGovind/orion")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/license/mit")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("GtechGovind")
                        name.set("Govind Yadav")
                        email.set("gtech.govind2000@gmail.com")
                        organization.set("GtechGovind")
                        organizationUrl.set("https://github.com/GtechGovind")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/GtechGovind/orion.git")
                    developerConnection.set("scm:git:ssh://git@github.com/GtechGovind/orion.git")
                    url.set("https://github.com/GtechGovind/orion")
                    tag.set("HEAD")
                }
                issueManagement {
                    system.set("GitHub Issues")
                    url.set("https://github.com/GtechGovind/orion/issues")
                }
            }
        }
    }
}

val signingKey = providers.gradleProperty("signingInMemoryKey")
val signingKeyId = providers.gradleProperty("signingInMemoryKeyId")
val signingKeyPassword = providers.gradleProperty("signingInMemoryKeyPassword")

signing {
    isRequired = false

    if (signingKey.isPresent) {
        useInMemoryPgpKeys(
            signingKeyId.orNull,
            signingKey.get(),
            signingKeyPassword.orNull,
        )
    }

    sign(publishing.publications.named("maven").get())
}

tasks.withType<Sign>().configureEach {
    onlyIf("an in-memory signing key is configured") { signingKey.isPresent }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = false)
}

val centralPublishingEnabled = providers.gradleProperty("orionCentralPublishing")
    .map(String::toBooleanStrict)
    .orElse(false)
val centralUsername = providers.gradleProperty("mavenCentralUsername")
val centralPassword = providers.gradleProperty("mavenCentralPassword")

tasks.matching { task -> task.name.contains("MavenCentral", ignoreCase = true) }.configureEach {
    doFirst {
        check(centralPublishingEnabled.get()) {
            "Central Portal publication is disabled; set -PorionCentralPublishing=true explicitly"
        }
        check(centralUsername.isPresent && centralPassword.isPresent) {
            "Central Portal credentials are missing; set mavenCentralUsername and mavenCentralPassword"
        }
        check(signingKey.isPresent) {
            "Central Portal publication requires signingInMemoryKey"
        }
    }
}

val verifyNativeJar = tasks.register("verifyNativeJar") {
    description = "Verifies that the SDK JAR contains at least one conventionally packaged JNI library."
    group = "verification"
    val jarTask = tasks.named<Jar>("jar")
    dependsOn(jarTask)

    doLast {
        ZipFile(jarTask.get().archiveFile.get().asFile).use { jar ->
            val nativeEntries = jar.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .filter { it.startsWith("$nativeResourcePrefix/") }
                .toList()
            check(nativeEntries.isNotEmpty()) { "Published SDK JAR contains no JNI native resources" }
            if (prebuiltNativeDirectory == null) {
                check(hostNativeResourcePath in nativeEntries) {
                    "Published SDK JAR is missing host JNI resource $hostNativeResourcePath"
                }
            }
        }
    }
}

tasks.test {
    dependsOn(verifyNativeJar)
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(verifyNativeJar)
}

tasks.named("publishToMavenLocal") {
    dependsOn(verifyNativeJar)
}

tasks.register<JavaExec>("runWeatherExample") {
    description = "Runs the multi-file typed weather agent example."
    group = "application"
    dependsOn(buildNative, tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.orion.example.weather.MainKt")
}

plugins {
    kotlin("jvm") version "2.4.10"
    `maven-publish`
}

group = "dev.orion.runtime"
version = "0.1.0"

repositories {
    mavenCentral()
}

val buildNative = tasks.register<Exec>("buildNative") {
    description = "Builds the Orion JNI library with Cargo."
    group = "build"
    workingDir(rootProject.projectDir.resolve("../.."))
    commandLine("cargo", "build", "-p", "orion-kotlin", "--release", "--locked")
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("Orion Kotlin SDK")
                description.set("Kotlin SDK with an in-process JNI binding to the Orion Rust kernel")
                developers {
                    developer {
                        id.set("GtechGovind")
                        name.set("Govind Yadav")
                        email.set("gtech.govind2000@gmail.com")
                    }
                }
            }
        }
    }
}

tasks.test {
    dependsOn(buildNative)
    useJUnitPlatform()
    jvmArgs("-Djava.library.path=${rootProject.projectDir.resolve("../../target/release").canonicalPath}")
}

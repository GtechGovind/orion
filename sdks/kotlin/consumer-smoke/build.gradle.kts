plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    application
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("dev.orion.runtime:orion-kotlin-sdk:0.1.0")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("dev.orion.consumer.MainKt")
}

plugins {
    kotlin("jvm") version "2.1.20"
}

group = "dev.orion.runtime"
version = "0.0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

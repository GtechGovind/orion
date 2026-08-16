plugins {
    kotlin("jvm") version "2.4.10"
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

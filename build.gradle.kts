plugins {
    kotlin("jvm") version "2.0.10"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.vk.api:sdk:1.0.16")
    implementation("dev.inmo:tgbotapi:18.2.1")
    implementation("org.slf4j:slf4j-log4j12:2.0.16")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

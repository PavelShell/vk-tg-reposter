plugins {
    kotlin("jvm") version "2.0.10"
}

group = "com.pavelshell"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.vk.api:sdk:1.0.16")
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.2.0")
/*   Need to import this library because `kotlin-telegram-bot` returns it's type from public API.
     TODO: remove after migration to a better TG API library.*/
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.github.jfposton:yt-dlp-java:v2.0.3")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.16")
    implementation(kotlin("reflect"))

    testImplementation("org.wiremock:wiremock:3.10.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("io.mockk:mockk:1.13.13")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.pavelshell.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}

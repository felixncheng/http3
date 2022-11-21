import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.netty.incubator:netty-incubator-codec-http3:0.0.15.Final")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

if (project.hasProperty("server")) {
    application {
        mainClass.set("HttpServersKt")
    }
} else {
    application {
        mainClass.set("DownloadTestKt")
    }
}

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.2"
}

group = "dev.alexanderdiaz"
version = "1.0.7"
description = "AthenaBuild"

repositories {
    mavenLocal()
    mavenCentral()

    maven("https://repo.pgm.fyi/snapshots")
    maven("https://repo.maven.apache.org/maven2/")
}

dependencies {
    //server
    compileOnly("app.ashcon:sportpaper:1.8.8-R0.1-SNAPSHOT")

    //command
    implementation("org.incendo:cloud-core:2.0.0")
    implementation("org.incendo:cloud-annotations:2.0.0")
    implementation("org.incendo:cloud-paper:2.0.0-beta.10")
    implementation("org.incendo:cloud-minecraft-extras:2.0.0-beta.10")
    implementation("net.kyori:adventure-platform-bukkit:4.3.4")

    //lombok
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
}

tasks.named<ShadowJar>("shadowJar") {
    manifest {
        attributes["Main-Class"] = "dev.alexanderdiaz.athenabuild.AthenaBuild"
    }
    archiveBaseName = "AthenaBuild"
    archiveClassifier.set("")

    exclude("META-INF/**")
    exclude("OSGI-INF/")
    exclude("**/*.html")
    exclude("javax/**")
}

tasks.withType<JavaCompile> {
    options.release.set(17)
}

tasks {
    processResources {
        val name = project.name
        val description = project.description
        val version = project.version.toString()

        filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
            expand(
                mapOf(
                    "name" to name,
                    "description" to description,
                    "version" to version,
                )
            )
        }
    }

    named("build") {
        dependsOn(shadowJar)
    }
}
plugins {
    kotlin("jvm") version "1.9.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "burp.repeater"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.portswigger.burp.extensions:montoya-api:2023.12.1")
    implementation(kotlin("stdlib-jdk8"))
}

tasks {
    shadowJar {
        manifest {
            attributes(
                "Main-Class" to "burp.repeater.BurpExtender",
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version
            )
        }
        archiveClassifier.set("")
        mergeServiceFiles()
        minimize()
    }
}

tasks.wrapper {
    gradleVersion = "8.5"
}

import org.gradle.api.GradleException
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

plugins {
    java
}

group = "local"
version = "1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

// I have test with the main code (in srs folder)
val testPatterns = listOf(
    "**/*Test.java",
    "**/*Tests.java",
    "**/*IT.java",
    "**/*TestCase.java",
    "**/Test*.java"
)

// Including all jar from libs (libs should be in the project root)
val gameLibs = fileTree("libs") { include("*.jar") }

// If libs folder is empty or there is no jar there — fail with the correct and understandable error (no 100 “package does not exist”)
if (gameLibs.files.isEmpty()) {
    throw GradleException(
        "Game dependencies have not been found : put heroes_task-lib*.jar to the folder ./libs (in the project root)."
    )
}

dependencies {
    // for my code compilation (will not be compiled in jar)
    compileOnly(gameLibs)

    //for compilation and run of the tests
    testImplementation(gameLibs)

    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")

    //  JUnit4 test
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.0")
}

// Configures main and test source sets
sourceSets {
    named("main") {
        java {
            setSrcDirs(listOf("src"))
            exclude(testPatterns) // test are not include din main
        }
        resources { setSrcDirs(emptyList<String>()) }
    }
    named("test") {
        java {
            setSrcDirs(listOf("src"))
            include(testPatterns) // only tests are here
        }
        resources { setSrcDirs(emptyList<String>()) }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Jar>("jar") {
    archiveFileName.set("obf.jar")
}

//  copying obf.jar to the project root
tasks.register<Copy>("copyObfJarToRoot") {
    dependsOn(tasks.named("jar"))
    from(layout.buildDirectory.dir("libs")) { include("obf.jar") }
    into(rootDir)
}

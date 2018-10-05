import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    idea
    kotlin("jvm") version "1.2.71"

    id("org.springframework.boot") version "2.0.5.RELEASE"
    id("io.spring.dependency-management") version "1.0.6.RELEASE"

    // gradle dependencyUpdates -Drevision=release
    id("com.github.ben-manes.versions") version "0.20.0"
    id("com.palantir.docker") version "0.20.1"
}

repositories {
    jcenter()
}

val kotlinLoggingVer = "1.6.10"

val javaxAnnotationApiVer = "1.3.2"
val javaxTransactionApiVer = "1.3"

val testContainersVer = "1.9.1"
val jfairyVer = "0.5.9"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVer")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.testcontainers:testcontainers:$testContainersVer")
    testImplementation("io.codearte.jfairy:jfairy:$jfairyVer")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

application {
    mainClassName = "app.AppKt"
    applicationName = "app"
    version = "0.1-SNAPSHOT"
    group = "template.kotlin.spring"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_10
    targetCompatibility = JavaVersion.VERSION_1_10
}

idea {
    project {
        languageLevel = IdeaLanguageLevel(JavaVersion.VERSION_1_10)
    }
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

sourceSets.create("testUtils") {
    java.srcDir(file("src/testUtils/kotlin"))
    resources.srcDir(file("src/testUtils/resources"))

    compileClasspath += sourceSets["main"].output
    compileClasspath += configurations.testCompileClasspath

    runtimeClasspath += sourceSets["main"].output
    runtimeClasspath += configurations.testRuntimeClasspath
}

sourceSets.getByName("test") {
    compileClasspath += sourceSets["testUtils"].output
    runtimeClasspath += sourceSets["testUtils"].output
}

sourceSets.create("testIntegration") {
    java.srcDir(file("src/testIntegration/kotlin"))
    resources.srcDir(file("src/testIntegration/resources"))

    compileClasspath += sourceSets["main"].output
    compileClasspath += sourceSets["testUtils"].output
    compileClasspath += configurations.testCompileClasspath

    runtimeClasspath += sourceSets["main"].output
    runtimeClasspath += sourceSets["testUtils"].output
    runtimeClasspath += configurations.testRuntimeClasspath
}

tasks {
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_1_8.toString()
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.isFork = true
    }

    tasks.withType<Test>().configureEach {
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1

        useJUnitPlatform()
        testLogging {
            events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }

        reports.html.isEnabled = false
        reports.junitXml.isEnabled = false
    }

    task<Test>("testIntegration") {
        description = "Integration tests"
        group = "verification"
        testClassesDirs = sourceSets["testIntegration"].output.classesDirs
        classpath = sourceSets["testIntegration"].runtimeClasspath
    }

    task("testAll") {
        finalizedBy(tasks["test"], tasks["testIntegration"])
    }

    tasks.getByName<Wrapper>("wrapper") {
        gradleVersion = "4.10.2"
        distributionType = Wrapper.DistributionType.ALL
    }

    val bootJar = tasks.getByName<BootJar>("bootJar") {
        baseName = "app"

        if (project.hasProperty("archiveName")) {
            archiveName = project.properties["archiveName"] as String
        }
    }

    val build = tasks.getByName("build")

    docker {
        dependsOn(build)
        name = "${project.group}/${bootJar.baseName}"
        files(bootJar.archivePath)
        setDockerfile(file("$projectDir/src/main/docker/Dockerfile"))
        buildArgs(mapOf(
            "JAR_FILE" to bootJar.archiveName,
            "JAVA_OPTS" to "-XX:-TieredCompilation",
            "PORT" to "8080"
        ))
        pull(true)
    }

    task("stage") {
        dependsOn(build, tasks.getByName("clean"))
    }
}

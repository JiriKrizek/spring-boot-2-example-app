import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    application
    idea
    kotlin("jvm") version "1.3.72"

    id("org.springframework.boot") version "2.3.1.RELEASE"
    id("io.spring.dependency-management") version "1.0.9.RELEASE"

    // gradle dependencyUpdates -Drevision=release
    id("com.github.ben-manes.versions") version "0.28.0"
    id("com.palantir.docker") version "0.25.0"
}

repositories {
    jcenter()
    mavenCentral()
}

val javaVer = JavaVersion.VERSION_11

val kotlinLoggingVer = "1.7.10"

val javaxAnnotationApiVer = "1.3.2"
val javaxTransactionApiVer = "1.3"

val testContainersVer = "1.14.3"
val jfairyVer = "0.5.9"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVer")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")

    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.testcontainers:testcontainers:$testContainersVer")
    testImplementation("io.codearte.jfairy:jfairy:$jfairyVer")

    testImplementation("org.junit.jupiter:junit-jupiter-api")
}

val appName = "app"
val appVer by lazy { "0.0.1+${gitRev()}" }

group = "example"
version = appVer

application {
    mainClassName = "app.AppKt"
    applicationName = appName
}

java {
    sourceCompatibility = javaVer
    targetCompatibility = javaVer
}

idea {
    project {
        languageLevel = IdeaLanguageLevel(javaVer)
    }
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

springBoot {
    buildInfo {
        properties {
            artifact = "$appName-$appVer.jar"
            version = appVer
            name = appName
        }
    }
}

tasks {
    withType(KotlinCompile::class).configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_1_8.toString()
            freeCompilerArgs = listOf("-progressive")
        }
    }

    withType(JavaCompile::class).configureEach {
        options.isFork = true
    }

    withType(Test::class).configureEach {
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2)
            .takeIf { it > 0 } ?: 1

        useJUnitPlatform()
        testLogging {
            showExceptions = true
            exceptionFormat = TestExceptionFormat.FULL
            showStackTraces = true
            showCauses = true
            showStandardStreams = true
            events = setOf(
                TestLogEvent.PASSED,
                TestLogEvent.SKIPPED,
                TestLogEvent.FAILED,
                TestLogEvent.STANDARD_OUT,
                TestLogEvent.STANDARD_ERROR
            )
        }

        reports.html.isEnabled = false

        useJUnitPlatform()
    }

    wrapper {
        gradleVersion = "6.5"
        distributionType = Wrapper.DistributionType.ALL
    }

    bootJar {
        manifest {
            attributes("Multi-Release" to true)
        }

        archiveBaseName.set(appName)
        archiveVersion.set(appVer)

        if (project.hasProperty("archiveName")) {
            archiveFileName.set(project.properties["archiveName"] as String)
        }
    }

    //gradle docker -PremoteDebug
    docker {
        val build = build.get()
        val bootJar = bootJar.get()
        val dockerImageName = "${project.group}/$appName"

        dependsOn(build)

        name = "$dockerImageName:latest"
        tag("current", "$dockerImageName:$appVer")
        tag("latest", "$dockerImageName:latest")
        files(bootJar.archiveFile)
        setDockerfile(file("$projectDir/src/main/docker/Dockerfile"))
        buildArgs(mapOf(
            "JAR_FILE" to bootJar.archiveFileName.get(),
            "JAVA_OPTS" to dockerJavaOpts(project)
        ))
        pull(true)
    }

    register("stage") {
        dependsOn("build", "clean")
    }

    register<Delete>("cleanOut") {
        delete("out")
    }

    clean {
        dependsOn("cleanOut")
    }

    withType(DependencyUpdatesTask::class) {
        resolutionStrategy {
            componentSelection {
                all {
                    val rejected = listOf(
                        "alpha", "beta", "rc", "cr", "m",
                        "preview", "b", "ea", "eap"
                    ).any { q ->
                        candidate.version.matches(
                            Regex("(?i).*[.-]$q[.\\d-+]*")
                        )
                    }
                    if (rejected) {
                        reject("Release candidate")
                    }
                }
            }
        }

        checkForGradleUpdate = true
        outputFormatter = "json"
        outputDir = "build/dependencyUpdates"
        reportfileName = "report"
    }
}

fun gitRev() = ProcessBuilder("git", "rev-parse", "--short", "HEAD").start().let { p ->
    p.waitFor(100, TimeUnit.MILLISECONDS)
    p.inputStream.bufferedReader().readLine() ?: "none"
}

fun dockerJavaOpts(project: Project): String {
    val baseOpts = "-XX:-TieredCompilation -XX:MaxRAMPercentage=80"

    if (project.hasProperty("remoteDebug")) {
        project.logger.lifecycle("WARNING: Remote Debugging Enabled!")
        return "$baseOpts -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
    }

    return baseOpts
}

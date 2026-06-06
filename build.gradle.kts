plugins {
    java
    // Declared here (apply false) so child modules can apply it by id with no version.
    id("org.springframework.boot") version "3.3.5" apply false
}

allprojects {
    group = "com.skyward"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    // Java 21 (LTS); we use virtual threads on REST read paths.
    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        // Native Gradle BOM import — one place pins Spring Boot, Spring Kafka,
        // Testcontainers, Flyway, Postgres driver versions. No version numbers in
        // child modules. Preferred over the io.spring.dependency-management plugin.
        add("implementation", platform("org.springframework.boot:spring-boot-dependencies:3.3.5"))
        add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        // Docker Desktop's daemon advertises a minimum API version (1.40+) that is newer than
        // the docker-java default bundled with Testcontainers, which otherwise gets a 400. Pin a
        // version inside the supported window; 1.41 is honoured by both new and older daemons (CI).
        systemProperty("api.version", "1.41")
        environment("DOCKER_API_VERSION", "1.41")
        testLogging {
            events("passed", "skipped", "failed")
            showExceptions = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

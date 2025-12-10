import com.diffplug.gradle.spotless.SpotlessExtension
import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    id("io.spring.dependency-management") version "1.1.7"
    id("org.springframework.boot") version "3.5.4" apply false
    id("com.google.protobuf") version "0.9.5" apply false
    id("org.liquibase.gradle") version "3.0.2" apply false
    id("org.jooq.jooq-codegen-gradle") version "3.20.6" apply false
    id("com.diffplug.spotless") version "7.2.1"
//    id("com.bmuschko.docker-spring-boot-application") version "10.0.0" apply false
}

buildscript {
    val liquibaseVer: String by extra { "4.33.0" }

    dependencies {
        classpath("org.liquibase:liquibase-core:$liquibaseVer")
    }
}

subprojects {
    repositories {
        mavenCentral()
    }
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "java-library")
    apply(plugin = "checkstyle")


    the<CheckstyleExtension>().apply {
        toolVersion = "11.0.0"
    }

    val isService = project.path in setOf(
        ":orders:orders-grpc-service",
        ":payments:payments-grpc-service",
        ":reserve:reserve-grpc-service"
    )

//    if (isService) {
//        apply(plugin = "com.bmuschko.docker-spring-boot-application")
//        fun DockerExtension.`springBootApplication`(configure: Action<DockerSpringBootApplication>): Unit =
//            (this as ExtensionAware).extensions.configure("springBootApplication", configure)
//        the<DockerExtension>().apply {
//            springBootApplication {
//                baseImage.set("eclipse-temurin:25.0.1_8-jre-ubi10-minimal")
//                ports.set(listOf(8080))
//                images.set(setOf("jvm-" + project.name + ":latest"))
//            }
//        }
//    }

    dependencies {
        fun implementation(notation: Any) = add("implementation", notation)
        fun runtimeOnly(notation: Any) = add("runtimeOnly", notation)
        fun testImplementation(notation: Any) = add("testImplementation", notation)
        fun testRuntimeOnly(notation: Any) = add("testRuntimeOnly", notation)

        listOf("implementation", "annotationProcessor", "testAnnotationProcessor").forEach {
            add(
                it,
                "org.projectlombok:lombok"
            )
        }
        if (isService) {
            implementation("org.aspectj:aspectjtools:1.9.25")
//            runtimeOnly("org.aspectj:aspectjrt:1.9.25")
            runtimeOnly("org.aspectj:aspectjweaver:1.9.25")

            implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
            implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
            runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-grpc-1.6")
            runtimeOnly("io.opentelemetry:opentelemetry-exporter-otlp:1.49.0")

            implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
            implementation("org.springframework.boot:spring-boot-starter-jooq")

            implementation("org.springframework.boot:spring-boot-starter-actuator")
//            implementation("io.micrometer:micrometer-tracing")
//            implementation("io.micrometer:micrometer-tracing-bridge-otel")
            implementation("io.micrometer:micrometer-registry-prometheus")
//            implementation("io.projectreactor:reactor-core-micrometer")
            implementation("org.springframework.boot:spring-boot-starter-webflux")
            implementation("org.springframework:spring-webflux")
            implementation("org.springdoc:springdoc-openapi-starter-webflux-ui")

            implementation("io.github.danielliu1123:grpc-server-boot-starter")
            implementation("io.github.danielliu1123:grpc-starter-protovalidate")
            implementation("io.github.danielliu1123:grpc-starter-transcoding")
            implementation("io.github.danielliu1123:grpc-starter-transcoding-springdoc")

            implementation("org.springframework.boot:spring-boot-autoconfigure")

//            implementation("io.r2dbc:r2dbc-proxy")

            implementation("io.grpc:grpc-netty-shaded")
            modules {
                module("io.grpc:grpc-netty") {
                    replacedBy("io.grpc:grpc-netty-shaded", "Use Netty shaded instead of regular Netty")
                }
            }
            implementation("io.projectreactor.kafka:reactor-kafka")
            implementation("org.springframework.kafka:spring-kafka")
        }

        testImplementation("org.junit.jupiter:junit-jupiter-api")
        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-engine")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    the<DependencyManagementExtension>().apply {
        imports {
            mavenBom("io.opentelemetry:opentelemetry-bom:1.53.0")

            mavenBom("io.github.danielliu1123:grpc-starter-dependencies:3.5.4")
//            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0")
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.4")
            mavenBom("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:2.15.0")
        }

        dependencies {
            dependency("org.projectlombok:lombok:1.18.42")

            dependency("io.opentelemetry.instrumentation:opentelemetry-grpc-1.6:2.15.0-alpha")
            dependency("io.opentelemetry.instrumentation:opentelemetry-reactor-3.1:2.15.0-alpha")
            dependency("io.opentelemetry.contrib:opentelemetry-samplers:1.49.0-alpha")

            dependency("org.slf4j:slf4j-api:2.0.17")

            dependency("io.projectreactor:reactor-core:3.7.7")

            dependency("org.postgresql:postgresql:42.7.7")

            dependency("jakarta.validation:jakarta.validation-api:3.0.2")

            dependency("org.springframework:spring-web:6.2.8")
            dependency("org.springframework:spring-webflux:6.2.8")
            dependency("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.9")

            dependency("org.springframework:spring-r2dbc:6.2.8")
            dependency("org.postgresql:r2dbc-postgresql:1.0.7.RELEASE")
//            dependency("io.r2dbc:r2dbc-proxy:1.1.6.RELEASE")

            dependency("io.grpc:grpc-core:1.74.0")
            dependency("io.grpc:grpc-stub:1.74.0")
            dependency("io.grpc:grpc-protobuf:1.74.0")
            dependency("io.grpc:protoc-gen-grpc-java:1.74.0")

            dependency("com.google.protobuf:protoc:3.25.5")
            dependency("com.google.protobuf:protobuf-java:3.25.5")
            dependency("com.google.protobuf:protobuf-java-util:3.25.5")

            dependency("build.buf:protovalidate:0.2.1")

            dependency("io.projectreactor.kafka:reactor-kafka:1.3.23")

            val liquibaseVer: String by rootProject.extra
            dependency("org.liquibase:liquibase-core:${liquibaseVer}")
            dependency("info.picocli:picocli:4.7.7")

            dependency("org.jooq:jooq:3.20.6")
            dependency("org.jooq:jooq-postgres-extensions:3.20.6")

//            dependency("org.junit.jupiter:junit-jupiter:5.12.2")
        }
        the<SpotlessExtension>().apply {
            java {
                target("src/*/java/**/*.java")
                removeUnusedImports()
                endWithNewline()

                cleanthat()
                    .version("2.23")
                    .sourceCompatibility("24")
                    .addMutator("SafeAndConsensual")
                    .addMutator("SafeButNotConsensual")
                    .addMutator("SafeButControversial")
                    .excludeMutator("AvoidInlineConditionals")
                    .includeDraft(false)

                eclipse()
                    .sortMembersEnabled(true)
                    .sortMembersOrder("SF,SI,F,SM,I,C,M,T")
//                    .sortMembersDoNotSortFields(false)
//                    .sortMembersVisibilityOrderEnabled(true)
//                    .sortMembersVisibilityOrder("B,R,D,V")
                    .configFile("$rootDir/config/codestyle.xml")
            }
        }
    }
    tasks {
        findByName("checkstyleMain")?.dependsOn("spotlessApply")
        withType<Test> {
            useJUnitPlatform()
        }
    }
}

allprojects {
    tasks.findByName("assemble")?.dependsOn("spotlessApply")
}

the<SpotlessExtension>().apply {
    protobuf {
        target("$rootDir/../proto/**/*.proto")
        buf()
    }
}

import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

//import org.ec4j.gradle.EditorconfigExtension

plugins {
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf") version "0.9.5" apply false
    id("org.springframework.boot") version "3.5.4" apply false
    id("com.diffplug.spotless") version "7.2.1" apply false
//    id("org.ec4j.editorconfig") version "0.1.0" apply false
}

subprojects {
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "java-library")
//    apply(plugin = "org.ec4j.editorconfig")

    repositories {
        mavenCentral()
    }
    buildscript {
        repositories {
            mavenCentral()
        }
    }
    the<DependencyManagementExtension>().apply {
        imports {
            mavenBom("io.github.danielliu1123:grpc-starter-dependencies:3.5.4")
//            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0")
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.4")
        }

        dependencies {
            dependency("org.projectlombok:lombok:1.18.38")

            dependency("org.slf4j:slf4j-api:2.0.17")

            dependency("io.projectreactor:reactor-core:3.7.7")

            dependency("org.postgresql:postgresql:42.7.7")

            dependency("jakarta.validation:jakarta.validation-api:3.0.2")

            dependency("org.springframework:spring-web:6.2.8")
            dependency("org.springframework:spring-webflux:6.2.8")
            dependency("org.springframework:spring-r2dbc:6.2.8")
            dependency("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.9")

            dependency("io.grpc:grpc-core:1.74.0")
            dependency("io.grpc:grpc-stub:1.74.0")
            dependency("io.grpc:grpc-protobuf:1.74.0")
            dependency("io.grpc:protoc-gen-grpc-java:1.74.0")

            dependency("com.google.protobuf:protoc:3.25.5")
            dependency("com.google.protobuf:protobuf-java:3.25.5")
            dependency("com.google.protobuf:protobuf-java-util:3.25.5")

            dependency("build.buf:protovalidate:0.2.1")

            dependency("io.projectreactor.kafka:reactor-kafka:1.3.23")
        }
        the<com.diffplug.gradle.spotless.SpotlessExtension>().apply {
//            kotlinGradle {
//            }
            java {
                target("src/*/java/**/*.java")
                removeUnusedImports()
                cleanthat()
                    .version("2.23")
                    .sourceCompatibility("21")
                    .addMutator("PMD")
                    .addMutator("SafeAndConsensual")
                    .addMutator("SafeButNotConsensual")
                    .addMutator("SafeButControversial")
                    .excludeMutator("AvoidInlineConditionals")
                    .includeDraft(false)

                eclipse().configFile("$rootDir/codestyle.xml")
            }
        }

        tasks.findByName("assemble")?.dependsOn("spotlessApply")

//        the<EditorconfigExtension>().apply {
////            this.isFailOnNoMatchingProperties=false
//        }
//
//
//        tasks.findByName("check")?.dependsOn("editorconfigFormat")

    }
}

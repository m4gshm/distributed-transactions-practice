import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.kotlin.dsl.the

plugins {
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf") version "0.9.5" apply false
    id("org.springframework.boot") version "3.5.3" apply false
}

allprojects {
    apply(plugin = "io.spring.dependency-management")
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
        }

//        <dependencyManagement>
//    <dependencies>
//        <dependency>
//            <groupId>org.springframework.cloud</groupId>
//            <artifactId>spring-cloud-dependencies</artifactId>
//            <version>2023.0.2</version>
//            <type>pom</type>
//            <scope>import</scope>
//        </dependency>
//    </dependencies>
//</dependencyManagement>
        dependencies{
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
    }
}

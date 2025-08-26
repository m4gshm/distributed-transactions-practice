plugins {
    `java-library`
    id("org.springframework.boot")
    id("org.liquibase.gradle") version "3.0.2"
    id("org.jooq.jooq-codegen-gradle") version "3.19.24"
}
apply(plugin = "io.spring.dependency-management")

buildscript {
    val liquibaseVer = "4.33.0"
    dependencies {
        classpath("org.liquibase:liquibase-core:$liquibaseVer")
    }
}

sourceSets {
    main {
        java {
            srcDirs("$projectDir/build/generated-sources/jooq")
        }
    }
}

dependencies {
    val liquibaseVer = "4.33.0"

    api(project(":reactive-utils"))
    api(project(":payments:payments-storage-r2dbc"))
    api(project(":storage-api"))
    api(project(":grpc-webflux"))
    api(project(":protobuf-utils"))
    
    api(project(":jooq-r2dbc"))
    api(project(":payments:payments-grpc-api"))
    api(project(":payments:payments-event-api"))
    api(project(":tpc:tpc-grpc-service"))

    implementation("io.grpc:grpc-netty-shaded")
    implementation("com.google.protobuf:protobuf-java")

    implementation("org.liquibase:liquibase-core:$liquibaseVer")
    implementation("org.postgresql:postgresql")
    implementation("org.postgresql:r2dbc-postgresql:1.0.7.RELEASE")

    liquibaseRuntime("org.liquibase:liquibase-core:$liquibaseVer")
    liquibaseRuntime("info.picocli:picocli:4.7.7")
    liquibaseRuntime("org.postgresql:postgresql")

    jooqCodegen("org.postgresql:postgresql")

//    implementation("org.hibernate.reactive:hibernate-reactive-core:3.0.3.Final")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
//    implementation("org.jooq:jooq-meta:3.19.24")

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui")

    implementation("io.github.danielliu1123:grpc-server-boot-starter")
    implementation("io.github.danielliu1123:grpc-starter-protovalidate")
    implementation("io.github.danielliu1123:grpc-starter-transcoding")
    implementation("io.github.danielliu1123:grpc-starter-transcoding-springdoc")

    implementation("org.springframework.boot:spring-boot-autoconfigure")

    implementation("org.springframework:spring-webflux")

    implementation("io.projectreactor.kafka:reactor-kafka")
    implementation("org.springframework.kafka:spring-kafka")

    modules {
        module("io.grpc:grpc-netty") {
            replacedBy("io.grpc:grpc-netty-shaded", "Use Netty shaded instead of regular Netty")
        }
    }
}

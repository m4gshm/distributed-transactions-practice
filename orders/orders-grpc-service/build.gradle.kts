plugins {
    `java-library`
    id("org.springframework.boot")
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    val liquibaseVer = "4.33.0"

    api(project(":storage-api"))
    api(project(":grpc-webflux"))
    api(project(":grpc-client"))
    api(project(":protobuf-utils"))
    
    api(project(":orders:orders-grpc-api"))
    api(project(":orders:orders-storage-r2dbc"))
    api(project(":payments:payments-grpc-api"))
    api(project(":payments:payments-event-api"))
    api(project(":reserve:reserve-grpc-api"))
    api(project(":tpc:tpc-grpc-api"))
    api(project(":idempotent-consumer"))

    implementation("io.grpc:grpc-netty-shaded")

    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")

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

//    implementation(platform("org.springframework.grpc:spring-grpc-dependencies:0.9.0"))

    modules {
        module("io.grpc:grpc-netty") {
            replacedBy("io.grpc:grpc-netty-shaded", "Use Netty shaded instead of regular Netty")
        }
    }
}

plugins {
    `java-library`
    id("org.springframework.boot")
}
apply(plugin = "io.spring.dependency-management")

dependencies {

    implementation("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    api(project(":reserve:reserve-grpc-api"))
    implementation("io.grpc:grpc-netty-shaded:1.72.0")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")

    implementation(platform("io.github.danielliu1123:grpc-starter-dependencies:3.5.3.1"))
    implementation("io.github.danielliu1123:grpc-server-boot-starter")
    implementation("io.github.danielliu1123:grpc-starter-transcoding")
    implementation("io.github.danielliu1123:grpc-starter-transcoding-springdoc")

    implementation("org.springframework.boot:spring-boot-autoconfigure")

    modules {
        module("io.grpc:grpc-netty") {
            replacedBy("io.grpc:grpc-netty-shaded", "Use Netty shaded instead of regular Netty")
        }
    }
}
import com.bmuschko.gradle.docker.DockerConventionJvmApplicationPlugin
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

plugins {
    `java-library`
    id("org.springframework.boot")
    id("docker-conventions")
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":jfr-rest-api"))
    api(project(":async-profile-rest-api"))
    api(project(":grpc-service-common"))
    api(project(":grpc-client"))
    api(project(":protobuf-utils"))
    api(project(":postgres-prepared-transaction-jdbc"))
    api(project(":orders:orders-storage-jooq-jdbc"))
    api(project(":orders:orders-storage-kotlin-sqlc"))
    api(project(":orders:orders-grpc-api"))
    api(project(":orders:orders-grpc-service-common"))
    api(project(":payments:payments-grpc-api"))
    api(project(":payments:payments-event-api"))
    api(project(":reserve:reserve-grpc-api"))
    api(project(":tpc:tpc-grpc-api"))
    api(project(":tpc:tpc-grpc-service-sync"))
    api(project(":idempotent-consumer-jdbc"))

    implementation("io.grpc:grpc-netty")

    implementation("org.springframework.boot:spring-boot-kafka")

    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    compileOnly("io.opentelemetry:opentelemetry-exporter-otlp")
}

tasks.named<Dockerfile>(DockerConventionJvmApplicationPlugin.DOCKERFILE_TASK_NAME) {
    exposePort(7080, 9080)
}

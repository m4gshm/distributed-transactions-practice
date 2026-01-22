import com.bmuschko.gradle.docker.DockerConventionJvmApplicationPlugin
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

plugins {
    `java-library`
    id("org.springframework.boot")
    id("docker-conventions")
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":storage-api-reactive"))
    api(project(":grpc-service-webflux"))
    api(project(":protobuf-utils"))
    api(project(":tpc:tpc-grpc-service"))
    api(project(":postgres-prepared-transaction-r2dbc"))

    api(project(":payments:payments-storage-r2dbc"))
    api(project(":payments:payments-grpc-api"))
    api(project(":payments:payments-event-api"))

    implementation("org.springframework.boot:spring-boot-kafka")
}

tasks.named<Dockerfile>(DockerConventionJvmApplicationPlugin.DOCKERFILE_TASK_NAME) {
    exposePort(7082, 9082)
}

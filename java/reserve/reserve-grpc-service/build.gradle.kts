import com.bmuschko.gradle.docker.DockerConventionJvmApplicationPlugin
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import org.gradle.kotlin.dsl.named

plugins {
    `java-library`
    id("org.springframework.boot")
    id("docker-conventions")
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":reactive-utils"))
    api(project(":storage-api"))
    api(project(":grpc-webflux"))
    api(project(":protobuf-utils"))

    api(project(":reserve:reserve-storage-r2dbc"))
    api(project(":reserve:reserve-grpc-api"))
    api(project(":tpc:tpc-grpc-service"))

    implementation("io.projectreactor.kafka:reactor-kafka")
    implementation("org.springframework.kafka:spring-kafka")
}

tasks.named<Dockerfile>(DockerConventionJvmApplicationPlugin.DOCKERFILE_TASK_NAME) {
    exposePort(7081, 9081)
}


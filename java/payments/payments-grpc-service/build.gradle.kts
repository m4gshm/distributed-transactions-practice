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
    api(project(":storage-api"))
    api(project(":grpc-webflux"))
    api(project(":protobuf-utils"))

    api(project(":payments:payments-storage-r2dbc"))
    api(project(":payments:payments-grpc-api"))
    api(project(":payments:payments-event-api"))
    api(project(":tpc:tpc-grpc-service"))
}

tasks.named<Dockerfile>(DockerConventionJvmApplicationPlugin.DOCKERFILE_TASK_NAME) {
    exposePort(7082, 9082)
}

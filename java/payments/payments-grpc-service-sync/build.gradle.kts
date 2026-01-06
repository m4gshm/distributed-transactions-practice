import com.bmuschko.gradle.docker.DockerConventionJvmApplicationPlugin
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

plugins {
    `java-library`
    id("org.springframework.boot")
    id("docker-conventions")
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":storage-api"))
    api(project(":grpc-common"))
    api(project(":protobuf-utils"))
//    api(project(":postgres-prepared-transaction"))
    api(project(":postgres-prepared-transaction-jdbc"))

    api(project(":payments:payments-storage-jdbc"))
    api(project(":payments:payments-grpc-api"))
    api(project(":payments:payments-event-api"))

    api(project(":tpc:tpc-grpc-service-sync"))

    implementation("org.springframework.boot:spring-boot-kafka")
}

tasks.named<Dockerfile>(DockerConventionJvmApplicationPlugin.DOCKERFILE_TASK_NAME) {
    exposePort(7082, 9082)
}

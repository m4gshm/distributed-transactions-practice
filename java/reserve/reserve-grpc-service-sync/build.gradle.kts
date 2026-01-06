import com.bmuschko.gradle.docker.DockerConventionJvmApplicationPlugin
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

plugins {
    `java-library`
    id("org.springframework.boot")
    id("docker-conventions")
}
apply(plugin = "io.spring.dependency-management")

dependencies {
//    api(project(":storage-reactive-api"))
    api(project(":grpc-common"))
    api(project(":protobuf-utils"))
    api(project(":reserve:reserve-grpc-service-common"))

    api(project(":postgres-prepared-transaction-jdbc"))

    api(project(":reserve:reserve-storage-jdbc"))
    api(project(":reserve:reserve-grpc-api"))

    api(project(":tpc:tpc-grpc-service-sync"))

//    api(project(":reactive-utils"))
//    api(project(":jooq-reactive"))
//    api(project(":reserve:reserve-storage-jooq"))
//    api(project(":postgres-prepared-transaction"))

//    implementation("io.projectreactor.kafka:reactor-kafka")
    implementation("org.springframework.kafka:spring-kafka")
}

tasks.named<Dockerfile>(DockerConventionJvmApplicationPlugin.DOCKERFILE_TASK_NAME) {
    exposePort(7081, 9081)
}

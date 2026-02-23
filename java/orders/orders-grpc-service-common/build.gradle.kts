import com.bmuschko.gradle.docker.DockerConventionJvmApplicationPlugin
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

plugins {
    `java-library`
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":storage-api-common"))
    api(project(":grpc-service-common"))
    api(project(":orders:orders-storage-jooq"))
    api(project(":protobuf-utils"))
    api(project(":orders:orders-grpc-api"))
    api(project(":payments:payments-grpc-api"))
    api(project(":reserve:reserve-grpc-api"))
    api(project(":tpc:tpc-grpc-api"))
}

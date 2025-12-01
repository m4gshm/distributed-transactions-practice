plugins {
    `java-library`
    id("org.springframework.boot")
    id("com.bmuschko.docker-spring-boot-application")
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

docker {
    springBootApplication {
        baseImage.set("eclipse-temurin:25.0.1_8-jre-ubi10-minimal")
        ports.set(listOf(7082, 9082))
        images.set(setOf("jvm-" + project.name + ":latest"))
    }
}

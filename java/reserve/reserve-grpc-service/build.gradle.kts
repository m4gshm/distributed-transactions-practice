plugins {
    `java-library`
    id("org.springframework.boot")
    id("com.bmuschko.docker-spring-boot-application")
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

docker {
    springBootApplication {
        baseImage.set("eclipse-temurin:25.0.1_8-jre-ubi10-minimal")
        ports.set(listOf(7081, 9081))
        images.set(setOf("jvm-" + project.name + ":latest"))
    }
}


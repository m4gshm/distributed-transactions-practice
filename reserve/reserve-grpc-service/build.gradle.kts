plugins {
    `java-library`
    id("org.springframework.boot")
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

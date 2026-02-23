plugins {
    `java-library`
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":protobuf-utils"))

    api(project(":reserve:reserve-storage-jooq"))
    api(project(":reserve:reserve-grpc-api"))

}


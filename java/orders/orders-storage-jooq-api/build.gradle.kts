plugins {
    `java-library`
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":storage-api"))
    api(project(":orders:orders-storage-jooq"))
}

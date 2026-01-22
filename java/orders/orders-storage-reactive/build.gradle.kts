plugins {
    `java-library`
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":reactive-utils"))
    api(project(":storage-api-reactive"))
    api(project(":orders:orders-storage-jooq"))

}

plugins {
    `java-library`
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":reactive-utils"))
    api(project(":storage-reactive-api"))
    api(project(":orders:orders-storage-jooq"))

}

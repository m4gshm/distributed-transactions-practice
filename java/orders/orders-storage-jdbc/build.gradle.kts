plugins {
    `java-library`
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":orders:orders-storage-reactive"))
    api(project(":orders:orders-storage-jooq"))
    api(project(":reactive-utils"))
    api(project(":storage-api"))
    api("org.springframework.boot:spring-boot-starter-jooq")
    implementation("io.opentelemetry:opentelemetry-context")
}

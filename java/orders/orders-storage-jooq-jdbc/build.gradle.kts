plugins {
    `java-library`
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":orders:orders-storage-jooq-api"))
    api(project(":orders:orders-storage-jooq"))
    api("org.springframework.boot:spring-boot-starter-jooq")
    implementation("io.opentelemetry:opentelemetry-context")
}

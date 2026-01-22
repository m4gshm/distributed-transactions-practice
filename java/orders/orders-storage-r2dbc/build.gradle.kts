plugins {
    `java-library`
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":orders:orders-storage-reactive"))
    api(project(":orders:orders-storage-jooq"))
    api(project(":reactive-utils"))
    api(project(":storage-api-reactive"))

    api(project(":jooq-r2dbc"))
    api(project(":postgres-prepared-transaction"))

//    api("jakarta.validation:jakarta.validation-api")

    api("org.postgresql:r2dbc-postgresql")

    implementation("io.opentelemetry:opentelemetry-context")

    api("org.springframework.boot:spring-boot-starter-data-r2dbc")
    api("org.springframework.boot:spring-boot-starter-jooq")
}

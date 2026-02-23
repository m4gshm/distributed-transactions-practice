plugins {
    `java-library`
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":reactive-utils"))
    api(project(":storage-api-reactive"))
    api(project(":payments:payments-storage-jooq"))

    api(project(":jooq"))
    api(project(":postgres-prepared-transaction"))

    implementation("jakarta.validation:jakarta.validation-api")

    implementation("org.liquibase:liquibase-core")
    implementation("org.postgresql:postgresql")

    implementation("org.springframework.boot:spring-boot-starter-jooq")
}

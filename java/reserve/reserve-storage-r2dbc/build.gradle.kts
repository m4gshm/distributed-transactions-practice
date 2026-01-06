plugins {
    `java-library`
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":reserve:reserve-storage-jooq"))
    api(project(":reactive-utils"))
    api(project(":storage-reactive-api"))

    api(project(":jooq-r2dbc"))
    api(project(":postgres-prepared-transaction"))

    implementation("jakarta.validation:jakarta.validation-api")

    implementation("org.liquibase:liquibase-core")
    implementation("org.postgresql:postgresql")
    implementation("org.postgresql:r2dbc-postgresql")

    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
}

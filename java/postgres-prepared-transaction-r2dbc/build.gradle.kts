plugins {
    `java-library`
}

dependencies {
    api(project(":postgres-prepared-transaction"))
    api(project(":storage-reactive-jooq"))
    api(project(":jooq-r2dbc"))
    api(project(":postgres-r2dbc"))

    implementation("org.slf4j:slf4j-api")
    implementation("org.jooq:jooq")
    implementation("io.projectreactor:reactor-core")
    implementation("org.postgresql:r2dbc-postgresql")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
}

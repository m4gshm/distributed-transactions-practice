plugins {
    `java-library`
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":idempotent-consumer"))
    api(project(":storage-reactive-api"))
//    api(project(":jooq-r2dbc"))
//    api(project(":r2dbc-postgres"))

    implementation("io.projectreactor:reactor-core")

    implementation("org.postgresql:postgresql")
//    implementation("org.postgresql:r2dbc-postgresql")

//    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    implementation("org.jooq:jooq")
    implementation("org.jooq:jooq-postgres-extensions")
}


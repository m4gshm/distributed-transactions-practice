plugins {
    `java-library`
}

dependencies {
    api(project(":storage-api"))
//    api(project(":storage-reactive-jooq"))
//    api(project(":jooq-r2dbc"))
//    api(project(":r2dbc-postgres"))

    implementation("org.slf4j:slf4j-api")
//    implementation("org.jooq:jooq")
//    implementation("io.projectreactor:reactor-core")
//    implementation("org.postgresql:r2dbc-postgresql")
//    implementation("org.springframework.boot:spring-boot-autoconfigure")
}

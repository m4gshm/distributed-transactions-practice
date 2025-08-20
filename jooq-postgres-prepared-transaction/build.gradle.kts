plugins {
    `java-library`
}

dependencies {
    api(project(":storage-jooq"))
    api(project(":jooq-r2dbc"))
    
    implementation("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    implementation("org.slf4j:slf4j-api")
    implementation("org.jooq:jooq")
    implementation("io.projectreactor:reactor-core")
    implementation("org.postgresql:r2dbc-postgresql")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
}

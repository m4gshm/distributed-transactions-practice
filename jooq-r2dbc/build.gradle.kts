plugins {
    `java-library`
}

dependencies {
    implementation("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    implementation("org.slf4j:slf4j-api")
    implementation("org.jooq:jooq:3.19.24")
    implementation("io.projectreactor:reactor-core")
    implementation("org.springframework:spring-r2dbc")
    implementation("org.springframework.boot:spring-boot-autoconfigure:3.5.3")
}
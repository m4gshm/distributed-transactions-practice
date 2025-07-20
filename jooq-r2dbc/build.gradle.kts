plugins {
    `java-library`
}

dependencies {
    implementation("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")

    implementation("org.jooq:jooq:3.19.24")
    implementation("io.projectreactor:reactor-core:3.7.7")
    implementation("org.springframework:spring-r2dbc:6.2.8")
    implementation("org.springframework.boot:spring-boot-autoconfigure:3.5.3")
}
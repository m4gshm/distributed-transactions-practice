plugins {
    `java-library`
}

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.jooq:jooq:3.19.24")
    implementation("io.projectreactor:reactor-core")
//    implementation("org.springframework:spring-r2dbc")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-r2dbc")
    implementation("io.projectreactor:reactor-core-micrometer")
    implementation("io.opentelemetry:opentelemetry-context")
}

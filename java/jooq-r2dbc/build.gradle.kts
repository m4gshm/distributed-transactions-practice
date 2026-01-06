plugins {
    `java-library`
}

dependencies {
    api(project(":jooq"))
    api(project(":tracing"))
    api(project(":jooq-reactive"))
    implementation("org.slf4j:slf4j-api")
    implementation("org.jooq:jooq")
    implementation("io.projectreactor:reactor-core")
//    implementation("org.springframework:spring-r2dbc")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-r2dbc")
    compileOnly("org.springframework.boot:spring-boot-jooq")
    implementation("io.projectreactor:reactor-core-micrometer")
    implementation("io.opentelemetry:opentelemetry-context")
    implementation("io.opentelemetry:opentelemetry-api")
    implementation("io.opentelemetry.instrumentation:opentelemetry-reactor-3.1")
}

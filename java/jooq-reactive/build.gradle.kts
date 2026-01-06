plugins {
    `java-library`
}

dependencies {
    api(project(":jooq"))
    api(project(":tracing"))
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-jdbc")

    implementation("io.projectreactor:reactor-core")
    implementation("io.opentelemetry.instrumentation:opentelemetry-reactor-3.1")
    //autoconfig order
    compileOnly("org.springframework.boot:spring-boot-jooq")
}

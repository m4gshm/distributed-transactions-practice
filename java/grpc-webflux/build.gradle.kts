plugins {
    `java-library`
}

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("io.grpc:grpc-stub")
//    implementation("io.projectreactor:reactor-core")
    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-webflux")
    implementation("io.github.danielliu1123:grpc-server-boot-autoconfigure")
    implementation("io.github.danielliu1123:grpc-transcoding")
    implementation("io.opentelemetry:opentelemetry-api")
    implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
    implementation("io.opentelemetry.instrumentation:opentelemetry-grpc-1.6")
    implementation("io.opentelemetry.instrumentation:opentelemetry-reactor-3.1")
    implementation("io.opentelemetry.contrib:opentelemetry-samplers")
//    implementation("io.opentelemetry.semconv:opentelemetry-semconv")
//    implementation("io.micrometer:micrometer-core")
//    implementation("io.projectreactor:reactor-core-micrometer")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")

    //todo need refactor
    //for error handling
    compileOnly("org.jooq:jooq:3.19.24")
}

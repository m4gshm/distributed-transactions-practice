plugins {
    `java-library`
}

dependencies {
    api(project(":grpc-common"))

    implementation("org.slf4j:slf4j-api")
    implementation("io.grpc:grpc-stub")
    implementation("io.grpc:grpc-netty")
    implementation("io.projectreactor.netty:reactor-netty-core")
    implementation("io.projectreactor.netty:reactor-netty-http")
//    implementation("org.springframework:spring-web")
//    implementation("org.springframework:spring-webflux")
    implementation("io.github.danielliu1123:grpc-server-boot-autoconfigure")
    implementation("io.github.danielliu1123:grpc-transcoding")

    implementation("io.opentelemetry:opentelemetry-api")
    compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
    implementation("io.opentelemetry.instrumentation:opentelemetry-grpc-1.6")
    implementation("io.opentelemetry.instrumentation:opentelemetry-reactor-3.1")
    implementation("io.opentelemetry.contrib:opentelemetry-samplers")

    compileOnly("org.postgresql:r2dbc-postgresql")
    compileOnly("org.jooq:jooq")

    compileOnly("org.springframework.boot:spring-boot-r2dbc")
    implementation("org.springframework.boot:spring-boot-webflux")
    implementation("org.springframework.boot:spring-boot-reactor-netty")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    implementation("io.micrometer:micrometer-core")

//    compileOnly("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
//    compileOnly("io.opentelemetry:opentelemetry-exporter-otlp")
    compileOnly("io.micrometer:micrometer-tracing")
    compileOnly("org.springframework.boot:spring-boot-micrometer-observation")
}

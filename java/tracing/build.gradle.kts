plugins {
    `java-library`
}

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-context")
    implementation("io.projectreactor:reactor-core")
    implementation("org.springframework.boot:spring-boot-micrometer-observation")

    implementation("io.grpc:grpc-api")
    implementation("io.micrometer:micrometer-observation")
    implementation("io.micrometer:micrometer-core")
    implementation("io.opentelemetry:opentelemetry-api")
    implementation("io.opentelemetry:opentelemetry-context")
    implementation("io.opentelemetry.instrumentation:opentelemetry-grpc-1.6")
    implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
    implementation("io.opentelemetry.contrib:opentelemetry-samplers")
    implementation("io.micrometer:micrometer-tracing")

    compileOnly("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    compileOnly("io.opentelemetry:opentelemetry-exporter-sender-grpc-managed-channel")
    compileOnly("io.opentelemetry:opentelemetry-exporter-common")
    compileOnly("io.opentelemetry:opentelemetry-exporter-otlp")
    compileOnly("org.apache.tomcat.embed:tomcat-embed-core")
    compileOnly("io.grpc:grpc-netty")
    api(project(":grpc-client"))
}

plugins {
    `java-library`
}

dependencies {
    implementation("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")

    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("io.grpc:grpc-stub:1.72.0")
//    implementation("io.projectreactor:reactor-core:3.7.7")
    implementation("org.springframework:spring-web:6.2.8")
    implementation("org.springframework:spring-webflux:6.2.8")
    implementation("io.github.danielliu1123:grpc-server-boot-autoconfigure:3.5.3.1")
    implementation("io.github.danielliu1123:grpc-transcoding:3.5.3.1")

    //todo need refactor
    //for error handling
    compileOnly("org.jooq:jooq:3.19.24")
}
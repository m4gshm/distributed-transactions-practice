plugins {
    `java-library`
}

dependencies {
    implementation("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("io.grpc:grpc-stub")
//    implementation("io.projectreactor:reactor-core:3.7.7")
    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-webflux")
    implementation("io.github.danielliu1123:grpc-server-boot-autoconfigure")
    implementation("io.github.danielliu1123:grpc-transcoding")

    //todo need refactor
    //for error handling
    compileOnly("org.jooq:jooq:3.19.24")
}
plugins {
    `java-library`
}

dependencies {
    implementation("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    implementation("jakarta.validation:jakarta.validation-api")

    implementation("org.slf4j:slf4j-api")
    implementation("io.projectreactor:reactor-core")
    implementation("org.springframework:spring-web")
}
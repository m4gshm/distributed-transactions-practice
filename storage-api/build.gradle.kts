plugins {
    `java-library`
}

dependencies {
    implementation("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")

    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("io.projectreactor:reactor-core:3.7.7")
}
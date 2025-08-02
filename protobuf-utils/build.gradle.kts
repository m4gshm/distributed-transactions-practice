plugins {
    `java-library`
}

dependencies {
    implementation("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("com.google.protobuf:protobuf-java")
}
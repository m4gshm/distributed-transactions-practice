plugins {
    `java-library`
}

dependencies {
    implementation(project(":storage-api"))
    implementation("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    implementation("org.slf4j:slf4j-api")
    implementation("org.jooq:jooq:3.19.24")
    implementation("io.projectreactor:reactor-core")
}
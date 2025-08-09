plugins {
    `java-library`
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    implementation("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}
plugins {
    `java-library`
}
//apply(plugin = "io.spring.dependency-management")

dependencies {

    implementation("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")

    api(project(":tpc:tpc-grpc-api"))
    api(project(":grpc-reactor"))
    api(project(":jooq-utils"))

    implementation("org.jooq:jooq:3.19.24")
    implementation("io.grpc:grpc-stub:1.72.0")
    implementation("com.google.protobuf:protobuf-java:3.25.5")
    implementation("io.projectreactor:reactor-core:3.7.7")
    implementation("org.springframework:spring-context:6.2.8")
    implementation("org.springframework.boot:spring-boot-autoconfigure:3.5.3")

}
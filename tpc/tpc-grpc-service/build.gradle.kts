plugins {
    `java-library`
}
//apply(plugin = "io.spring.dependency-management")

dependencies {

    implementation("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    api(project(":tpc:tpc-grpc-api"))
    api(project(":jooq-postgres-prepared-transaction"))
    api(project(":grpc-webflux"))
    

    implementation("org.jooq:jooq:3.19.24")
    implementation("io.grpc:grpc-stub")
    implementation("com.google.protobuf:protobuf-java")
    implementation("io.projectreactor:reactor-core")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-autoconfigure:3.5.3")

}
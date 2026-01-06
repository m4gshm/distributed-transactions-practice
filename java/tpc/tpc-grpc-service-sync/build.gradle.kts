plugins {
    `java-library`
}

dependencies {
    api(project(":tpc:tpc-grpc-api"))
    api(project(":postgres-prepared-transaction-jdbc"))
    api(project(":grpc-common"))

    implementation("org.jooq:jooq")
    implementation("io.grpc:grpc-stub")
    implementation("com.google.protobuf:protobuf-java")
    implementation("io.projectreactor:reactor-core")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
}

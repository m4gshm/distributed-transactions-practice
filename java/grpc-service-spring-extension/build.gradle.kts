dependencies {
    api(project(":grpc-service-common"))

    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.jooq:jooq")

    implementation("org.springframework.grpc:spring-grpc-spring-boot-starter")

    compileOnly("io.grpc:grpc-netty")
    compileOnly("io.grpc:grpc-okhttp")
}

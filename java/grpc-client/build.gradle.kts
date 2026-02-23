dependencies {
    api(project(":grpc-utils"))
    implementation("org.slf4j:slf4j-api")
    implementation("io.grpc:grpc-api")
    implementation("io.grpc:grpc-core")
    compileOnly("io.grpc:grpc-netty")
    compileOnly("io.grpc:grpc-okhttp")
    implementation("org.springframework:spring-beans")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
}

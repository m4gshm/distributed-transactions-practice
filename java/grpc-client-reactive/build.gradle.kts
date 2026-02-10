plugins {
    `java-library`
}

dependencies {
    api(project(":grpc-client"))
    implementation("org.slf4j:slf4j-api")
    implementation("io.grpc:grpc-api")
    implementation("io.grpc:grpc-core")
    compileOnly("io.grpc:grpc-netty")
    implementation("io.projectreactor.netty:reactor-netty-core")
    implementation("org.springframework:spring-beans")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-autoconfigure")

}

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("io.grpc:grpc-api")
    implementation("io.grpc:grpc-core")

    compileOnly("io.grpc:grpc-netty")
}

dependencies {
    api(project(":tracing"))
    api(project(":grpc-utils"))

    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-beans")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-webmvc")
    implementation("io.github.danielliu1123:grpc-server-boot-autoconfigure")
    implementation("io.github.danielliu1123:grpc-transcoding")
    implementation("io.grpc:grpc-api")
    implementation("io.grpc:grpc-netty")
    implementation("io.grpc:grpc-core")
    implementation("io.grpc:grpc-stub")
    implementation("org.jooq:jooq")

    compileOnly("org.apache.tomcat.embed:tomcat-embed-core")

}

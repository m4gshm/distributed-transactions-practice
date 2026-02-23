dependencies {
    api(project(":grpc-service-common"))

    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-webmvc")
    implementation("org.jooq:jooq")

    compileOnly("io.grpc:grpc-netty")
    compileOnly("io.grpc:grpc-okhttp")

    compileOnly("io.github.danielliu1123:grpc-server-boot-autoconfigure")
    compileOnly("io.github.danielliu1123:grpc-transcoding")
    compileOnly("org.apache.tomcat.embed:tomcat-embed-core")

}

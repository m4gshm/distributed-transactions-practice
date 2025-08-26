plugins {
    `jvm-test-suite`
}

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            dependencies {
                annotationProcessor("org.projectlombok:lombok")
                compileOnly("org.projectlombok:lombok")

                implementation("org.junit.jupiter:junit-jupiter")
                implementation("org.junit.jupiter:junit-jupiter-engine")
                runtimeOnly("org.junit.platform:junit-platform-launcher")
                implementation("io.grpc:grpc-testing")
                implementation("io.grpc:grpc-netty-shaded")
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation("com.google.protobuf:protobuf-java-util")

                implementation(project(":orders:orders-grpc-api"))
                implementation(project(":reserve:reserve-grpc-api"))
                implementation(project(":payments:payments-grpc-api"))
                implementation(project(":payments:payments-storage-r2dbc"))
                implementation(project(":grpc-client"))
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

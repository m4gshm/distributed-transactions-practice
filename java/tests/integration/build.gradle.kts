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

                implementation(projects.tests.commons)
                implementation(projects.orders.ordersGrpcApi)
                implementation(projects.reserve.reserveGrpcApi)
                implementation(projects.payments.paymentsGrpcApi)
                implementation(projects.payments.paymentsStorageR2dbc)
                implementation(projects.grpcClient)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

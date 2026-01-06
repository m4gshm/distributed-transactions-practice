plugins {
    `jvm-test-suite`
}

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            dependencies {
                annotationProcessor("org.projectlombok:lombok")
                compileOnly("org.projectlombok:lombok")
                compileOnly("io.netty:netty-transport")

                implementation("org.junit.jupiter:junit-jupiter")
                implementation("org.junit.jupiter:junit-jupiter-engine")
                runtimeOnly("org.junit.platform:junit-platform-launcher")
                implementation("io.grpc:grpc-testing")
                implementation("io.grpc:grpc-netty")
//                implementation("io.grpc:grpc-netty-shaded")
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation("com.google.protobuf:protobuf-java-util")
                implementation("io.projectreactor.netty:reactor-netty-core")
                implementation("io.opentelemetry:opentelemetry-api")

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

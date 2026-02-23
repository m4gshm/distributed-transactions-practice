dependencies {
    implementation(projects.orders.ordersGrpcApi)
    implementation(projects.payments.paymentsGrpcApi)
    implementation(projects.reserve.reserveGrpcApi)
    implementation(projects.grpcClient)
    implementation("com.google.protobuf:protobuf-java-util")
    implementation("io.grpc:grpc-netty")
}

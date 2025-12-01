dependencies {
    implementation(projects.orders.ordersGrpcApi)
    implementation(projects.payments.paymentsGrpcApi)
    implementation(projects.reserve.reserveGrpcApi)
    implementation("com.google.protobuf:protobuf-java-util")
}
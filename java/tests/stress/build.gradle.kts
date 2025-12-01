import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    id("io.gatling.gradle") version "3.14.9"
}

dependencies {
    gatlingAnnotationProcessor("org.projectlombok:lombok")
    gatlingCompileOnly("org.projectlombok:lombok")
    gatling("com.google.protobuf:protobuf-java-util")
    gatling(projects.tests.commons)
    gatling(projects.orders.ordersGrpcApi)
    gatling(projects.reserve.reserveGrpcApi)
    gatling(projects.payments.paymentsGrpcApi)
}

the<DependencyManagementExtension>().apply {
    imports {
        mavenBom("io.netty:netty-bom:4.2.1.Final")
    }
}

gatling {
//    jvmArgs = listOf(
////    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005",
//    "--add-opens=java.base/java.lang=ALL-UNNAMED",
//    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
//    )
}
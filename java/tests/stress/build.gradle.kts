import io.gatling.gradle.GatlingRunTask
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

}

tasks.register<GatlingRunTask>("gatlingRunGo") {
    dependsOn(project.tasks.named("gatlingClasses"))
    group = "Gatling"
    environment = mapOf(
        "ORDER_URL" to "http://localhost:8001",
        "ACCOUNT_URL" to "http://localhost:8002",
        "WAREHOUSE_URL" to "http://localhost:8003",
    )
}
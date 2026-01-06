import io.gatling.gradle.GatlingRunTask
import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    id("io.gatling.gradle") version "3.14.9"
}

dependencies {
    gatlingAnnotationProcessor("org.projectlombok:lombok")
    gatlingCompileOnly("org.projectlombok:lombok")
    gatling("com.google.protobuf:protobuf-java-util")
    gatling("io.gatling:gatling-grpc-java:3.14.9.1")
    gatling(project(":grpc-client"))
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

tasks.named<GatlingRunTask>("gatlingRun") {
    dependsOn(project.tasks.named("gatlingClasses"))
    group = "Gatling"
    simulationClassName = "io.github.m4gshm.tests.stress.gatling.OrderFlowSimulation"
    this.isRunInSameProcess = true
    this.runInSameProcess = true
}

tasks.register<GatlingRunTask>("gatlingRunGrpc") {
    dependsOn(project.tasks.named("gatlingClasses"))
    group = "Gatling"
    environment = goEnv
    simulationClassName = "io.github.m4gshm.tests.stress.gatling.OrderFlowSimulationGrpc"
}

val goEnv = mapOf(
    "ORDER_URL" to "http://localhost:8001",
    "ACCOUNT_URL" to "http://localhost:8002",
    "ACCOUNT_ADDRESS" to "localhost:9002",
    "WAREHOUSE_URL" to "http://localhost:8003",
    "WAREHOUSE_ADDRESS" to "localhost:9003",
)

tasks.register<GatlingRunTask>("gatlingRunGo") {
    dependsOn(project.tasks.named("gatlingClasses"))
    group = "Gatling"
    environment = goEnv
    simulationClassName = "io.github.m4gshm.tests.stress.gatling.OrderFlowSimulation"
}

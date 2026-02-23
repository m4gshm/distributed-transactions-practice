plugins {
    java
}

val liquibaseRuntime by configurations.creating

dependencies {
    implementation("org.liquibase:liquibase-core")
    liquibaseRuntime("org.liquibase:liquibase-core")
    liquibaseRuntime("org.postgresql:postgresql")
}

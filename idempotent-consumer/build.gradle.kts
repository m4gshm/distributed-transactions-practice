plugins {
    `java-library`
    id("org.liquibase.gradle")
    id("org.jooq.jooq-codegen-gradle")
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":jooq-r2dbc"))

    implementation("org.postgresql:postgresql")
    implementation("org.postgresql:r2dbc-postgresql")

    liquibaseRuntime("org.liquibase:liquibase-core")
    liquibaseRuntime("info.picocli:picocli")
    liquibaseRuntime("org.postgresql:postgresql")

    jooqCodegen("org.postgresql:postgresql")

    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    implementation("org.jooq:jooq")
    implementation("org.jooq:jooq-postgres-extensions")
}

val dbSchema = "public"
val dbUsername = "postgres"
val dbPassword = "postgres"
val dbUrl = "jdbc:postgresql://localhost:5000/idempotent_consumer"

liquibase.activities.register("main") {
    arguments = mapOf<String, Any?>(
        "searchPath" to "${project.projectDir}/src/main/liquibase/",
        "changelogFile" to requiredProperty("changeLogFile", "db/changelog/db.changelog-master.yaml"),
        "url" to requiredProperty("dbUrl", dbUrl),
        "username" to requiredProperty("dbUsername", dbUsername),
        "password" to requiredProperty("dbPassword", dbPassword),
        "liquibaseSchemaName" to requiredProperty("dbSchema", dbSchema),
        "defaultSchemaName" to requiredProperty("dbSchema", dbSchema),
        "logLevel" to "DEBUG",
    ) + listOf(
        "count"
    ).map { it to project.findProperty(it) }.filter { it.second != null }
}

jooq {
    configuration {
        logging = org.jooq.meta.jaxb.Logging.DEBUG
        jdbc {
            driver = "org.postgresql.Driver"
            url = dbUrl
            user = dbUsername
            password = dbPassword
        }
        generator {
            name = "org.jooq.codegen.DefaultGenerator"
            database {
                inputSchema = "public"
                isOutputSchemaToDefault = true
                name = "org.jooq.meta.postgres.PostgresDatabase"
                includes = "public.*"
                excludes = "databasechangelog|databasechangeloglock"
            }
            target {
                this.directory = "$projectDir/src/main/java"
                packageName = "io.github.m4gshm.reactive.idempotent.consumer.storage"
            }
            generate {
                isDefaultCatalog = false
                isDefaultSchema = false
                isTables = false
                isKeys = false
                isGlobalObjectReferences = false
            }
        }
    }
}

fun requiredProperty(propertyName: String, defaultValue: String? = null) = project.findProperty(propertyName)
    ?: defaultValue ?: throw GradleException("undefined $propertyName")

tasks.named("jooqCodegen") {
    dependsOn("update")
}


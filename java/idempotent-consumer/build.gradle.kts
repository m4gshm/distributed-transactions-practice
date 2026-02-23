plugins {
    `java-library`
    id("liquibase-conventions")
    id("org.jooq.jooq-codegen-gradle")
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    compileOnly("io.projectreactor:reactor-core")

    implementation("org.postgresql:postgresql")

    liquibaseRuntime("org.liquibase:liquibase-core")
    liquibaseRuntime("info.picocli:picocli")
    liquibaseRuntime("org.postgresql:postgresql")

    jooqCodegen("org.postgresql:postgresql")

    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    implementation("org.jooq:jooq")
    implementation("org.jooq:jooq-postgres-extensions")
}

val dbSchema by project.extra { "public" }
val dbUsername by project.extra { "postgres" }
val dbPassword by project.extra { "postgres" }
val dbAddress by project.extra { "localhost:5000" }
val dbUrl by project.extra { "jdbc:postgresql://$dbAddress/idempotent_consumer" }

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

tasks.register<LiquibaseTask>("liquibaseUpdate") {
    searchPath = project.projectDir.path + "/src/main/liquibase"
    command = "update"
}

tasks.named("jooqCodegen") {
    dependsOn("liquibaseUpdate")
}


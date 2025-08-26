plugins {
    `java-library`
    id("org.liquibase.gradle") version "3.0.2"
    id("org.jooq.jooq-codegen-gradle") version "3.19.24"
}
apply(plugin = "io.spring.dependency-management")

buildscript {
    val liquibaseVer = "4.33.0"
    dependencies {
        classpath("org.liquibase:liquibase-core:$liquibaseVer")
    }
}

sourceSets {
    main {
        java {
            srcDirs("$projectDir/build/generated-sources/jooq")
        }
    }
}

dependencies {
    api(project(":reactive-utils"))
    api(project(":storage-api"))

    api(project(":jooq-r2dbc"))
    api(project(":jooq-postgres-prepared-transaction"))

    api("jakarta.validation:jakarta.validation-api")

    api("org.liquibase:liquibase-core:")
    api("org.postgresql:postgresql")
    api("org.postgresql:r2dbc-postgresql")

    liquibaseRuntime("org.liquibase:liquibase-core")
    liquibaseRuntime("info.picocli:picocli:4.7.7")
    liquibaseRuntime("org.postgresql:postgresql")

    jooqCodegen("org.postgresql:postgresql")

    api("org.springframework.boot:spring-boot-starter-data-r2dbc")
    api("org.springframework.boot:spring-boot-starter-jooq")
}

val dbSchema = "public"
val dbUsername = "postgres"
val dbPassword = "postgres"
val dbUrl = "jdbc:postgresql://localhost:5000/orders"

liquibase.activities.register("main") {
    arguments = mapOf<String, Any?>(
        "searchPath" to "${project.projectDir}/src/main/resources/",
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
                name = "org.jooq.meta.postgres.PostgresDatabase"
                includes = ".*"
                excludes = ""
            }
            target {
                packageName = "io.github.m4gshm.orders.data.access.jooq"
            }
        }
    }
}

fun requiredProperty(propertyName: String, defaultValue: String? = null) = project.findProperty(propertyName)
    ?: defaultValue ?: throw GradleException("undefined $propertyName")

tasks.named("jooqCodegen") {
    dependsOn("update")
}

tasks.withType<JavaCompile> {
    if (!project.hasProperty("no-codegen")) {
        dependsOn(tasks.named("jooqCodegen"))
    }
}

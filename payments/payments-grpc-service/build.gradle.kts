plugins {
    `java-library`
    id("org.springframework.boot")
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
    val liquibaseVer = "4.33.0"

    implementation("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    implementation(project(":storage-api"))
    implementation(project(":grpc-webflux"))
    implementation(project(":protobuf-utils"))
    implementation(project(":jooq-utils"))
    implementation(project(":jooq-r2dbc"))
    implementation(project(":payments:payments-grpc-api"))
    implementation(project(":tpc:tpc-grpc-service"))

    implementation("io.grpc:grpc-netty-shaded")
    implementation("com.google.protobuf:protobuf-java")

    implementation("org.liquibase:liquibase-core:$liquibaseVer")
    implementation("org.postgresql:postgresql")
    implementation("org.postgresql:r2dbc-postgresql:1.0.7.RELEASE")

    liquibaseRuntime("org.liquibase:liquibase-core:$liquibaseVer")
    liquibaseRuntime("info.picocli:picocli:4.7.7")
    liquibaseRuntime("org.postgresql:postgresql")

    jooqCodegen("org.postgresql:postgresql")

//    implementation("org.hibernate.reactive:hibernate-reactive-core:3.0.3.Final")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
//    implementation("org.jooq:jooq-meta:3.19.24")

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui")

    implementation("io.github.danielliu1123:grpc-server-boot-starter")
    implementation("io.github.danielliu1123:grpc-starter-protovalidate")
    implementation("io.github.danielliu1123:grpc-starter-transcoding")
    implementation("io.github.danielliu1123:grpc-starter-transcoding-springdoc")

    implementation("org.springframework.boot:spring-boot-autoconfigure")

    implementation("org.springframework:spring-webflux")

//    implementation(platform("org.springframework.grpc:spring-grpc-dependencies:0.9.0"))

    modules {
        module("io.grpc:grpc-netty") {
            replacedBy("io.grpc:grpc-netty-shaded", "Use Netty shaded instead of regular Netty")
        }
    }
}

val dbSchema = "public"
val dbUsername = "postgres"
val dbPassword = "postgres"
val dbUrl = "jdbc:postgresql://localhost:5000/payments"

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
                packageName = "payments.data.access.jooq"
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

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.provideDelegate

abstract class LiquibaseTask : JavaExec() {
    @Input
    var command: String? = null
    @Input
    var searchPath: String = project.projectDir.path + "/src/main/resources"
    @Input
    var changelogFile: String = "db/changelog/db.changelog-master.yaml"

    init {
        group = "liquibase"
        description = "Run Liquibase $command"
        classpath = project.configurations.getByName("liquibaseRuntime")
        mainClass.set("liquibase.integration.commandline.Main")
    }

    @TaskAction
    fun runLiquibase() {
        val dbUrl: String by project.extra
        val dbUsername: String by project.extra
        val dbPassword: String by project.extra

        args = listOf(
            "--changeLogFile=$changelogFile",
            "--url=$dbUrl",
            "--username=$dbUsername",
            "--password=$dbPassword",
            "--classpath=$searchPath",
            "--logLevel=warn",
            "--defaultSchemaName=public",
            command ?: name
        )
        project.logger.warn("args: " + args.map {
            if (it.startsWith("--password=")) {
                it.split("=").let { parts -> parts[0] + "=" + "****" }
            } else {
                it
            }
        })
    }
}
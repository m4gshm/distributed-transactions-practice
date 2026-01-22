import com.bmuschko.gradle.docker.DockerConventionJvmApplicationPlugin
import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile


plugins.apply(DockerRemoteApiPlugin::class)

tasks.register<Sync>(DockerConventionJvmApplicationPlugin.SYNC_BUILD_CONTEXT_TASK_NAME) {
    group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
    val bootJar = tasks.named<Jar>("bootJar")
    dependsOn("bootJar")
    into(project.layout.buildDirectory.dir("docker"))
    with(project.copySpec {
        val bootJar1 = bootJar.get()
        val ext = bootJar1.archiveExtension.get()
        into(".") { from(bootJar1.archiveFile) }
            .rename("(.+)\\.$ext", "app.jar")
        into(".") {
            from(rootProject.layout.projectDirectory.dir("docker"))
        }
        into("./async-profiler") {
            from(rootProject.layout.projectDirectory.dir("async-profiler-4.3-linux-x64"))
        }
    })
}

tasks.register<Dockerfile>(DockerConventionJvmApplicationPlugin.DOCKERFILE_TASK_NAME) {
    group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
    mustRunAfter(DockerConventionJvmApplicationPlugin.SYNC_BUILD_CONTEXT_TASK_NAME)

    from("eclipse-temurin:25.0.1_8-jre-ubi10-minimal")
    copyFile(Dockerfile.CopyFile("app.jar", "app.jar"))
    copyFile(Dockerfile.CopyFile("entrypoint.sh", "/entrypoint.sh"))
    runCommand("chmod +x /entrypoint.sh")
    environmentVariable("METASPACE_SIZE_MB", "150")
    entryPoint("/entrypoint.sh")
    copyFile(Dockerfile.CopyFile("async-profiler", "async-profiler"))
}

tasks.register<DockerBuildImage>(DockerConventionJvmApplicationPlugin.BUILD_IMAGE_TASK_NAME) {
    group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
    dependsOn(
        DockerConventionJvmApplicationPlugin.DOCKERFILE_TASK_NAME,
        DockerConventionJvmApplicationPlugin.SYNC_BUILD_CONTEXT_TASK_NAME,
    )
    val projectVersion = project.version
    val tagVersion = if (projectVersion == "unspecified") "latest" else projectVersion.toString()
    images.set(listOf(("jvm-" + this.project.name + ":" + tagVersion).lowercase()))
}


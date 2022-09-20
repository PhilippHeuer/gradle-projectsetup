package me.philippheuer.projectcfg.modules.framework

import me.philippheuer.projectcfg.ProjectConfigurationExtension
import me.philippheuer.projectcfg.domain.IProjectContext
import me.philippheuer.projectcfg.domain.PluginModule
import me.philippheuer.projectcfg.domain.ProjectFramework
import me.philippheuer.projectcfg.domain.ProjectLanguage
import me.philippheuer.projectcfg.domain.ProjectType
import me.philippheuer.projectcfg.modules.framework.tasks.QuarkusConfigurationTask
import me.philippheuer.projectcfg.util.DependencyUtils
import me.philippheuer.projectcfg.util.DependencyVersion
import me.philippheuer.projectcfg.util.PluginHelper
import me.philippheuer.projectcfg.util.PluginLogger
import me.philippheuer.projectcfg.util.addDependency
import me.philippheuer.projectcfg.util.applyPlugin
import org.gradle.api.Project
import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension

private const val CONFIG_TASK_NAME = "projectcfg-resources-quarkus-properties"
private const val CONFIG_RESOURCES_DIR = "quarkus-properties"

class QuarkusFramework constructor(override var ctx: IProjectContext) : PluginModule {
    override fun check(): Boolean {
        return ctx.isProjectFramework(ProjectFramework.QUARKUS)
    }

    override fun run() {
        if (ctx.isProjectType(ProjectType.LIBRARY)) {
            configureLibrary(ctx)
            configureAllOpen(ctx.project, ctx.config)
            configDefaults(ctx)
        } else if (ctx.isProjectType(ProjectType.APP)) {
            applyPlugin(ctx.project, ctx.config)
            configureAllOpen(ctx.project, ctx.config)
            configDefaults(ctx)
        }
    }

    companion object {
        fun configureLibrary(ctx: IProjectContext) {
            // core
            ctx.project.addDependency("io.quarkus:quarkus-core")
            // test
            ctx.project.addDependency("io.quarkus:quarkus-junit5")
        }

        fun applyPlugin(project: Project, config: ProjectConfigurationExtension) {
            project.run {
                // plugin
                applyPlugin("io.quarkus")

                // logging
                addDependency("implementation", "org.jboss.slf4j:slf4j-jboss-logmanager:1.1.0.Final")
                addDependency("implementation", "io.quarkus:quarkus-logging-json")

                // kotlin
                if (config.language.get() == ProjectLanguage.KOTLIN) {
                    addDependency("implementation", "io.quarkus:quarkus-kotlin")
                }

                // test
                addDependency("testImplementation", "io.quarkus:quarkus-junit5")

                // hibernate jandex constraint
                if (DependencyUtils.hasOneOfDependency(PluginLogger.project, listOf("implementation"), listOf("io.quarkus:quarkus-hibernate-orm", "io.quarkus:quarkus-hibernate-orm-panache", "io.quarkus:quarkus-hibernate-orm-panache-kotlin"))) {
                    project.dependencies.constraints.add("implementation", "org.jboss:jandex") { constraint ->
                        constraint.version { v ->
                            v.strictly("[2.4, 3[")
                            v.prefer(DependencyVersion.jandexVersion)
                        }
                        constraint.because("quarkus > 2.2 is not compatible with jandex < 2.4 (jandex index format version 10)")
                    }
                }
            }
        }

        fun configureAllOpen(project: Project, config: ProjectConfigurationExtension) {
            // kotlin
            if (config.language.get() == ProjectLanguage.KOTLIN) {
                project.applyPlugin("org.jetbrains.kotlin.plugin.allopen")

                project.extensions.configure(AllOpenExtension::class.java) {
                    it.annotation("io.quarkus.test.junit.QuarkusTest")
                    it.annotation("javax.enterprise.context.ApplicationScoped")
                    it.annotation("javax.persistence.Entity")
                }
            }
        }

        fun configDefaults(ctx: IProjectContext) {
            configBuildTime(ctx)
            configRuntime(ctx)
        }

        private fun configBuildTime(ctx: IProjectContext) {
            val properties = mutableMapOf<String, String>()
            if (ctx.config.native.get()) {
                properties["quarkus.package.type"] = "native"
            } else {
                properties["quarkus.package.type"] = "fast-jar"
            }
            properties["quarkus.native.container-build"] = "true"
            properties["quarkus.native.builder-image"] = "quay.io/quarkus/ubi-quarkus-native-image:21.3.3-java17"
            properties["quarkus.ssl.native"] = "true"

            // create file in a custom build resources dir
            val configDirectory = ctx.project.layout.buildDirectory.dir(CONFIG_RESOURCES_DIR).get()
            PluginHelper.createOrUpdatePropertyFile(ctx.project, configDirectory.file("META-INF/microprofile-config.properties").asFile, properties, managed = true)
            PluginHelper.addResourcesSource(ctx.project, configDirectory.toString())
        }

        private fun configRuntime(ctx: IProjectContext) {
            // properties edit task
            val task = ctx.project.tasks.register(CONFIG_TASK_NAME, QuarkusConfigurationTask::class.java) {
                it.config = ctx.config
            }
            ctx.project.tasks.matching { it.name == "classes" }.configureEach {
                it.dependsOn(task)
                it.mustRunAfter("processResources")
            }
        }

    }
}

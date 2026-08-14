package io.github.enummapper.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/** Adds the enum-mapper runtime and FIR compiler plugin to Kotlin compilations. */
public class EnumMapperGradlePlugin : KotlinCompilerPluginSupportPlugin {
    private val artifactVersion: String
        get() = javaClass.`package`.implementationVersion ?: DEFAULT_VERSION

    override fun apply(target: Project) {
        target.plugins.withType(KotlinBasePlugin::class.java).configureEach { kotlinPlugin ->
            if (kotlinPlugin.pluginVersion != KOTLIN_VERSION) {
                throw GradleException(
                    "enum-mapper $artifactVersion requires Kotlin $KOTLIN_VERSION, " +
                        "but this project uses Kotlin ${kotlinPlugin.pluginVersion}",
                )
            }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(GROUP, ARTIFACT, artifactVersion)

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> {
        kotlinCompilation.dependencies {
            implementation("$GROUP:$ARTIFACT:$artifactVersion")
        }
        return kotlinCompilation.target.project.provider { emptyList() }
    }

    private companion object {
        const val PLUGIN_ID = "io.github.enummapper"
        const val GROUP = "io.github.enummapper"
        const val ARTIFACT = "enum-mapper"
        const val DEFAULT_VERSION = "1.0.0-SNAPSHOT"
        const val KOTLIN_VERSION = "2.3.20"
    }
}

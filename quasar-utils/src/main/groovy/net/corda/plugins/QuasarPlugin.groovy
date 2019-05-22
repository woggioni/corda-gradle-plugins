package net.corda.plugins

import groovy.transform.PackageScope
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test

import javax.inject.Inject

/**
 * QuasarPlugin creates a "quasar" configuration and adds quasar as a dependency.
 */
class QuasarPlugin implements Plugin<Project> {

    private static final String QUASAR = "quasar"
    @PackageScope static final String defaultGroup = "co.paralleluniverse"
    // JDK 11 official support in 0.8.0 (https://github.com/puniverse/quasar/issues/317)
    @PackageScope static final String defaultVersion = "0.8.0"
    @PackageScope static final String defaultClassifier = ""

    private final ObjectFactory objects

    @Inject
    QuasarPlugin(ObjectFactory objects) {
        this.objects = objects
    }

    @Override
    void apply(Project project) {
        // Apply the Java plugin on the assumption that we're building a JAR.
        // This will also create the "compile", "compileOnly" and "runtime" configurations.
        project.pluginManager.apply(JavaPlugin)

        def rootProject = project.rootProject
        def quasarGroup = rootProject.hasProperty('quasar_group') ? rootProject.property('quasar_group') : defaultGroup
        def quasarVersion = rootProject.hasProperty('quasar_version') ? rootProject.property('quasar_version') : defaultVersion
        def quasarClassifier = rootProject.hasProperty('quasar_classifier') ? rootProject.property('quasar_classifier') : defaultClassifier
        def quasarExclusions = rootProject.hasProperty("quasar_exclusions") ? rootProject.property('quasar_exclusions') : Collections.emptyList()
        if (!(quasarExclusions instanceof Iterable<?>)) {
            throw new InvalidUserDataException("quasar_exclusions property must be an Iterable<String>")
        }
        def quasarExtension = project.extensions.create(QUASAR, QuasarExtension, objects, quasarGroup, quasarVersion, quasarClassifier, quasarExclusions)

        addQuasarDependencies(project, quasarExtension)
        configureQuasarTasks(project, quasarExtension)
    }

    private void addQuasarDependencies(Project project, QuasarExtension extension) {
        def quasar = project.configurations.create(QUASAR)
        quasar.withDependencies { dependencies ->
            def quasarDependency = project.dependencies.create(extension.dependency.get()) {
                it.transitive = false
            }
            dependencies.add(quasarDependency)
        }

        // Add Quasar to the compile classpath WITHOUT any of its transitive dependencies.
        project.dependencies.add("compileOnly", quasar) {
            it.transitive = false
        }

        // Add Quasar to the runtime classpath WITHOUT any of its transitive dependencies.
        Utils.createRuntimeConfiguration("cordaRuntime", project.configurations)
        project.dependencies.add("cordaRuntime", quasar) {
            // Ensure that Quasar's transitive dependencies are available at runtime (only).
            it.transitive = false
        }
    }

    private void configureQuasarTasks(Project project, QuasarExtension extension) {
        project.tasks.withType(Test) {
            doFirst {
                jvmArgs "-javaagent:${project.configurations[QUASAR].singleFile}${extension.options.get()}",
                        "-Dco.paralleluniverse.fibers.verifyInstrumentation"
            }
        }
        project.tasks.withType(JavaExec) {
            doFirst {
                jvmArgs "-javaagent:${project.configurations[QUASAR].singleFile}${extension.options.get()}",
                        "-Dco.paralleluniverse.fibers.verifyInstrumentation"
            }
        }
    }
}

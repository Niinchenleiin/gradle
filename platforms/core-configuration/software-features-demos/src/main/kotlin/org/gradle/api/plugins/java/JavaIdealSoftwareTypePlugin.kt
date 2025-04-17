/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.plugins.java

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.plugins.BindsSoftwareType
import org.gradle.api.internal.plugins.SoftwareTypeBindingBuilder
import org.gradle.api.internal.plugins.SoftwareTypeBindingRegistration
import org.gradle.api.internal.plugins.bind
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

@BindsSoftwareType(JavaIdealSoftwareTypePlugin.Binding::class)
class JavaIdealSoftwareTypePlugin : Plugin<Project> {
    /**
     * javaLibrary {
     *     version = "11"
     *     sources {
     *        javaSources("main") {
     *        }
     *     }
     * }
     */
    class Binding : SoftwareTypeBindingRegistration {
        override fun configure(builder: SoftwareTypeBindingBuilder) {
            builder
                .bind<JavaIdealSoftwareType, JavaLibraryOutputs>("javaLibrary") { definition, model ->
                    definition.sources.register("main")
                    definition.sources.register("test")

                    definition.sources.all { javaSources ->
                        // Should be TaskRegistrar with some sort of an implicit namer for the context
                        val compileTask = project.tasks.register(
                            "compile" + javaSources.name.capitalize() + "Java",
                            JavaCompile::class.java
                        ) { task ->
                            task.source(javaSources.javaSources.asFileTree)
                        }

                        val processResourcesTask = project.tasks.register("processResources", Copy::class.java) { task ->
                            task.from(javaSources.resources.asFileTree)
                        }

                        val classes = model.classes.register(javaSources.name) { bytecode ->
                            bytecode.byteCodeDir.set(compileTask.map { it.destinationDirectory.get() })
                            bytecode.processedResourcesDir.fileProvider(processResourcesTask.map { it.destinationDir })
                        }

                        // Creates an extension on javaSources containing its classes object
                        registerModel(javaSources, classes)
                    }

                    val mainClasses = model.classes.named("main")
                    val jarTask = project.tasks.register("jar", Jar::class.java) { task ->
                        task.from(mainClasses.map { it.byteCodeDir })
                        task.from(mainClasses.map { it.processedResourcesDir })
                    }

                    model.jarFile.set(jarTask.map { it.archiveFile.get() })
                }
        }
    }

    override fun apply(target: Project) {

    }
}

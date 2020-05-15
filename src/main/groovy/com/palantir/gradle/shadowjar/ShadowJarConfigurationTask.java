/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.shadowjar;

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

// Originally taken from https://github.com/johnrengelman/shadow/blob/d4e649d7dd014bfdd9575bfec92d7e74c3cf1aca/
// src/main/groovy/com/github/jengelman/gradle/plugins/shadow/tasks/ConfigureShadowRelocation.groovy
public abstract class ShadowJarConfigurationTask extends DefaultTask {
    private final Property<ShadowJar> shadowJarProperty =
            getProject().getObjects().property(ShadowJar.class);

    private final Property<String> prefix = getProject().getObjects().property(String.class);
    private final SetProperty<ResolvedDependency> acceptedDependencies =
            getProject().getObjects().setProperty(ResolvedDependency.class);

    @Internal
    public final Property<ShadowJar> getShadowJar() {
        return shadowJarProperty;
    }

    @Input
    public final Property<String> getPrefix() {
        return prefix;
    }

    @Classpath
    public final List<Configuration> getConfigurations() {
        return shadowJarProperty.get().getConfigurations();
    }

    @Input
    public final SetProperty<ResolvedDependency> getAcceptedDependencies() {
        return acceptedDependencies;
    }

    @TaskAction
    public final void run() {
        ShadowJar shadowJar = shadowJarProperty.get();

        shadowJar.getDependencyFilter().include(acceptedDependencies.get()::contains);

        FileCollection jars = shadowJar.getDependencyFilter().resolve(getConfigurations());

        Set<String> packages = jars.getFiles().stream()
                .flatMap(jar -> {
                    try (JarFile jarFile = new JarFile(jar)) {
                        return Collections.list(jarFile.entries()).stream()
                                .filter(jarEntry -> jarEntry.getName().endsWith(".class"))
                                .filter(jarEntry -> jarEntry.getName().contains("/"))
                                .map(jarEntry -> jarEntry.getName()
                                        .substring(0, jarEntry.getName().lastIndexOf('/') - 1)
                                        .replace('/', '.'))
                                .collect(Collectors.toSet())
                                .stream();
                    } catch (IOException e) {
                        throw new RuntimeException("Could not open jar file", e);
                    }
                })
                .collect(Collectors.toSet());

        packages.forEach(pkg -> shadowJar.relocate(pkg, prefix.get() + "." + pkg));
    }
}

/**
 *  Copyright 2005-2015 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.forge.devops.setup;

import java.io.PrintStream;
import javax.inject.Inject;

import io.fabric8.forge.addon.utils.MavenHelpers;
import org.jboss.forge.addon.dependencies.Coordinate;
import org.jboss.forge.addon.dependencies.builder.CoordinateBuilder;
import org.jboss.forge.addon.maven.plugins.MavenPlugin;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.projects.Projects;
import org.jboss.forge.addon.projects.ui.AbstractProjectCommand;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractFabricProjectCommand extends AbstractProjectCommand {

    public static String CATEGORY = "Fabric";

    final transient Logger log = LoggerFactory.getLogger(this.getClass());

    @Inject
    protected ProjectFactory projectFactory;

    @Override
    protected boolean isProjectRequired() {
        return true;
    }

    protected Project getSelectedProjectOrNull(UIContext context) {
        return Projects.getSelectedProject(this.getProjectFactory(), context);
    }

    @Override
    protected ProjectFactory getProjectFactory() {
        return projectFactory;
    }

    protected PrintStream getOutput(UIExecutionContext context) {
        return context.getUIContext().getProvider().getOutput().out();
    }

    protected Coordinate createCoordinate(String groupId, String artifactId, String version) {
        return createCoordinate(groupId, artifactId, version, null);
    }

    protected Coordinate createCoordinate(String groupId, String artifactId, String version, String packaging) {
        CoordinateBuilder builder = CoordinateBuilder.create()
                .setGroupId(groupId)
                .setArtifactId(artifactId);
        if (version != null) {
            builder = builder.setVersion(version);
        }
        if (packaging != null) {
            builder = builder.setPackaging(packaging);
        }

        return builder;
    }

    protected boolean isFabric8Project(Project project) {
        if (project == null) {
            // must have a project
            return false;
        } else {
            // must be fabric8 project, eg have fabric8-maven-plugin
            MavenPlugin plugin = MavenHelpers.findPlugin(project, "io.fabric8", "fabric8-maven-plugin");
            return plugin != null;
        }
    }

}

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
package io.fabric8.forge.camel.commands.project;

import java.util.LinkedHashMap;
import java.util.Map;

import io.fabric8.forge.camel.commands.project.dto.ComponentDto;
import io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper;
import org.apache.camel.catalog.CamelCatalog;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.dependencies.builder.DependencyBuilder;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.wizard.UIWizardStep;

public class CamelProjectAddComponentStep extends AbstractCamelProjectCommand implements UIWizardStep {

    private final UISelectOne<String> filter;
    private final UISelectOne<ComponentDto> componentName;
    private final DependencyInstaller dependencyInstaller;

    public CamelProjectAddComponentStep(UISelectOne<String> filter, UISelectOne<ComponentDto> componentName, ProjectFactory projectFactory,
                                        DependencyInstaller dependencyInstaller, CamelCatalog camelCatalog) {
        this.filter = filter;
        this.componentName = componentName;
        this.projectFactory = projectFactory;
        this.dependencyInstaller = dependencyInstaller;
        this.camelCatalog = camelCatalog;
    }

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelProjectAddComponentStep.class).name(
                "Camel: Project Add Component").category(Categories.create(CATEGORY))
                .description("Adds a Camel component to your project dependencies");
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        final Project project = getSelectedProject(builder);

        Iterable<ComponentDto> it = CamelCommandsHelper.createAllComponentDtoValues(project, getCamelCatalog(), filter, true).call();
        // flattern the choices to a map so the UI is more responsive, as we can do a direct lookup
        // in the map from the value converter
        final Map<String, ComponentDto> components = new LinkedHashMap<>();
        for (ComponentDto dto : it) {
            components.put(dto.getScheme(), dto);
        }

        componentName.setValueChoices(components.values());
        // include converter from string->dto
        componentName.setValueConverter(components::get);

        builder.add(componentName);
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        Project project = getSelectedProject(context);

        // does the project already have camel?
        Dependency core = findCamelCoreDependency(project);
        if (core == null) {
            return Results.fail("The project does not include camel-core");
        }

        ComponentDto dto = componentName.getValue();
        if (dto != null) {

            // we want to use same version as camel-core if its a camel component
            // otherwise use the version from the dto
            String version;
            if ("org.apache.camel".equals(dto.getGroupId())) {
                version = core.getCoordinate().getVersion();
            } else {
                version = dto.getVersion();
            }
            DependencyBuilder dependency = DependencyBuilder.create().setGroupId(dto.getGroupId())
                    .setArtifactId(dto.getArtifactId()).setVersion(version);

            // install the component
            dependencyInstaller.install(project, dependency);

            return Results.success("Added Camel component " + dto.getScheme() + " (" + dto.getArtifactId() + ") to the project");
        } else {
            return Results.fail("Unknown Camel component");
        }
    }
}

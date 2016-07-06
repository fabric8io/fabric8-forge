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

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

import io.fabric8.forge.camel.commands.project.completer.CamelLanguagesCompleter;
import io.fabric8.forge.camel.commands.project.dto.LanguageDto;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.dependencies.builder.DependencyBuilder;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;

public class CamelProjectAddLanguageCommand extends AbstractCamelProjectCommand {

    @Inject
    @WithAttributes(label = "Name", required = true, description = "Name of language to add")
    private UISelectOne<String> languageName;
    // use plain String in UISelectOne as the DTOs do not work on the web (that requires a special picker like for component picker)
    private Iterable<LanguageDto> dtos;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelProjectAddLanguageCommand.class).name(
                "Camel: Project Add Language").category(Categories.create(CATEGORY))
                .description("Adds a Camel language to your project dependencies");
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        Project project = getSelectedProject(builder);

        dtos = new CamelLanguagesCompleter(project, getCamelCatalog()).getValueChoices();
        List<String> names = new ArrayList<>();
        for (LanguageDto dto : dtos) {
            names.add(dto.getName());
        }
        languageName.setValueChoices(names);

        builder.add(languageName);
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        Project project = getSelectedProject(context);

        // does the project already have camel?
        Dependency core = findCamelCoreDependency(project);
        if (core == null) {
            return Results.fail("The project does not include camel-core");
        }

        String name = languageName.getValue();
        LanguageDto dto = findLanguateDto(name);

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

            return Results.success("Added Camel language " + dto.getName() + " (" + dto.getArtifactId() + ") to the project");
        } else {
            return Results.fail("Unknown Camel language");
        }
    }

    private LanguageDto findLanguateDto(String name) {
        for (LanguageDto dto : dtos) {
            if (dto.getName().equals(name)) {
                return dto;
            }
        }
        return null;
    }

}

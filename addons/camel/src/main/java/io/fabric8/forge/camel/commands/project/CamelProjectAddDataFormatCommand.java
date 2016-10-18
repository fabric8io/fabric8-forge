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

import io.fabric8.forge.camel.commands.project.completer.CamelDataFormatsCompleter;
import io.fabric8.forge.camel.commands.project.dto.DataFormatDto;
import io.fabric8.forge.camel.commands.project.helper.CamelVersionHelper;
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

public class CamelProjectAddDataFormatCommand extends AbstractCamelProjectCommand {

    @Inject
    @WithAttributes(label = "Name", required = true, description = "Name of dataformat to add")
    private UISelectOne<String> dataformatName;
    // use plain String in UISelectOne as the DTOs do not work on the web (that requires a special picker like for component picker)
    private Iterable<DataFormatDto> dtos;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelProjectAddDataFormatCommand.class).name(
                "Camel: Project Add DataFormat").category(Categories.create(CATEGORY))
                .description("Adds a Camel dataformat to your project dependencies");
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        Project project = getSelectedProject(builder);

        dtos = new CamelDataFormatsCompleter(project, getCamelCatalog()).getValueChoices();
        List<String> names = new ArrayList<>();
        for (DataFormatDto dto : dtos) {
            names.add(dto.getName());
        }
        dataformatName.setValueChoices(names);

        builder.add(dataformatName);
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        Project project = getSelectedProject(context);

        // does the project already have camel?
        Dependency core = findCamelCoreDependency(project);
        if (core == null) {
            return Results.fail("The project does not include camel-core");
        }

        String name = dataformatName.getValue();
        DataFormatDto dto = findDataFormatDto(name);

        if (dto != null) {

            // we want to use same version as camel-core if its a camel component
            // otherwise use the version from the dto
            String version;
            if ("org.apache.camel".equals(dto.getGroupId())) {
                version = core.getCoordinate().getVersion();
            } else {
                version = dto.getVersion();
            }

            String artifactId = dto.getArtifactId();

            // is it spring boot project and Camel is > 2.18.0 then we must use -starter
            if (isSpringBootProject(project)) {
                boolean newer = CamelVersionHelper.isGE("2.18.0", version);
                if (newer) {
                    artifactId = artifactId + "-starter";
                }
            }


            DependencyBuilder dependency = DependencyBuilder.create().setGroupId(dto.getGroupId())
                    .setArtifactId(artifactId).setVersion(version);

            // install the component
            dependencyInstaller.install(project, dependency);

            return Results.success("Added Camel dataformat " + dto.getName() + " (" + dto.getArtifactId() + ") to the project");
        } else {
            return Results.fail("Unknown Camel dataformat");
        }
    }

    private DataFormatDto findDataFormatDto(String name) {
        for (DataFormatDto dto : dtos) {
            if (dto.getName().equals(name)) {
                return dto;
            }
        }
        return null;
    }

}

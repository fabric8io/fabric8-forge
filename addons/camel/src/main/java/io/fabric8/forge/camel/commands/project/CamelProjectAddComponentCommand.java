/**
 * Copyright 2005-2015 Red Hat, Inc.
 * <p/>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.forge.camel.commands.project;

import javax.inject.Inject;

import io.fabric8.forge.camel.commands.project.dto.ComponentDto;
import io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.result.navigation.NavigationResultBuilder;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.wizard.UIWizard;

public class CamelProjectAddComponentCommand extends AbstractCamelProjectCommand implements UIWizard {

    @Inject
    @WithAttributes(label = "Filter", required = true, description = "To filter components")
    private UISelectOne<String> filter;

    @Inject
    @WithAttributes(label = "Name", required = true, description = "Name of component type to add")
    private UISelectOne<ComponentDto> componentName;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelProjectAddComponentCommand.class).name(
                "Camel: Project Add Component").category(Categories.create(CATEGORY))
                .description("Adds a Camel component to your project dependencies");
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        final Project project = getSelectedProject(builder);

        filter.setValueChoices(CamelCommandsHelper.createComponentLabelValues(project, getCamelCatalog()));
        filter.setDefaultValue("<all>");

        // we use the componentName in the next step so do not add it to this builder
        builder.add(filter);
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        NavigationResultBuilder builder = Results.navigationBuilder();
        builder.add(new CamelProjectAddComponentStep(filter, componentName, projectFactory, dependencyInstaller, getCamelCatalog()));
        return builder.build();
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        return null;
    }
}

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import io.fabric8.forge.camel.commands.project.dto.ComponentDto;
import io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper;
import io.fabric8.forge.camel.commands.project.model.InputOptionByGroup;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.InputComponentFactory;
import org.jboss.forge.addon.ui.input.UIInput;
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

import static io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper.createUIInputsForCamelEndpoint;

public class CamelAddRouteXmlCommand extends AbstractCamelProjectCommand implements UIWizard {

    @Inject
    @WithAttributes(label = "XML File", required = true, description = "The XML file to use (either Spring or Blueprint)")
    private UISelectOne<String> xml;

    @Inject
    @WithAttributes(label = "Name", required = false, description = "The name of the new route")
    private UIInput<String> id;

    @Inject
    @WithAttributes(label = "Name", required = true, description = "Name of component to use for the endpoint")
    private UISelectOne<ComponentDto> componentName;

    @Inject
    private InputComponentFactory componentFactory;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelAddRouteXmlCommand.class).name(
                "Camel: Add Route XML").category(Categories.create(CATEGORY))
                .description("Adds a Camel route to an existing or new XML file");
    }

    @Override
    public boolean isEnabled(UIContext context) {
        boolean enabled = super.isEnabled(context);
        if (enabled) {

            // TODO: must be an xml file to chose from

            // must be spring or blueprint project for editing xml files
            boolean spring = CamelCommandsHelper.isSpringProject(getSelectedProject(context));
            boolean blueprint = CamelCommandsHelper.isBlueprintProject(getSelectedProject(context));
            return spring || blueprint;
        }
        return false;
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        Map<Object, Object> attributeMap = builder.getUIContext().getAttributeMap();
        attributeMap.remove("navigationResult");

        Project project = getSelectedProject(builder.getUIContext());
        String currentFile = getSelectedFile(builder.getUIContext());

        // we only want components that is able to consume because this is to add a new route
        configureComponentName(project, componentName, true, false);
        configureXml(project, xml, currentFile);

        builder.add(xml).add(id).add(componentName);
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
            Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();

            // always refresh these as the end user may have edited the instance name
            attributeMap.put("xml", xml.getValue());
            attributeMap.put("mode", "add");
            attributeMap.put("kind", "xml");

            ComponentDto component = componentName.getValue();
            String camelComponentName = component.getScheme();

            // must be same component name to allow reusing existing navigation result
            String previous = (String) attributeMap.get("componentName");
            if (previous != null && previous.equals(camelComponentName)) {
                NavigationResult navigationResult = (NavigationResult) attributeMap.get("navigationResult");
                if (navigationResult != null) {
                    return navigationResult;
                }
            }

            attributeMap.put("componentName", camelComponentName);

            // we need to figure out how many options there is so we can as many steps we need

            // producer vs consumer only if selected
            boolean consumerOnly = true;
            boolean producerOnly = false;

            UIContext ui = context.getUIContext();
            List<InputOptionByGroup> groups = createUIInputsForCamelEndpoint(camelComponentName, null, MAX_OPTIONS, consumerOnly, producerOnly,
                    getCamelCatalog(), componentFactory, converterFactory, ui);

            // need all inputs in a list as well
            List<InputComponent> allInputs = new ArrayList<>();
            for (InputOptionByGroup group : groups) {
                allInputs.addAll(group.getInputs());
            }

            NavigationResultBuilder builder = Results.navigationBuilder();
            int pages = groups.size();
            for (int i = 0; i < pages; i++) {
                boolean last = i == pages - 1;
                InputOptionByGroup current = groups.get(i);
                AddRouteFromEndpointXmlStep step = new AddRouteFromEndpointXmlStep(projectFactory, dependencyInstaller,
                        getCamelCatalog(),
                        camelComponentName, current.getGroup(), allInputs, current.getInputs(), last, i, pages,
                        id.getValue());
                builder.add(step);
            }

            NavigationResult navigationResult = builder.build();
            attributeMap.put("navigationResult", navigationResult);
            return navigationResult;
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        return Results.success();
    }

}

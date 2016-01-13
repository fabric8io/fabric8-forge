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

import io.fabric8.forge.camel.commands.project.completer.XmlFileCompleter;
import io.fabric8.forge.camel.commands.project.dto.ComponentDto;
import io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper;
import io.fabric8.forge.camel.commands.project.model.EndpointOptionByGroup;
import org.jboss.forge.addon.convert.Converter;
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
import org.jboss.forge.addon.ui.input.ValueChangeListener;
import org.jboss.forge.addon.ui.input.events.ValueChangeEvent;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.result.navigation.NavigationResultBuilder;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.wizard.UIWizard;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.createComponentDto;
import static io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper.createUIInputsForCamelComponent;
import static io.fabric8.forge.camel.commands.project.helper.CollectionHelper.first;

public class CamelAddRouteXmlCommand extends AbstractCamelProjectCommand implements UIWizard {
    @Inject
    @WithAttributes(label = "XML File", required = true, description = "The XML file to use (either Spring or Blueprint)")
    private UISelectOne<String> xml;

    @Inject
    @WithAttributes(label = "Route ID", required = false, description = "The ID of the route to add")
    private UIInput<String> id;

    @Inject
    @WithAttributes(label = "Route Description", required = false, description = "The description of the route to add")
    private UIInput<String> description;

    @Inject
    @WithAttributes(label = "Filter", required = false, description = "To filter components")
    private UISelectOne<String> componentNameFilter;

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
                .description("Adds a Camel route to an existing XML file");
    }

    @Override
    public boolean isEnabled(UIContext context) {
        boolean enabled = super.isEnabled(context);
        if (enabled) {
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

        // TODO limit the components to consumer endpoints only?

        componentNameFilter.setValueChoices(CamelCommandsHelper.createComponentLabelValues(project, getCamelCatalog()));
        componentNameFilter.setDefaultValue("<all>");
        componentName.setValueChoices(CamelCommandsHelper.createComponentDtoValues(project, getCamelCatalog(), componentNameFilter, false));
        // include converter from string->dto
        componentName.setValueConverter(new Converter<String, ComponentDto>() {
            @Override
            public ComponentDto convert(String text) {
                return createComponentDto(getCamelCatalog(), text);
            }
        });
        componentName.setValueConverter(new Converter<String, ComponentDto>() {
            @Override
            public ComponentDto convert(String name) {
                return createComponentDto(getCamelCatalog(), name);
            }
        });
        // show note about the chosen component
        componentName.addValueChangeListener(new ValueChangeListener() {
            @Override
            public void valueChanged(ValueChangeEvent event) {
                ComponentDto component = (ComponentDto) event.getNewValue();
                if (component != null) {
                    String description = component.getDescription();
                    componentName.setNote(description != null ? description : "");
                } else {
                    componentName.setNote("");
                }
            }
        });

        XmlFileCompleter xmlFileCompleter = createXmlFileCompleter(project);
        Set<String> files = xmlFileCompleter.getFiles();

        // use value choices instead of completer as that works better in web console
        xml.setValueChoices(files);
        if (files.size() == 1) {
            // lets default the value if there's only one choice
            xml.setDefaultValue(first(files));
        }
        builder.add(xml).add(id).add(description).add(componentNameFilter).add(componentName);
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
            List<EndpointOptionByGroup> groups = createUIInputsForCamelComponent(camelComponentName, null, CamelAddEndpointXmlCommand.MAX_OPTIONS, consumerOnly, producerOnly,
                    getCamelCatalog(), componentFactory, converterFactory, ui);

            // need all inputs in a list as well
            List<InputComponent> allInputs = new ArrayList<>();
            for (EndpointOptionByGroup group : groups) {
                allInputs.addAll(group.getInputs());
            }

            NavigationResultBuilder builder = Results.navigationBuilder();
            int pages = groups.size();
            for (int i = 0; i < pages; i++) {
                boolean last = i == pages - 1;
                EndpointOptionByGroup current = groups.get(i);
                AddRouteFromEndpointXmlStep step = new AddRouteFromEndpointXmlStep(projectFactory, dependencyInstaller,
                        getCamelCatalog(),
                        camelComponentName, current.getGroup(), allInputs, current.getInputs(), last, i, pages,
                        id.getValue(), description.getValue());
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

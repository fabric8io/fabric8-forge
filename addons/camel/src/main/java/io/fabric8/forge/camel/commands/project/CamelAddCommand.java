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
import java.util.Optional;
import javax.inject.Inject;

import io.fabric8.forge.camel.commands.project.dto.ComponentDto;
import io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper;
import io.fabric8.forge.camel.commands.project.model.InputOptionByGroup;
import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.context.UIRegion;
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

import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.createComponentDto;
import static io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper.createUIInputsForCamelComponent;

public class CamelAddCommand extends AbstractCamelProjectCommand implements UIWizard {

    private static final int MAX_OPTIONS = 20;

    @Inject
    @WithAttributes(label = "Filter", required = false, description = "To filter components")
    private UISelectOne<String> componentNameFilter;

    @Inject
    @WithAttributes(label = "Name", required = true, description = "Name of component to use for the endpoint")
    private UISelectOne<ComponentDto> componentName;

    // TODO: remove me when no longer needed
    @Inject
    @WithAttributes(label = "Debug", required = true)
    private UIInput<String> debug;

    @Inject
    private InputComponentFactory componentFactory;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelAddCommand.class).name(
                "Camel: Add").category(Categories.create(CATEGORY))
                .description("Add Camel endpoint or EIP from the current cursor position");
    }

    @Override
    public boolean isEnabled(UIContext context) {
        boolean answer = super.isEnabled(context);
        if (answer) {
            // we are only enabled if there is a file open in the editor
            final String currentFile = getSelectedFile(context);
            answer = currentFile != null;
        }
        return answer;
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        Map<Object, Object> attributeMap = builder.getUIContext().getAttributeMap();
        attributeMap.remove("navigationResult");

        Project project = getSelectedProject(builder.getUIContext());

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

        builder.add(componentNameFilter).add(componentName);

        Optional<UIRegion<Object>> region = builder.getUIContext().getSelection().getRegion();
        if (region.isPresent()) {
            int pos = region.get().getStartPosition();
            attributeMap.put("pos", pos);
        }

        attributeMap.put("routeBuilder", getSelectedFile(builder.getUIContext()));
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();

        // always refresh these as the end user may have edited the instance name
        attributeMap.put("mode", "add");
        attributeMap.put("kind", "java");

        ComponentDto component = componentName.getValue();
        String camelComponentName = component.getScheme();

        // must be same component name and endpoint type to allow reusing existing navigation result
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
        boolean consumerOnly = component.isConsumerOnly();
        boolean producerOnly = component.isProducerOnly();

        UIContext ui = context.getUIContext();
        List<InputOptionByGroup> groups = createUIInputsForCamelComponent(camelComponentName, null, MAX_OPTIONS, consumerOnly, producerOnly,
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
            ConfigureEndpointPropertiesStep step = new ConfigureEndpointPropertiesStep(projectFactory, dependencyInstaller, getCamelCatalog(),
                    camelComponentName, current.getGroup(), allInputs, current.getInputs(), last, i, pages);
            builder.add(step);
        }

        NavigationResult navigationResult = builder.build();
        attributeMap.put("navigationResult", navigationResult);
        return navigationResult;
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        return Results.success();

        /*
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();

        Optional<UIRegion<Object>> region = context.getUIContext().getSelection().getRegion();
        if (region.isPresent()) {
            Object resource = region.get().getResource();
            if (resource instanceof FileResource) {
                FileResource fr = (FileResource) resource;

                Integer pos = (Integer) attributeMap.getOrDefault("pos", -1);

                // check if prev and next post is quote, then add that automatic

                if (pos != null && pos > -1) {
                    StringBuilder sb = new StringBuilder(fr.getContents());
                    sb.insert(pos, "log:foo");

                    // and save the file back
                    fr.setContents(sb.toString());
                }
            }
        }

        return Results.success();*/
    }

}

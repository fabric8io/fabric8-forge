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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.inject.Inject;

import io.fabric8.forge.camel.commands.project.completer.SpringBootConfigurationFileCompleter;
import io.fabric8.forge.camel.commands.project.dto.ComponentDto;
import io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper;
import io.fabric8.forge.camel.commands.project.helper.PoorMansLogger;
import io.fabric8.forge.camel.commands.project.helper.StringHelper;
import io.fabric8.forge.camel.commands.project.model.InputOptionByGroup;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.projects.facets.ResourcesFacet;
import org.jboss.forge.addon.resource.FileResource;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.InputComponentFactory;
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

import static io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper.createUIInputsForCamelComponent;
import static io.fabric8.forge.camel.commands.project.helper.CollectionHelper.first;

public class CamelEditComponentCommand extends AbstractCamelProjectCommand implements UIWizard {

    private static final PoorMansLogger LOG = new PoorMansLogger(true);

    private static final int MAX_OPTIONS = 20;

    @Inject
    @WithAttributes(label = "Components", required = true, description = "The components from the project")
    private UISelectOne<ComponentDto> componentName;

    @Inject
    private InputComponentFactory componentFactory;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelEditComponentCommand.class).name(
                "Camel: Edit Component").category(Categories.create(CATEGORY))
                .description("Edit Camel component");
    }

    @Override
    public boolean isEnabled(UIContext context) {
        boolean answer = super.isEnabled(context);
        if (answer) {
            // TODO: require Spring Boot currently

        }
        return answer;
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        final Project project = getSelectedProject(builder);

        Map<Object, Object> attributeMap = builder.getUIContext().getAttributeMap();
        attributeMap.remove("navigationResult");

        // find all components in project (and filter out components without options)
        Iterable<ComponentDto> it = CamelCommandsHelper.createComponentDtoValues(project, getCamelCatalog(), null, false, false, false, true).call();
        componentName.setValueChoices(it);

        builder.add(componentName);
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();

        ComponentDto selectedComponent = componentName.getValue();
        if ("<select>".equals(selectedComponent)) {
            // no choice yet
            attributeMap.remove("navigationResult");
            return null;
        }

        // must be same component name to allow reusing existing navigation result
        String previous = (String) attributeMap.get("componentName");
        if (previous != null && previous.equals(selectedComponent.getScheme())) {
            NavigationResult navigationResult = (NavigationResult) attributeMap.get("navigationResult");
            if (navigationResult != null) {
                return navigationResult;
            }
        }

        LOG.info("Component: " + selectedComponent.getScheme());

        String json = getCamelCatalog().componentJSonSchema(selectedComponent.getScheme());
        if (json == null) {
            throw new IllegalArgumentException("Could not find catalog entry for component name: " + selectedComponent.getScheme());
        }

        LOG.info("Component json: " + json);

        attributeMap.put("componentName", selectedComponent.getScheme());
        attributeMap.put("mode", "edit");
        attributeMap.put("kind", "springboot");

        Map<String, String> currentValues = Collections.EMPTY_MAP;

        // read current values from spring-boot application configuration file
        SpringBootConfigurationFileCompleter xmlFileCompleter = createSpringBootConfigurationFileCompleter(context.getUIContext(), null);
        Set<String> files = xmlFileCompleter.getFiles();
        if (files.size() >= 1) {
            String file = first(files);
            attributeMap.put("applicationFile", file);

            LOG.info("Application configuration file: " + file);

            ResourcesFacet facet = getSelectedProject(context).getFacet(ResourcesFacet.class);
            FileResource fr = facet.getResource(file);
            String data = fr.getContents();

            boolean yaml = file.endsWith(".yaml") || file.endsWith(".yml");
            boolean properties = file.endsWith(".properties");
            String prefix = "camel.component." + selectedComponent.getScheme() + ".";

            if (yaml) {
                // TODO: support parsing yaml

            } else if (properties) {
                Properties prop = new Properties();
                prop.load(new StringReader(data));

                currentValues = new LinkedHashMap<>();
                for (String key : prop.stringPropertyNames()) {
                    String value = prop.getProperty(key);

                    // the key must be without dash and in camelCase
                    key = StringHelper.dashToCamelCase(key);

                    if (key.startsWith(prefix)) {
                        key = key.substring(prefix.length());
                        currentValues.put(key, value);
                        LOG.info("Current value " + key + "=" + value);
                    }
                }
            }
        } else {
            // no file so we create a file manually in the wizard
            attributeMap.put("applicationFile", "application.properties");
        }

        UIContext ui = context.getUIContext();
        List<InputOptionByGroup> groups = createUIInputsForCamelComponent(selectedComponent.getScheme(), currentValues, MAX_OPTIONS,
                getCamelCatalog(), componentFactory, converterFactory, ui);

        // need all inputs in a list as well
        List<InputComponent> allInputs = new ArrayList<>();
        for (InputOptionByGroup group : groups) {
            allInputs.addAll(group.getInputs());
        }

        LOG.info(allInputs.size() + " input fields in the UI wizard");

        NavigationResultBuilder builder = Results.navigationBuilder();
        int pages = groups.size();
        for (int i = 0; i < pages; i++) {
            boolean last = i == pages - 1;
            InputOptionByGroup current = groups.get(i);
            ConfigureComponentPropertiesStep step = new ConfigureComponentPropertiesStep(projectFactory, dependencyInstaller, getCamelCatalog(),
                    selectedComponent.getScheme(), current.getGroup(), allInputs, current.getInputs(), last, i, pages);
            builder.add(step);
        }

        NavigationResult navigationResult = builder.build();
        attributeMap.put("navigationResult", navigationResult);
        return navigationResult;
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        return null;
    }

}

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
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

import io.fabric8.forge.addon.utils.completer.PackageNameCompleter;
import io.fabric8.forge.addon.utils.validator.ClassNameValidator;
import io.fabric8.forge.addon.utils.validator.PackageNameValidator;
import io.fabric8.forge.camel.commands.project.completer.RouteBuilderCompleter;
import io.fabric8.forge.camel.commands.project.dto.ComponentDto;
import io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper;
import io.fabric8.forge.camel.commands.project.model.InputOptionByGroup;
import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.facets.constraints.FacetConstraint;
import org.jboss.forge.addon.parser.java.facets.JavaSourceFacet;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.projects.facets.ClassLoaderFacet;
import org.jboss.forge.addon.projects.facets.ResourcesFacet;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.facets.HintsFacet;
import org.jboss.forge.addon.ui.hints.InputType;
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
import org.jboss.forge.roaster.model.util.Strings;

import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.createComponentDto;
import static io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper.createUIInputsForCamelComponent;
import static io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper.createUIInputsForCamelEndpoint;
import static io.fabric8.forge.camel.commands.project.helper.CollectionHelper.first;

@FacetConstraint({JavaSourceFacet.class, ResourcesFacet.class, ClassLoaderFacet.class})
public class CamelNewComponentCommand extends AbstractCamelProjectCommand implements UIWizard {

    @Inject
    @WithAttributes(label = "Component Name", required = true, description = "The component to use")
    private UISelectOne<ComponentDto> componentName;

    @Inject
    @WithAttributes(label = "Instance Name", required = true, description = "Name of component instance to add")
    private UIInput<String> instanceName;

    @Inject
    @WithAttributes(label = "Target Package", required = false, description = "The package name where this type will be created")
    private UIInput<String> targetPackage;

    @Inject
    @WithAttributes(label = "Class Name", required = false, description = "The class name to create")
    private UIInput<String> className;

    @Inject
    private InputComponentFactory componentFactory;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelNewComponentCommand.class).name(
                "Camel: New Component").category(Categories.create(CATEGORY))
                .description("Creates a new Camel component configured in Java code");
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        Project project = getSelectedProject(builder.getUIContext());
        final JavaSourceFacet facet = project.getFacet(JavaSourceFacet.class);

        componentName.setValueChoices(CamelCommandsHelper.createComponentDtoValues(project, getCamelCatalog(), null, false));
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

        targetPackage.setCompleter(new PackageNameCompleter(facet));
        targetPackage.addValidator(new PackageNameValidator());
        targetPackage.getFacet(HintsFacet.class).setInputType(InputType.JAVA_PACKAGE_PICKER);
        // if there is only one package then use that as default
        Set<String> packages = new RouteBuilderCompleter(facet).getPackages();
        if (packages.size() == 1) {
            targetPackage.setDefaultValue(first(packages));
        }

        className.addValidator(new ClassNameValidator(false));
        className.getFacet(HintsFacet.class).setInputType(InputType.JAVA_CLASS_PICKER);

        builder.add(componentName).add(instanceName).add(targetPackage).add(className);
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();

        ComponentDto component = componentName.getValue();
        String camelComponentName = component.getScheme();

        attributeMap.put("componentName", camelComponentName);
        attributeMap.put("componentName", componentName.getValue().getScheme());
        attributeMap.put("instanceName", instanceName.getValue());
        attributeMap.put("targetPackage", targetPackage.getValue());
        // calculate a default class name if none provided
        String name = className.getValue();
        if (Strings.isBlank(name)) {
            name = Strings.capitalize(instanceName.getValue()) + "ComponentFactory";
        }
        attributeMap.put("className", name);

        boolean cdi = CamelCommandsHelper.isCdiProject(getSelectedProject(context));
        boolean spring = CamelCommandsHelper.isSpringProject(getSelectedProject(context));
        if (cdi) {
            attributeMap.put("kind", "cdi");
        } else if (spring) {
            attributeMap.put("kind", "spring");
        } else {
            attributeMap.put("kind", "java");
        }

        // must be same component name to allow reusing existing navigation result
        String previous = (String) attributeMap.get("componentName");
        if (previous != null && previous.equals(camelComponentName)) {
            NavigationResult navigationResult = (NavigationResult) attributeMap.get("navigationResult");
            if (navigationResult != null) {
                return navigationResult;
            }
        }

        // we need to figure out how many options there is so we can as many steps we need
        UIContext ui = context.getUIContext();
        List<InputOptionByGroup> groups = createUIInputsForCamelComponent(camelComponentName, MAX_OPTIONS,
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
            ConfigureComponentPropertiesStep step = new ConfigureComponentPropertiesStep(projectFactory, dependencyInstaller, getCamelCatalog(),
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
    }

}

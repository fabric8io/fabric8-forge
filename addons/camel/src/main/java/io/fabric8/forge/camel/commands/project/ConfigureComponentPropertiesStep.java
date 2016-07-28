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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.forge.addon.utils.CamelProjectHelper;
import io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper;
import io.fabric8.forge.camel.commands.project.helper.PoorMansLogger;
import io.fabric8.forge.camel.commands.project.model.CamelComponentDetails;
import org.apache.camel.catalog.CamelCatalog;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.parser.java.facets.JavaSourceFacet;
import org.jboss.forge.addon.parser.java.resources.JavaResource;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.wizard.UIWizardStep;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.util.Strings;

import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.getEnumJavaTypeComponent;
import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.isDefaultValueComponent;
import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.isNonePlaceholderEnumValueComponent;
import static io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper.ensureCamelArtifactIdAdded;
import static io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper.loadCamelComponentDetails;

public class ConfigureComponentPropertiesStep extends AbstractCamelProjectCommand implements UIWizardStep {

    private static final PoorMansLogger LOG = new PoorMansLogger(true);

    private final DependencyInstaller dependencyInstaller;
    private final CamelCatalog camelCatalog;

    private final String componentName;
    private final String group;
    private final List<InputComponent> allInputs;
    private final List<InputComponent> inputs;
    private final boolean last;
    private final int index;
    private final int total;

    // TODO: add support for spring boot application.yaml/properties

    public ConfigureComponentPropertiesStep(ProjectFactory projectFactory,
                                            DependencyInstaller dependencyInstaller,
                                            CamelCatalog camelCatalog,
                                            String componentName, String group,
                                            List<InputComponent> allInputs,
                                            List<InputComponent> inputs,
                                            boolean last, int index, int total) {
        this.projectFactory = projectFactory;
        this.dependencyInstaller = dependencyInstaller;
        this.camelCatalog = camelCatalog;
        this.componentName = componentName;
        this.group = group;
        this.allInputs = allInputs;
        this.inputs = inputs;
        this.last = last;
        // we want to 1-based
        this.index = index + 1;
        this.total = total;
    }

    public String getGroup() {
        return group;
    }

    public int getIndex() {
        return index;
    }

    public int getTotal() {
        return total;
    }

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(ConfigureComponentPropertiesStep.class).name(
                "Camel: Component options").category(Categories.create(CATEGORY))
                .description(String.format("Configure %s options (%s of %s)", group, index, total));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initializeUI(UIBuilder builder) throws Exception {
        if (inputs != null) {
            for (InputComponent input : inputs) {
                builder.add(input);
            }
        }
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        return null;
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        // only execute if we are last
        if (last) {
            Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();
            String kind = mandatoryAttributeValue(attributeMap, "kind");

            if ("springboot".equals(kind)) {
                return doExecuteSpringBoot(context);
            } else {
                return doExecuteJava(context);
            }
        } else {
            return null;
        }
    }

    private Result doExecuteJava(UIExecutionContext context) throws Exception {
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();
        try {
            String camelComponentName = mandatoryAttributeValue(attributeMap, "componentName");
            String componentInstanceName = mandatoryAttributeValue(attributeMap, "instanceName");
            String generatePackageName = mandatoryAttributeValue(attributeMap, "targetPackage");
            String generateClassName = mandatoryAttributeValue(attributeMap, "className");
            String kind = mandatoryAttributeValue(attributeMap, "kind");

            Project project = getSelectedProject(context);
            JavaSourceFacet facet = project.getFacet(JavaSourceFacet.class);

            // does the project already have camel?
            Dependency core = CamelProjectHelper.findCamelCoreDependency(project);
            if (core == null) {
                return Results.fail("The project does not include camel-core");
            }

            // lets find the camel component class
            CamelComponentDetails details = new CamelComponentDetails();
            Result result = loadCamelComponentDetails(camelCatalog, camelComponentName, details);
            if (result != null) {
                return result;
            }
            result = ensureCamelArtifactIdAdded(project, details, dependencyInstaller);
            if (result != null) {
                return result;
            }

            // do we already have a class with the name
            String fqn = generatePackageName != null ? generatePackageName + "." + generateClassName : generateClassName;

            JavaResource existing = facet.getJavaResource(fqn);
            if (existing != null && existing.exists()) {
                return Results.fail("A class with name " + fqn + " already exists");
            }

            // need to parse to be able to extends another class
            final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);
            javaClass.setName(generateClassName);
            if (generatePackageName != null) {
                javaClass.setPackage(generatePackageName);
            }

            // collect all the options that was set
            Map<String, Object> options = new LinkedHashMap<>();
            for (InputComponent input : allInputs) {
                String key = input.getName();
                // only use the value if a value was set (and the value is not the same as the default value)
                if (input.hasValue()) {
                    Object value = input.getValue();
                    String text = input.getValue().toString();
                    if (text != null) {
                        boolean matchDefault = isDefaultValueComponent(camelCatalog, camelComponentName, key, text);
                        if ("none".equals(text)) {
                            // special for enum that may have a none as dummy placeholder which we should not add
                            boolean nonePlaceholder = isNonePlaceholderEnumValueComponent(camelCatalog, camelComponentName, key);
                            if (!matchDefault && !nonePlaceholder) {
                                options.put(key, value);
                            }
                        } else if (!matchDefault) {
                            options.put(key, value);
                        }
                    }
                } else if (input.isRequired() && input.hasDefaultValue()) {
                    // if its required then we need to grab the value
                    Object value = input.getValue();
                    if (value != null) {
                        options.put(key, value);
                    }
                }
            }

            Set<String> extraJavaImports = new HashSet<>();

            // generate the correct class payload based on the style...
            StringBuilder buffer = new StringBuilder();
            for (Map.Entry<String, Object> option : options.entrySet()) {
                String key = option.getKey();
                Object value = option.getValue();

                String valueExpression = null;
                // special for enum types
                String enumJavaType = getEnumJavaTypeComponent(camelCatalog, camelComponentName, key);
                if (enumJavaType != null) {
                    extraJavaImports.add(enumJavaType);
                    String simpleName = enumJavaType;
                    int idx = simpleName.lastIndexOf(".");
                    if (idx != -1) {
                        simpleName = simpleName.substring(idx + 1);
                    }
                    valueExpression = simpleName + "." + value;;
                } else if (value instanceof String) {
                    String text = value.toString();
                    if (!Strings.isBlank(text)) {
                        valueExpression = "\"" + text + "\"";
                    }
                } else {
                    valueExpression = value.toString();
                }
                if (valueExpression != null) {
                    buffer.append("\n");
                    buffer.append("component.set");
                    buffer.append(Strings.capitalize(key));
                    buffer.append("(");
                    buffer.append(valueExpression);
                    buffer.append(");");
                }
            }

            String configurationCode = buffer.toString();
            if (kind.equals("cdi")) {
                CamelCommandsHelper.createCdiComponentProducerClass(javaClass, details, camelComponentName, componentInstanceName, configurationCode, extraJavaImports);
            } else if (kind.equals("spring")) {
                CamelCommandsHelper.createSpringComponentFactoryClass(javaClass, details, camelComponentName, componentInstanceName, configurationCode, extraJavaImports);
            } else {
                CamelCommandsHelper.createJavaComponentFactoryClass(javaClass, details, camelComponentName, componentInstanceName, configurationCode, extraJavaImports);
            }

            JavaResource javaResource = facet.saveJavaSource(javaClass);

            // if we are in an GUI editor then open the file
            if (isRunningInGui(context.getUIContext())) {
                context.getUIContext().setSelection(javaResource);
            }

            return Results.success("Created new class " + generateClassName);

        } catch (Exception e) {
            return Results.fail(e.getMessage());
        }
    }

    private Result doExecuteSpringBoot(UIExecutionContext context) throws Exception {
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();
        try {
            String camelComponentName = mandatoryAttributeValue(attributeMap, "componentName");

            Project project = getSelectedProject(context);
            JavaSourceFacet facet = project.getFacet(JavaSourceFacet.class);

            // does the project already have camel?
            Dependency core = CamelProjectHelper.findCamelCoreDependency(project);
            if (core == null) {
                return Results.fail("The project does not include camel-core");
            }

            // collect all the options that was set
            Map<String, Object> options = new LinkedHashMap<>();
            for (InputComponent input : allInputs) {
                String key = input.getName();
                // only use the value if a value was set (and the value is not the same as the default value)
                if (input.hasValue()) {
                    Object value = input.getValue();
                    String text = input.getValue().toString();
                    if (text != null) {
                        boolean matchDefault = isDefaultValueComponent(camelCatalog, camelComponentName, key, text);
                        if ("none".equals(text)) {
                            // special for enum that may have a none as dummy placeholder which we should not add
                            boolean nonePlaceholder = isNonePlaceholderEnumValueComponent(camelCatalog, camelComponentName, key);
                            if (!matchDefault && !nonePlaceholder) {
                                options.put(key, value);
                            }
                        } else if (!matchDefault) {
                            options.put(key, value);
                        }
                    }
                } else if (input.isRequired() && input.hasDefaultValue()) {
                    // if its required then we need to grab the value
                    Object value = input.getValue();
                    if (value != null) {
                        options.put(key, value);
                    }
                }
            }

            String applicationFile = mandatoryAttributeValue(attributeMap, "applicationFile");

            // load content of file (properties or yaml)


            StringBuilder buffer = new StringBuilder();
            for (Map.Entry<String, Object> option : options.entrySet()) {
                String key = option.getKey();
                Object value = option.getValue();

                // TODO: if lookup option then add # as prefix if missing

                String prefix = "camel.component." + camelComponentName;
                String line = prefix + "." + key + "=" + value;

                LOG.info(line);

                buffer.append(line).append("\n");
            }



            return Results.success("Edited Camel component " + camelComponentName);

        } catch (Exception e) {
            return Results.fail(e.getMessage());
        }
    }

    /**
     * Returns the mandatory String value of the given name
     *
     * @throws IllegalArgumentException if the value is not available in the given attribute map
     */
    public static String mandatoryAttributeValue(Map<Object, Object> attributeMap, String name) {
        Object value = attributeMap.get(name);
        if (value != null) {
            String text = value.toString();
            if (!Strings.isBlank(text)) {
                return text;
            }
        }
        throw new IllegalArgumentException("The attribute value '" + name + "' did not get passed on from the previous wizard page");
    }
}

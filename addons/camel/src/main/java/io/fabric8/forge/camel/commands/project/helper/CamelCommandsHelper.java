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
package io.fabric8.forge.camel.commands.project.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import io.fabric8.forge.addon.utils.CamelProjectHelper;
import io.fabric8.forge.camel.commands.project.completer.CamelComponentsCompleter;
import io.fabric8.forge.camel.commands.project.completer.CamelComponentsLabelCompleter;
import io.fabric8.forge.camel.commands.project.completer.CamelEipsCompleter;
import io.fabric8.forge.camel.commands.project.completer.CamelEipsLabelCompleter;
import io.fabric8.forge.camel.commands.project.dto.ComponentDto;
import io.fabric8.forge.camel.commands.project.dto.EipDto;
import io.fabric8.forge.camel.commands.project.model.CamelComponentDetails;
import io.fabric8.forge.camel.commands.project.model.InputOptionByGroup;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.JSonSchemaHelper;
import org.jboss.forge.addon.convert.ConverterFactory;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.dependencies.builder.DependencyBuilder;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.shell.ui.ShellContext;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.InputComponentFactory;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.util.Strings;

import static io.fabric8.forge.addon.utils.UIHelper.createUIInput;
import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.endpointComponentName;

public final class CamelCommandsHelper {

    public static Iterable<String> createComponentLabelValues(Project project, CamelCatalog camelCatalog) {
        return new CamelComponentsLabelCompleter(project, camelCatalog).getValueChoices();
    }

    public static Iterable<String> createEipLabelValues(Project project, CamelCatalog camelCatalog) {
        return new CamelEipsLabelCompleter(project, camelCatalog).getValueChoices();
    }

    public static Callable<Iterable<ComponentDto>> createAllComponentDtoValues(final Project project, final CamelCatalog camelCatalog,
                                                                            final UISelectOne<String> componentCategoryFilter,
                                                                            final boolean excludeComponentsOnClasspath) {
        // use callable so we can live update the filter
        return new Callable<Iterable<ComponentDto>>() {
            @Override
            public Iterable<ComponentDto> call() throws Exception {
                String label = componentCategoryFilter.getValue();
                return new CamelComponentsCompleter(project, camelCatalog, null, excludeComponentsOnClasspath, true, false, false).getValueChoices(label);
            }
        };
    }

    public static Callable<Iterable<ComponentDto>> createComponentDtoValues(final Project project, final CamelCatalog camelCatalog,
                                                                            final UISelectOne<String> componentCategoryFilter,
                                                                            final boolean excludeComponentsOnClasspath) {
        // use callable so we can live update the filter
        return new Callable<Iterable<ComponentDto>>() {
            @Override
            public Iterable<ComponentDto> call() throws Exception {
                String label = componentCategoryFilter != null ? componentCategoryFilter.getValue() : null;
                return new CamelComponentsCompleter(project, camelCatalog, null, excludeComponentsOnClasspath, false, false, false).getValueChoices(label);
            }
        };
    }

    public static Callable<Iterable<ComponentDto>> createComponentDtoValues(final Project project, final CamelCatalog camelCatalog,
                                                                            final UISelectOne<String> componentCategoryFilter,
                                                                            final boolean excludeComponentsOnClasspath,
                                                                            final boolean consumerOnly, final boolean producerOnly) {
        // use callable so we can live update the filter
        return new Callable<Iterable<ComponentDto>>() {
            @Override
            public Iterable<ComponentDto> call() throws Exception {
                String label = componentCategoryFilter != null ? componentCategoryFilter.getValue() : null;
                return new CamelComponentsCompleter(project, camelCatalog, null, excludeComponentsOnClasspath, false, consumerOnly, producerOnly).getValueChoices(label);
            }
        };
    }

    public static Callable<Iterable<EipDto>> createAllEipDtoValues(final Project project, final CamelCatalog camelCatalog, final UISelectOne<String> eipCategoryFilter) {
        // use callable so we can live update the filter
        return new Callable<Iterable<EipDto>>() {
            @Override
            public Iterable<EipDto> call() throws Exception {
                String label = eipCategoryFilter != null ? eipCategoryFilter.getValue() : null;
                return new CamelEipsCompleter(project, camelCatalog).getValueChoices(label);
            }
        };
    }

    /**
     * Populates the details for the given component, returning a Result if it fails.
     */
    public static Result loadCamelComponentDetails(CamelCatalog camelCatalog, String camelComponentName, CamelComponentDetails details) {
        String json = camelCatalog.componentJSonSchema(camelComponentName);
        if (json == null) {
            return Results.fail("Could not find catalog entry for component name: " + camelComponentName);
        }
        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("component", json, false);

        for (Map<String, String> row : data) {
            String javaType = row.get("javaType");
            if (!Strings.isNullOrEmpty(javaType)) {
                details.setComponentClassQName(javaType);
            }
            String groupId = row.get("groupId");
            if (!Strings.isNullOrEmpty(groupId)) {
                details.setGroupId(groupId);
            }
            String artifactId = row.get("artifactId");
            if (!Strings.isNullOrEmpty(artifactId)) {
                details.setArtifactId(artifactId);
            }
            String version = row.get("version");
            if (!Strings.isNullOrEmpty(version)) {
                details.setVersion(version);
            }
        }
        if (Strings.isNullOrEmpty(details.getComponentClassQName())) {
            return Results.fail("Could not find fully qualified class name in catalog for component name: " + camelComponentName);
        }
        return null;
    }

    public static Result ensureCamelArtifactIdAdded(Project project, CamelComponentDetails details, DependencyInstaller dependencyInstaller) {
        String groupId = details.getGroupId();
        String artifactId = details.getArtifactId();
        Dependency core = CamelProjectHelper.findCamelCoreDependency(project);
        if (core == null) {
            return Results.fail("The project does not include camel-core");
        }

        // we want to use same version as camel-core if its a camel component
        // otherwise use the version from the dto
        String version;
        if ("org.apache.camel".equals(groupId)) {
            version = core.getCoordinate().getVersion();
        } else {
            version = details.getVersion();
        }
        DependencyBuilder component = DependencyBuilder.create().setGroupId(groupId)
                .setArtifactId(artifactId).setVersion(version);

        // install the component
        dependencyInstaller.install(project, component);
        return null;
    }

    public static boolean isCdiProject(Project project) {
        return CamelProjectHelper.findCamelCDIDependency(project) != null;
    }

    public static boolean isSpringProject(Project project) {
        return CamelProjectHelper.findCamelSpringDependency(project) != null;
    }

    public static boolean isBlueprintProject(Project project) {
        return CamelProjectHelper.findCamelBlueprintDependency(project) != null;
    }

    public static void createCdiComponentProducerClass(JavaClassSource javaClass, CamelComponentDetails details, String camelComponentName,
                                                       String componentInstanceName, String configurationCode, Set<String> extraJavaImports) {
        javaClass.addImport("javax.enterprise.inject.Produces");
        javaClass.addImport("javax.inject.Singleton");
        javaClass.addImport("javax.inject.Named");
        javaClass.addImport(details.getComponentClassQName());
        if (extraJavaImports != null) {
            for (String extra : extraJavaImports) {
                javaClass.addImport(extra);
            }
        }

        String componentClassName = details.getComponentClassName();
        String methodName = "create" + Strings.capitalize(componentInstanceName) + "Component";

        String body = componentClassName + " component = new " + componentClassName + "();" + configurationCode + "\nreturn component;";

        MethodSource<JavaClassSource> method = javaClass.addMethod()
                .setPublic()
                .setReturnType(componentClassName)
                .setName(methodName)
                .setBody(body);

        method.addAnnotation("Named").setStringValue(camelComponentName);
        method.addAnnotation("Produces");
        method.addAnnotation("Singleton");
    }

    public static void createSpringComponentFactoryClass(JavaClassSource javaClass, CamelComponentDetails details, String camelComponentName,
                                                         String componentInstanceName, String configurationCode, Set<String> extraJavaImports) {
        javaClass.addAnnotation("Component");

        javaClass.addImport("org.springframework.beans.factory.config.BeanDefinition");
        javaClass.addImport("org.springframework.beans.factory.annotation.Qualifier");
        javaClass.addImport("org.springframework.context.annotation.Bean");
        javaClass.addImport("org.springframework.context.annotation.Scope");
        javaClass.addImport("org.springframework.stereotype.Component");
        javaClass.addImport(details.getComponentClassQName());
        if (extraJavaImports != null) {
            for (String extra : extraJavaImports) {
                javaClass.addImport(extra);
            }
        }

        String componentClassName = details.getComponentClassName();
        String methodName = "create" + Strings.capitalize(componentInstanceName) + "Component";

        String body = componentClassName + " component = new " + componentClassName + "();" + configurationCode + "\nreturn component;";

        MethodSource<JavaClassSource> method = javaClass.addMethod()
                .setPublic()
                .setReturnType(componentClassName)
                .setName(methodName)
                .setBody(body);

        method.addAnnotation("Qualifier").setStringValue(camelComponentName);
        method.addAnnotation("Bean");
        method.addAnnotation("Scope").setLiteralValue("BeanDefinition.SCOPE_SINGLETON");
    }

    public static void createJavaComponentFactoryClass(JavaClassSource javaClass, CamelComponentDetails details, String camelComponentName,
                                                       String componentInstanceName, String configurationCode, Set<String> extraJavaImports) {
        javaClass.addImport(details.getComponentClassQName());
        if (extraJavaImports != null) {
            for (String extra : extraJavaImports) {
                javaClass.addImport(extra);
            }
        }

        String componentClassName = details.getComponentClassName();
        String methodName = "create" + Strings.capitalize(componentInstanceName) + "Component";

        String body = componentClassName + " component = new " + componentClassName + "();" + configurationCode + "\nreturn component;";

        javaClass.addMethod()
                .setPublic()
                .setReturnType(componentClassName)
                .setName(methodName)
                .setBody(body);
    }

    /**
     * Converts a java type as a string to a valid input type and returns the class or null if its not supported
     */
    public static Class<Object> loadValidInputTypes(String javaType, String type) {
        // we have generics in the javatype, if so remove it so its loadable from a classloader
        int idx = javaType.indexOf('<');
        if (idx > 0) {
            javaType = javaType.substring(0, idx);
        }

        try {
            Class<Object> clazz = getPrimitiveWrapperClassType(type);
            if (clazz == null) {
                clazz = loadPrimitiveWrapperType(javaType);
            }
            if (clazz == null) {
                clazz = loadStringSupportedType(javaType);
            }
            if (clazz == null) {
                try {
                    clazz = (Class<Object>) Class.forName(javaType);
                } catch (Throwable e) {
                    // its a custom java type so use String as the input type, so you can refer to it using # lookup
                    if ("object".equals(type)) {
                        clazz = loadPrimitiveWrapperType("java.lang.String");
                    }
                }
            }

            // favor specialized UI for these types
            if (clazz != null && (clazz.equals(String.class) || clazz.equals(Date.class) || clazz.equals(Boolean.class)
                    || clazz.isPrimitive() || Number.class.isAssignableFrom(clazz))) {
                return clazz;
            }

            // its a custom java type so use String as the input type, so you can refer to it using # lookup
            if ("object".equals(type)) {
                clazz = loadPrimitiveWrapperType("java.lang.String");
                return clazz;
            }

        } catch (Throwable e) {
            // ignore errors
        }
        return null;
    }

    private static Class loadStringSupportedType(String javaType) {
        if ("java.io.File".equals(javaType)) {
            return String.class;
        } else if ("java.net.URL".equals(javaType)) {
            return String.class;
        } else if ("java.net.URI".equals(javaType)) {
            return String.class;
        }
        return null;
    }

    /**
     * Gets the JSon schema primitive type.
     *
     * @param name the json type
     * @return the primitive Java Class type
     */
    public static Class getPrimitiveWrapperClassType(String name) {
        if ("string".equals(name)) {
            return String.class;
        } else if ("boolean".equals(name)) {
            return Boolean.class;
        } else if ("integer".equals(name)) {
            return Integer.class;
        } else if ("number".equals(name)) {
            return Float.class;
        }

        return null;
    }

    private static Class loadPrimitiveWrapperType(String name) {
        // special for byte[] or Object[] as its common to use
        if ("java.lang.byte[]".equals(name) || "byte[]".equals(name)) {
            return Byte[].class;
        } else if ("java.lang.Byte[]".equals(name) || "Byte[]".equals(name)) {
            return Byte[].class;
        } else if ("java.lang.Object[]".equals(name) || "Object[]".equals(name)) {
            return Object[].class;
        } else if ("java.lang.String[]".equals(name) || "String[]".equals(name)) {
            return String[].class;
            // and these is common as well
        } else if ("java.lang.String".equals(name) || "String".equals(name)) {
            return String.class;
        } else if ("java.lang.Boolean".equals(name) || "Boolean".equals(name)) {
            return Boolean.class;
        } else if ("boolean".equals(name)) {
            return Boolean.class;
        } else if ("java.lang.Integer".equals(name) || "Integer".equals(name)) {
            return Integer.class;
        } else if ("int".equals(name)) {
            return Integer.class;
        } else if ("java.lang.Long".equals(name) || "Long".equals(name)) {
            return Long.class;
        } else if ("long".equals(name)) {
            return Long.class;
        } else if ("java.lang.Short".equals(name) || "Short".equals(name)) {
            return Short.class;
        } else if ("short".equals(name)) {
            return Short.class;
        } else if ("java.lang.Byte".equals(name) || "Byte".equals(name)) {
            return Byte.class;
        } else if ("byte".equals(name)) {
            return Byte.class;
        } else if ("java.lang.Float".equals(name) || "Float".equals(name)) {
            return Float.class;
        } else if ("float".equals(name)) {
            return Float.class;
        } else if ("java.lang.Double".equals(name) || "Double".equals(name)) {
            return Double.class;
        } else if ("double".equals(name)) {
            return Double.class;
        } else if ("java.lang.Character".equals(name) || "Character".equals(name)) {
            return Character.class;
        } else if ("char".equals(name)) {
            return Character.class;
        }
        return null;
    }

    public static List<InputOptionByGroup> createUIInputsForCamelComponent(String camelComponentName, int maxOptionsPerPage,
                                                                          CamelCatalog camelCatalog, InputComponentFactory componentFactory, ConverterFactory converterFactory, UIContext ui) throws Exception {
        return doCreateUIInputsForCamel(camelComponentName, null, maxOptionsPerPage, false, false, camelCatalog, componentFactory, converterFactory, ui, false);
    }

    public static List<InputOptionByGroup> createUIInputsForCamelEndpoint(String camelComponentName, String uri, int maxOptionsPerPage, boolean consumerOnly, boolean producerOnly,
                                                                          CamelCatalog camelCatalog, InputComponentFactory componentFactory, ConverterFactory converterFactory, UIContext ui) throws Exception {
        return doCreateUIInputsForCamel(camelComponentName, uri, maxOptionsPerPage, consumerOnly, producerOnly, camelCatalog, componentFactory, converterFactory, ui, true);
    }

    private static List<InputOptionByGroup> doCreateUIInputsForCamel(String camelComponentName, String uri, int maxOptionsPerPage, boolean consumerOnly, boolean producerOnly,
        CamelCatalog camelCatalog, InputComponentFactory componentFactory, ConverterFactory converterFactory, UIContext ui, boolean endpoint) throws Exception {

        List<InputOptionByGroup> answer = new ArrayList<>();

        if (camelComponentName == null && uri != null) {
            camelComponentName = endpointComponentName(uri);
        }

        String json = camelCatalog.componentJSonSchema(camelComponentName);
        if (json == null) {
            throw new IllegalArgumentException("Could not find catalog entry for component name: " + camelComponentName);
        }

        // is the component consumer or producer only, if so we do not need any kind of filter
        boolean componentConsumerOnly = CamelCatalogHelper.isComponentConsumerOnly(camelCatalog, camelComponentName);
        boolean componentProducerOnly = CamelCatalogHelper.isComponentProducerOnly(camelCatalog, camelComponentName);
        if (componentConsumerOnly || componentProducerOnly) {
            // reset the filters as the component can only be one of them anyway, so we should show all options
            consumerOnly = false;
            producerOnly = false;
        }


        List<Map<String, String>> data;
        if (endpoint) {
            data = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        } else {
            data = JSonSchemaHelper.parseJsonSchema("componentProperties", json, true);
        }

        Map<String, String> currentValues = uri != null ? camelCatalog.endpointProperties(uri) : Collections.EMPTY_MAP;

        if (data != null) {

            // whether to prompt for all fields or not in the interactive mode
            boolean promptInInteractiveMode = false;
            if (ui instanceof ShellContext) {
                // we want to prompt if the command was executed without any arguments
                boolean params = ((ShellContext) ui).getCommandLine().hasParameters();
                promptInInteractiveMode = !params;
            }

            List<InputComponent> inputs = new ArrayList<>();
            InputOptionByGroup current = new InputOptionByGroup();
            current.setGroup(null);
            current.setInputs(inputs);

            Set<String> namesAdded = new HashSet<>();

            for (Map<String, String> propertyMap : data) {
                String name = propertyMap.get("name");
                String kind = propertyMap.get("kind");
                String group = propertyMap.get("group");
                String label = propertyMap.get("label");
                String type = propertyMap.get("type");
                String javaType = propertyMap.get("javaType");
                String deprecated = propertyMap.get("deprecated");
                String required = propertyMap.get("required");
                String currentValue = currentValues.get(name);
                String defaultValue = propertyMap.get("defaultValue");
                String description = propertyMap.get("description");
                String enums = propertyMap.get("enum");
                String prefix = propertyMap.get("prefix");
                String multiValue = propertyMap.get("multiValue");

                if (current.getGroup() == null) {
                    current.setGroup(group);
                }
                // its a new group
                if (group != null && !group.equals(current.getGroup())) {
                    if (!current.getInputs().isEmpty()) {
                        // this group is now done so add to answer
                        answer.add(current);
                    }

                    // get ready for a new group
                    inputs = new ArrayList<>();
                    current = new InputOptionByGroup();
                    current.setGroup(group);
                    current.setInputs(inputs);
                }

                // filter out options in case we should only include consumers or producers only
                if (consumerOnly && label != null) {
                    if (!label.contains("consumer")) {
                        continue;
                    }
                }
                if (producerOnly && label != null) {
                    if (!label.contains("producer")) {
                        continue;
                    }
                }

                if (!Strings.isNullOrEmpty(name)) {
                    Class<Object> inputClazz = CamelCommandsHelper.loadValidInputTypes(javaType, type);
                    if (inputClazz != null) {
                        if (namesAdded.add(name)) {

                            // if its an enum and its optional then make sure there is a default value
                            // if no default value exists then add none as the 1st choice default value
                            // otherwise the GUI makes us force to select an option which is not what we want
                            if (enums != null && (required == null || "false".equals(required))) {
                                if (defaultValue == null || defaultValue.isEmpty()) {
                                    defaultValue = "none";
                                    if (!enums.startsWith("none,")) {
                                        enums = "none," + enums;
                                    }
                                }
                            }

                            boolean multi = "true".equals(multiValue);
                            InputComponent input = createUIInput(ui.getProvider(), componentFactory, converterFactory, null, name, inputClazz, required, currentValue, defaultValue, enums, description, promptInInteractiveMode, multi, prefix);
                            if (input != null) {
                                inputs.add(input);

                                // if we hit max options then create a new group
                                if (inputs.size() == maxOptionsPerPage) {
                                    // this group is now done so add to answer
                                    if (!current.getInputs().isEmpty()) {
                                        answer.add(current);
                                    }
                                    // get ready for a new group
                                    inputs = new ArrayList<>();
                                    current = new InputOptionByGroup();
                                    current.setGroup(group);
                                    current.setInputs(inputs);
                                }
                            }
                        }
                    }
                }
            }

            // add last group if there was some new inputs
            if (!inputs.isEmpty()) {
                answer.add(current);
            }
        }

        // use common as faullback group name
        for (InputOptionByGroup group : answer) {
            if (group.getGroup() == null) {
                group.setGroup("common");
            }
        }

        return answer;
    }

    public static List<InputOptionByGroup> createUIInputsForCamelEIP(String eip, int maxOptionsPerPage, Map<String, String> currentValues,
                                                                     CamelCatalog camelCatalog, InputComponentFactory componentFactory, ConverterFactory converterFactory, UIContext ui) throws Exception {
        List<InputOptionByGroup> answer = new ArrayList<>();

        String json = camelCatalog.modelJSonSchema(eip);
        if (json == null) {
            throw new IllegalArgumentException("Could not find catalog entry for model name: " + eip);
        }

        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("properties", json, true);

        if (data != null) {

            // whether to prompt for all fields or not in the interactive mode
            boolean promptInInteractiveMode = false;
            if (ui instanceof ShellContext) {
                // we want to prompt if the command was executed without any arguments
                boolean params = ((ShellContext) ui).getCommandLine().hasParameters();
                promptInInteractiveMode = !params;
            }

            List<InputComponent> inputs = new ArrayList<>();
            InputOptionByGroup current = new InputOptionByGroup();
            current.setGroup(null);
            current.setInputs(inputs);

            Set<String> namesAdded = new HashSet<>();

            for (Map<String, String> propertyMap : data) {
                String name = propertyMap.get("name");
                String kind = propertyMap.get("kind");
                String group = propertyMap.get("group");
                String label = propertyMap.get("label");
                String type = propertyMap.get("type");
                String javaType = propertyMap.get("javaType");
                String deprecated = propertyMap.get("deprecated");
                String required = propertyMap.get("required");
                String currentValue = currentValues != null ? currentValues.get(name) : null;
                String defaultValue = propertyMap.get("defaultValue");
                String description = propertyMap.get("description");
                String enums = propertyMap.get("enum");
                String oneOf = propertyMap.get("oneOf");

                // we do not want to include outputs as an option
                if ("outputs".equals(name)) {
                    continue;
                }

                if (current.getGroup() == null) {
                    current.setGroup(group);
                }
                // its a new group
                if (group != null && !group.equals(current.getGroup())) {
                    if (!current.getInputs().isEmpty()) {
                        // this group is now done so add to answer
                        answer.add(current);
                    }

                    // get ready for a new group
                    inputs = new ArrayList<>();
                    current = new InputOptionByGroup();
                    current.setGroup(group);
                    current.setInputs(inputs);
                }

                if (!Strings.isNullOrEmpty(name)) {
                    Class<Object> inputClazz = CamelCommandsHelper.loadValidInputTypes(javaType, type);
                    if (inputClazz != null) {
                        if (namesAdded.add(name)) {

                            // if its an enum and its optional then make sure there is a default value
                            // if no default value exists then add none as the 1st choice default value
                            // otherwise the GUI makes us force to select an option which is not what we want
                            if (enums != null && (required == null || "false".equals(required))) {
                                if (defaultValue == null || defaultValue.isEmpty()) {
                                    defaultValue = "none";
                                    if (!enums.startsWith("none,")) {
                                        enums = "none," + enums;
                                    }
                                }
                            }

                            // if its an enum and its optional then make sure there is a default value
                            // if no default value exists then add none as the 1st choice default value
                            // otherwise the GUI makes us force to select an option which is not what we want
                            if (oneOf != null && (required == null || "false".equals(required))) {
                                if (defaultValue == null || defaultValue.isEmpty()) {
                                    defaultValue = "none";
                                    if (!oneOf.startsWith("none,")) {
                                        oneOf = "none," + oneOf;
                                    }
                                }
                            }

                            if ("expression".equals(kind) && currentValue != null) {
                                // these 3 languages do not have a value and should therefore not be required
                                if ("method".equals(currentValue) || "tokenize".equals(currentValue) || "xtokenize".equals(currentValue)) {
                                    required = "false";
                                }
                                // fix current value from bean to method because that is the oneOf choices
                                if ("bean".equals(currentValue)) {
                                    currentValue = "method";
                                }
                            }

                            // we cannot have both enum and oneOf at the same time
                            String enumsOrOneOfs = enums != null ? enums : oneOf;

                            InputComponent input = createUIInput(ui.getProvider(), componentFactory, converterFactory, eip, name, inputClazz, required, currentValue, defaultValue, enumsOrOneOfs, description, promptInInteractiveMode, false, null);

                            if (input != null) {
                                inputs.add(input);

                                // if its an expression then we need to add an input for the actual value and another for extra values
                                if ("expression".equals(kind)) {
                                    currentValue = currentValues != null ? currentValues.get(name + "_value") : null;
                                    InputComponent input2 = createUIInput(ui.getProvider(), componentFactory, converterFactory, eip, name + "_value", String.class, required, currentValue, null, null, description, promptInInteractiveMode, false, null);
                                    if (input2 != null) {
                                        inputs.add(input2);
                                        // listen for changes in the selection of languages
                                        input.addValueChangeListener(event -> {
                                            String value = (String) event.getNewValue();
                                            // these 3 languages do not have a value and should therefore not be required
                                            if ("method".equals(value) || "tokenize".equals(value) || "xtokenize".equals(value)) {
                                                input2.setRequired(false);
                                            } else {
                                                input2.setRequired(true);
                                            }
                                        });
                                    }
                                    String extra = currentValues != null ? currentValues.get(name + "_extra") : null;
                                    InputComponent input3 = createUIInput(ui.getProvider(), componentFactory, converterFactory, eip, name + "_extra", String.class, "false", extra, null, null, description, promptInInteractiveMode, true, "");
                                    if (input3 != null) {
                                        inputs.add(input3);
                                    }
                                }

                                // if we hit max options then create a new group
                                if (inputs.size() == maxOptionsPerPage) {
                                    // this group is now done so add to answer
                                    if (!current.getInputs().isEmpty()) {
                                        answer.add(current);
                                    }
                                    // get ready for a new group
                                    inputs = new ArrayList<>();
                                    current = new InputOptionByGroup();
                                    current.setGroup(group);
                                    current.setInputs(inputs);
                                }
                            }
                        }
                    }
                }
            }

            // add last group if there was some new inputs
            if (!inputs.isEmpty()) {
                answer.add(current);
            }
        }

        // use common as faullback group name
        for (InputOptionByGroup group : answer) {
            if (group.getGroup() == null) {
                group.setGroup("common");
            }
        }

        return answer;
    }

}

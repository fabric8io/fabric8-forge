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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.forge.addon.utils.CamelProjectHelper;
import io.fabric8.forge.addon.utils.LineNumberHelper;
import io.fabric8.forge.camel.commands.project.helper.PoorMansLogger;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.util.IntrospectionSupport;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.projects.facets.ResourcesFacet;
import org.jboss.forge.addon.projects.facets.WebResourcesFacet;
import org.jboss.forge.addon.resource.FileResource;
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
import org.jboss.forge.roaster.model.util.Strings;

import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.getModelJavaType;
import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.isModelDefaultValue;
import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.isModelExpressionKind;
import static io.fabric8.forge.camel.commands.project.helper.CamelXmlHelper.dumpModelAsXml;

public abstract class ConfigureEipPropertiesStep extends AbstractCamelProjectCommand implements UIWizardStep {

    private static final PoorMansLogger LOG = new PoorMansLogger(false);

    private final CamelCatalog camelCatalog;

    private final String eipName;
    private final String group;
    private final List<InputComponent> allInputs;
    private final List<InputComponent> inputs;
    private final boolean last;
    private final int index;
    private final int total;

    public ConfigureEipPropertiesStep(ProjectFactory projectFactory,
                                      CamelCatalog camelCatalog,
                                      String eipName, String group,
                                      List<InputComponent> allInputs,
                                      List<InputComponent> inputs,
                                      boolean last, int index, int total) {
        this.projectFactory = projectFactory;
        this.camelCatalog = camelCatalog;
        this.eipName = eipName;
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

    public String getEipName() {
        return eipName;
    }

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(ConfigureEipPropertiesStep.class).name(
                "Camel: EIP options").category(Categories.create(CATEGORY))
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
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();

        // only execute if we are last
        if (last) {
            String kind = mandatoryAttributeValue(attributeMap, "kind");
            if ("xml".equals(kind)) {
                return executeXml(context, attributeMap);
            } else {
                throw new UnsupportedOperationException("Java not yet supported");
            }
        } else {
            return null;
        }
    }

    protected Result executeXml(UIExecutionContext context, Map<Object, Object> attributeMap) throws Exception {
        String mode = mandatoryAttributeValue(attributeMap, "mode");
        String xml = mandatoryAttributeValue(attributeMap, "xml");
        String pattern = mandatoryAttributeValue(attributeMap, "pattern");

        // line number for the parent node
        String lineNumber = mandatoryAttributeValue(attributeMap, "lineNumber");
        String lineNumberEnd = mandatoryAttributeValue(attributeMap, "lineNumberEnd");

        Project project = getSelectedProject(context);
        ResourcesFacet facet = project.getFacet(ResourcesFacet.class);
        WebResourcesFacet webResourcesFacet = null;
        if (project.hasFacet(WebResourcesFacet.class)) {
            webResourcesFacet = project.getFacet(WebResourcesFacet.class);
        }

        // does the project already have camel?
        Dependency core = CamelProjectHelper.findCamelCoreDependency(project);
        if (core == null) {
            return Results.fail("The project does not include camel-core");
        }

        // collect all the options that was set
        Map<String, Object> options = new HashMap<String, Object>();
        Map<String, String> expressionKeys = new HashMap<String, String>();
        Map<String, String> expressionValues = new HashMap<String, String>();
        for (InputComponent input : allInputs) {
            String key = input.getName();

            // special for expression
            boolean expressionKey = isModelExpressionKind(camelCatalog, eipName, key);
            boolean expressionValue = key.endsWith("_value");

            // only use the value if a value was set (and the value is not the same as the default value)
            if (input.hasValue()) {
                String value = input.getValue().toString();
                if (value != null) {
                    // do not add the value if it match the default value
                    boolean matchDefault = isModelDefaultValue(camelCatalog, eipName, key, value);
                    if (!matchDefault) {
                        if (expressionKey) {
                            expressionKeys.put(key, value);
                        } else if (expressionValue) {
                            expressionValues.put(key, value);
                        } else {
                            options.put(key, value);
                        }
                    }
                }
            } else if (input.isRequired() && input.hasDefaultValue()) {
                // if its required then we need to grab the value
                String value = input.getValue().toString();
                if (value != null) {
                    if (expressionKey) {
                        expressionKeys.put(key, value);
                    } else if (expressionValue) {
                        expressionValues.put(key, value);
                    } else {
                        options.put(key, value);
                    }
                }
            }
        }

        String javaType = getModelJavaType(camelCatalog, eipName);
        if (javaType == null) {
            return Results.fail("Cannot find javaType for " + eipName);
        }

        FileResource file = facet != null ? facet.getResource(xml) : null;
        if (file == null || !file.exists()) {
            file = webResourcesFacet != null ? webResourcesFacet.getWebResource(xml) : null;
        }
        if (file == null || !file.exists()) {
            return Results.fail("Cannot find XML file " + xml);
        }

        String modelXml = null;
        try {
            ClassLoader cl = CamelCatalog.class.getClassLoader();
            Class clazz = cl.loadClass(javaType);
            Object instance = clazz.newInstance();

            // set properties on the instance
            IntrospectionSupport.setProperties(instance, options);

            for (Map.Entry<String, String> entry : expressionKeys.entrySet()) {
                String name = entry.getKey();
                String language = entry.getValue();
                String text = expressionValues.get(name + "_value");

                // build the model
                String lanJavaType = getModelJavaType(camelCatalog, language);
                if (lanJavaType != null) {
                    Class clazz2 = cl.loadClass(lanJavaType);
                    Object instance2 = clazz2.newInstance();

                    // set the value of the expression
                    IntrospectionSupport.setProperty(instance2, "expression", text);
                    // add the expression to the parent
                    IntrospectionSupport.setProperty(instance, name, instance2);
                }
            }

            // we should only include the end tag if the node does not have children
            boolean includeEndTag = true;
            String children = optionalAttributeValue(attributeMap, "nodeChildren");
            if (children != null) {
                includeEndTag = "false".equals(children);
            }
            // calculate indent to use
            int indent = calculateIndent(file, lineNumber);
            LOG.info("Calculated indent " + indent);
            // marshal to xml
            modelXml = dumpModelAsXml(instance, cl, includeEndTag, indent);
            LOG.info("Generated XML model:\n" + modelXml + "\n");
        } catch (Exception e) {
            // ignore
        }

        if (modelXml == null) {
            return Results.fail("Cannot create XML model of the node");
        }

        return addOrEditModelXml(file, pattern, modelXml, xml, lineNumber, lineNumberEnd, mode);
    }

    protected int calculateIndent(FileResource file, String lineNumber) throws Exception {
        List<String> lines = LineNumberHelper.readLines(file.getResourceInputStream());

        int idx = Integer.valueOf(lineNumber);

        int spaces = LineNumberHelper.leadingSpaces(lines, idx - 1);
        int spaces1 = LineNumberHelper.leadingSpaces(lines, idx);
        int spaces2 = LineNumberHelper.leadingSpaces(lines, idx + 1);

        int delta = Math.abs(spaces1 - spaces);
        int delta2 = Math.abs(spaces2 - spaces1);

        int answer = delta2 > 0 ? delta2 : delta;
        // the indent must minimum be 2 spaces
        return Math.max(answer, 2);
    }

    protected Result addOrEditModelXml(FileResource file, String pattern, String modelXml, String xml, String lineNumber, String lineNumberEnd, String mode) throws Exception {
        List<String> lines = LineNumberHelper.readLines(file.getResourceInputStream());
        if ("add".equals(mode)) {
            return addModelXml(pattern, lines, lineNumber, lineNumberEnd, modelXml, file, xml);
        } else {
            return editModelXml(pattern, lines, lineNumber, lineNumberEnd, modelXml, file, xml);
        }
    }

    protected abstract Result addModelXml(String pattern, List<String> lines, String lineNumber, String lineNumberEnd, String modelXml, FileResource file, String xml) throws Exception;

    protected abstract Result editModelXml(String pattern, List<String> lines, String lineNumber, String lineNumberEnd, String modelXml, FileResource file, String xml) throws Exception;

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

    /**
     * Returns the optional String value of the given name
     */
    public static String optionalAttributeValue(Map<Object, Object> attributeMap, String name) {
        Object value = attributeMap.get(name);
        if (value != null) {
            String text = value.toString();
            if (!Strings.isBlank(text)) {
                return text;
            }
        }
        return null;
    }
}

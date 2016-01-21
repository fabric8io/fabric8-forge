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
import org.apache.camel.catalog.CamelCatalog;
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

import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.isModelDefaultValue;

public abstract class ConfigureEipPropertiesStep extends AbstractCamelProjectCommand implements UIWizardStep {

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
        Map<String, String> options = new HashMap<String, String>();
        for (InputComponent input : allInputs) {
            String key = input.getName();
            // only use the value if a value was set (and the value is not the same as the default value)
            if (input.hasValue()) {
                String value = input.getValue().toString();
                if (value != null) {
                    // do not add the value if it match the default value
                    boolean matchDefault = isModelDefaultValue(camelCatalog, eipName, key, value);
                    if (!matchDefault) {
                        options.put(key, value);
                    }
                }
            } else if (input.isRequired() && input.hasDefaultValue()) {
                // if its required then we need to grab the value
                String value = input.getValue().toString();
                if (value != null) {
                    options.put(key, value);
                }
            }
        }

        // TODO: build the eip model, maybe use the catalog so we can build it from there
//        String uri = camelCatalog.asEndpointUriXml(camelComponentName, options, false);
//        if (uri == null) {
//            return Results.fail("Cannot create endpoint uri");
//        }
        String modelXml = "<todo/>";

        FileResource file = facet != null ? facet.getResource(xml) : null;
        if (file == null || !file.exists()) {
            file = webResourcesFacet != null ? webResourcesFacet.getWebResource(xml) : null;
        }
        if (file == null || !file.exists()) {
            return Results.fail("Cannot find XML file " + xml);
        }
        return addOrEditModelXml(file, modelXml, xml, lineNumber, lineNumberEnd);
    }

    protected Result addOrEditModelXml(FileResource file, String modelXml, String xml, String lineNumber, String lineNumberEnd) throws Exception {
        List<String> lines = LineNumberHelper.readLines(file.getResourceInputStream());
        return editModelXml(lines, lineNumber, lineNumberEnd, modelXml, file, xml);
    }

    protected abstract Result editModelXml(List<String> lines, String lineNumber, String lineNumberEnd, String modelXml, FileResource file, String xml) throws Exception;

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

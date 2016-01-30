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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import io.fabric8.forge.addon.utils.XmlLineNumberParser;
import io.fabric8.forge.camel.commands.project.dto.NodeDto;
import io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper;
import io.fabric8.forge.camel.commands.project.model.InputOptionByGroup;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.util.IntrospectionSupport;
import org.jboss.forge.addon.projects.Project;
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
import org.w3c.dom.Element;

import static io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper.createUIInputsForCamelEIP;
import static io.fabric8.forge.camel.commands.project.helper.CamelXmlHelper.xmlAsModel;

/**
 * Edits an EIP to an existing XML route
 */
public class CamelEditNodeXmlCommand extends AbstractCamelProjectCommand implements UIWizard {

    @Inject
    @WithAttributes(label = "XML File", required = true, description = "The XML file to use (either Spring or Blueprint)")
    private UISelectOne<String> xml;

    @Inject
    @WithAttributes(label = "Node", required = true, description = "Node to edit")
    private UISelectOne<NodeDto> node;

    @Inject
    private InputComponentFactory componentFactory;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelEditNodeXmlCommand.class).name(
                "Camel: Edit Node XML").category(Categories.create(CATEGORY))
                .description("Edits a node in a Camel XML file");
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
        UIContext context = builder.getUIContext();
        Map<Object, Object> attributeMap = context.getAttributeMap();
        attributeMap.remove("navigationResult");

        Project project = getSelectedProject(context);

        String first = configureXml(project, xml);
        configureNode(context, project, first, xml, node);

        builder.add(xml).add(node);
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        Project project = getSelectedProject(context);

        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();

        // always refresh these as the end user may have edited the instance name
        String xmlResourceName = xml.getValue();
        attributeMap.put("xml", xmlResourceName);
        attributeMap.put("mode", "edit");
        attributeMap.put("kind", "xml");

        NodeDto editNode = node.getValue();
        String key = editNode.getKey();

        // must be same node to allow reusing existing navigation result
        String previous = getNodeKey(attributeMap.get("node"));
        if (previous != null && previous.equals(key)) {
            NavigationResult navigationResult = (NavigationResult) attributeMap.get("navigationResult");
            if (navigationResult != null) {
                return navigationResult;
            }
        }

        attributeMap.put("node", key);
        String nodeName = editNode.getPattern();
        attributeMap.put("nodeName", nodeName);

        Element selectedElement = getSelectedCamelElementNode(project, xmlResourceName, key);
        if (selectedElement == null) {
            throw new IllegalArgumentException("Cannot find xml for node " + editNode);
        }
        String lineNumber = (String) selectedElement.getUserData(XmlLineNumberParser.LINE_NUMBER);
        String lineNumberEnd = (String) selectedElement.getUserData(XmlLineNumberParser.LINE_NUMBER_END);
        attributeMap.put("lineNumber", lineNumber);
        attributeMap.put("lineNumberEnd", lineNumberEnd);

        // we need to get all the options that are currently configured on the EIP so we have all the current values
        Map<String, String> options = new LinkedHashMap<>();
        try {
            ClassLoader cl = CamelCatalog.class.getClassLoader();

            // xml -> pojo
            Object model = xmlAsModel(selectedElement, cl);

            // extra options from model
            Map<String, Object> temp = new LinkedHashMap<>();
            IntrospectionSupport.getProperties(model, temp, null);
            for (Map.Entry<String, Object> entry : temp.entrySet()) {
                String k = entry.getKey();
                Object v = entry.getValue();

                // special for expression
                if (v instanceof ExpressionDefinition) {
                    ExpressionDefinition exp = (ExpressionDefinition) v;
                    String text = exp.getExpression();
                    String lan = exp.getLanguage();
                    options.put(k, lan);
                    options.put(k + "_value", text);
                } else {
                    // convert the value to a text based
                    String text = v != null ? v.toString() : null;
                    if (text != null) {
                        options.put(k, text);
                    }
                }
            }

        } catch (Exception e) {
            // ignore
        }


        UIContext ui = context.getUIContext();
        List<InputOptionByGroup> groups = createUIInputsForCamelEIP(nodeName, CamelAddEndpointDefinitionXmlCommand.MAX_OPTIONS,
                options, getCamelCatalog(), componentFactory, converterFactory, ui);

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
            EditNodeXmlStep step = new EditNodeXmlStep(projectFactory, getCamelCatalog(),
                    nodeName, current.getGroup(), allInputs, current.getInputs(), last, i, pages);
            builder.add(step);
        }
        NavigationResult navigationResult = builder.build();
        attributeMap.put("navigationResult", navigationResult);
        return navigationResult;
    }

    private String getNodeKey(Object value) {
        if (value instanceof NodeDto) {
            NodeDto nodeDto = (NodeDto) value;
            return nodeDto.getKey();
        } else if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        return Results.success();
    }

}

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

import io.fabric8.forge.addon.utils.XmlLineNumberParser;
import io.fabric8.forge.camel.commands.project.dto.EipDto;
import io.fabric8.forge.camel.commands.project.dto.NodeDto;
import io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper;
import io.fabric8.forge.camel.commands.project.helper.PoorMansLogger;
import io.fabric8.forge.camel.commands.project.model.InputOptionByGroup;
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

import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.createEipDto;
import static io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper.createUIInputsForCamelEIP;

/**
 * Adds an EIP to an existing XML route
 */
public class CamelAddNodeXmlCommand extends AbstractCamelProjectCommand implements UIWizard {

    private static final PoorMansLogger LOG = new PoorMansLogger(false);

    @Inject
    @WithAttributes(label = "XML File", required = true, description = "The XML file to use (either Spring or Blueprint)")
    private UISelectOne<String> xml;

    @Inject
    @WithAttributes(label = "Parent Node", required = true, description = "Parent node (where to add the node)")
    private UISelectOne<String> parent;
    private transient List<NodeDto> parents;

    @Inject
    @WithAttributes(label = "Filter", required = false, description = "To filter patterns")
    private UISelectOne<String> nameFilter;

    @Inject
    @WithAttributes(label = "Pattern", required = true, description = "The pattern to add")
    private UISelectOne<EipDto> name;

    @Inject
    private InputComponentFactory componentFactory;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelAddNodeXmlCommand.class).name(
                "Camel: Add Node XML").category(Categories.create(CATEGORY))
                .description("Adds a node in a Camel XML file");
    }

    @Override
    public boolean isEnabled(UIContext context) {
        // TODO: Allow to add in GUI from current cursor position
        boolean enabled = super.isEnabled(context);
        if (enabled) {
            // we should only be enabled in non gui
            boolean gui = isRunningInGui(context);
            enabled = !gui;
        }
        if (enabled) {
            // must have xml files with camel routes to be enabled
            Project project = getSelectedProject(context);
            String currentFile = getSelectedFile(context);
            String selected = configureXml(project, xml, currentFile);
            return selected != null;
        }
        return false;
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        UIContext context = builder.getUIContext();
        Map<Object, Object> attributeMap = context.getAttributeMap();
        attributeMap.remove("navigationResult");

        Project project = getSelectedProject(context);
        String currentFile = getSelectedFile(context);

        String selected = configureXml(project, xml, currentFile);
        parents = configureXmlNodes(context, project, selected, xml, parent);

        nameFilter.setValueChoices(CamelCommandsHelper.createEipLabelValues(project, getCamelCatalog()));
        nameFilter.setDefaultValue("<all>");

        name.setValueChoices(CamelCommandsHelper.createAllEipDtoValues(project, getCamelCatalog(), nameFilter));
        // include converter from string->dto
        name.setValueConverter(text -> createEipDto(getCamelCatalog(), text));

        builder.add(xml).add(parent).add(nameFilter).add(name);
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        Project project = getSelectedProject(context);

        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();

        // always refresh these as the end user may have edited the instance name
        String xmlResourceName = xml.getValue();
        attributeMap.put("xml", xmlResourceName);
        attributeMap.put("mode", "add");
        attributeMap.put("kind", "xml");

        NodeDto parentNode = null;
        int selectedIdx = parent.getSelectedIndex();
        if (selectedIdx != -1) {
            parentNode = parents.get(selectedIdx);
        }

        LOG.info("Parent node " + parentNode);

        String key = parentNode != null ? parentNode.getKey() : null;

        String nodeName = name.getValue() != null ? name.getValue().getName() : null;

        // must be same node and modelName to allow reusing existing navigation result
        String previous = getNodeKey(attributeMap.get("node"));
        String previous2 = (String) attributeMap.get("name");
        if (previous != null && previous.equals(key) && previous2 != null && previous2.equals(nodeName)) {
            NavigationResult navigationResult = (NavigationResult) attributeMap.get("navigationResult");
            if (navigationResult != null) {
                return navigationResult;
            }
        }

        attributeMap.put("node", key);
        attributeMap.put("name", nodeName);
        attributeMap.put("pattern", parentNode.getPattern());

        Element selectedElement = getSelectedCamelElementNode(project, xmlResourceName, key);
        if (selectedElement == null) {
            throw new IllegalArgumentException("Cannot find xml for node " + parentNode);
        }
        String lineNumber = (String) selectedElement.getUserData(XmlLineNumberParser.LINE_NUMBER);
        String lineNumberEnd = (String) selectedElement.getUserData(XmlLineNumberParser.LINE_NUMBER_END);
        attributeMap.put("lineNumber", lineNumber);
        attributeMap.put("lineNumberEnd", lineNumberEnd);

        UIContext ui = context.getUIContext();
        List<InputOptionByGroup> groups = createUIInputsForCamelEIP(nodeName, MAX_OPTIONS,
                null, getCamelCatalog(), componentFactory, converterFactory, ui);

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
            AddNodeXmlStep step = new AddNodeXmlStep(projectFactory, getCamelCatalog(),
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

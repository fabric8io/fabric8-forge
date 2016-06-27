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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import io.fabric8.forge.addon.utils.XmlLineNumberParser;
import io.fabric8.forge.camel.commands.project.completer.XmlEndpointsCompleter;
import io.fabric8.forge.camel.commands.project.dto.NodeDto;
import io.fabric8.forge.camel.commands.project.helper.CamelXmlHelper;
import io.fabric8.forge.camel.commands.project.helper.PoorMansLogger;
import io.fabric8.forge.camel.commands.project.model.CamelEndpointDetails;
import io.fabric8.forge.camel.commands.project.model.InputOptionByGroup;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.util.IntrospectionSupport;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper.createUIInputsForCamelEIP;
import static io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper.createUIInputsForCamelEndpoint;
import static io.fabric8.forge.camel.commands.project.helper.CamelXmlHelper.loadCamelXmlFileAsDom;
import static io.fabric8.forge.camel.commands.project.helper.CamelXmlHelper.xmlAsModel;

/**
 * Edits an EIP to an existing XML route
 */
public class CamelEditNodeXmlCommand extends AbstractCamelProjectCommand implements UIWizard {

    private static final PoorMansLogger LOG = new PoorMansLogger(false);

    @Inject
    @WithAttributes(label = "XML File", required = true, description = "The XML file to use (either Spring or Blueprint)")
    private UISelectOne<String> xml;

    @Inject
    @WithAttributes(label = "Node", required = true, description = "Node to edit")
    private UISelectOne<String> node;
    private transient List<NodeDto> nodes;

    @Inject
    private InputComponentFactory componentFactory;

    @Inject
    private DependencyInstaller dependencyInstaller;

    private XmlEndpointsCompleter xmlCompleter;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelEditNodeXmlCommand.class).name(
                "Camel: Edit Node XML").category(Categories.create(CATEGORY))
                .description("Edits a node in a Camel XML file");
    }

    @Override
    public boolean isEnabled(UIContext context) {
        boolean enabled = super.isEnabled(context);
        // this command works both in Web UI and in GUI mode
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
        String selectedFile = getSelectedFile(builder.getUIContext());
        final String currentFile = asRelativeFile(builder.getUIContext(), selectedFile);
        final int cursorLineNumber = getCurrentCursorLine(builder.getUIContext());
        LOG.info("Current file " + currentFile + " line number: " + cursorLineNumber);

        String selected = configureXml(project, xml, currentFile);
        nodes = configureXmlNodes(context, project, selected, xml, node);

        NodeDto candidate = null;

        FileResource file = getXmlResourceFile(project, currentFile);
        InputStream resourceInputStream = file.getResourceInputStream();
        Document root = loadCamelXmlFileAsDom(resourceInputStream);
        if (root != null) {
            for (NodeDto node : nodes) {
                String key = node.getKey();
                Node selectedNode = CamelXmlHelper.findCamelNodeInDocument(root, key);
                LOG.info("Node " + key + " in XML " + selectedNode);

                if (selectedNode != null) {
                    // we need to add after the parent node, so use line number information from the parent
                    String lineNumber = (String) selectedNode.getUserData(XmlLineNumberParser.LINE_NUMBER);
                    String lineNumberEnd = (String) selectedNode.getUserData(XmlLineNumberParser.LINE_NUMBER_END);

                    LOG.info("Node " + key + " line " + lineNumber + "-" + lineNumberEnd);

                    if (lineNumber != null && lineNumberEnd != null) {
                        int start = Integer.parseInt(lineNumber);
                        int end = Integer.parseInt(lineNumberEnd);
                        if (start <= cursorLineNumber && end >= cursorLineNumber) {
                            // its okay to select new candidate as a child is better than a parent if its within the range
                            LOG.info("Selecting candidate " + node);
                            candidate = node;
                        }
                    }
                }
            }
        }

        if (candidate != null) {
            // lets pre-select the EIP from the cursor line so the wizard can move on
            node.setDefaultValue(candidate.getLabel());
            node.setRequired(false);
            xml.setRequired(false);
        } else {
            // show the UI where you can chose the xml and EIPs to select
            builder.add(xml).add(node);
        }
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();

        // always refresh these as the end user may have edited the instance name
        String xmlResourceName = xml.getValue();
        attributeMap.put("xml", xmlResourceName);
        attributeMap.put("mode", "edit");
        attributeMap.put("kind", "xml");

        NodeDto editNode = null;
        int selectedIdx = node.getSelectedIndex();
        if (selectedIdx != -1) {
            editNode = nodes.get(selectedIdx);
        }

        String key = editNode != null ? editNode.getKey() : null;

        // must be same node to allow reusing existing navigation result
        String previous = getNodeKey(attributeMap.get("node"));
        if (previous != null && previous.equals(key)) {
            NavigationResult navigationResult = (NavigationResult) attributeMap.get("navigationResult");
            if (navigationResult != null) {
                return navigationResult;
            }
        }

        LOG.info("Edit node " + editNode + " pattern " + editNode.getPattern());

        attributeMap.put("node", key);
        String nodeName = editNode.getPattern();
        attributeMap.put("nodeName", nodeName);
        attributeMap.put("pattern", editNode.getPattern());

        // if its "from" or "to" then lets edit the node as an endpoint
        if (editNode != null && ("from".equals(editNode.getPattern()) || "to".equals(editNode.getPattern()))) {
            return nextEditEndpoint(context, xmlResourceName, key, editNode);
        } else {
            return nextEditEip(context, xmlResourceName, key, editNode, nodeName);
        }
    }

    private NavigationResult nextEditEndpoint(UINavigationContext context, String xmlResourceName, String key, NodeDto editNode) throws Exception {
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();

        // find all endpoints
        xmlCompleter = createXmlEndpointsCompleter(context.getUIContext(), xmlResourceName::equals);

        String uri = editNode.getProperty("uri");
        LOG.info("Endpoint uri " + uri);

        CamelEndpointDetails detail = xmlCompleter.getEndpointDetail(uri);
        LOG.info("Endpoint detail " + detail);

        if (detail == null) {
            return null;
        }

        attributeMap.put("componentName", detail.getEndpointComponentName());
        attributeMap.put("instanceName", detail.getEndpointInstance());
        attributeMap.put("endpointUri", detail.getEndpointUri());
        attributeMap.put("lineNumber", detail.getLineNumber());
        attributeMap.put("lineNumberEnd", detail.getLineNumberEnd());
        attributeMap.put("mode", "edit");
        attributeMap.put("xml", detail.getFileName());
        attributeMap.put("kind", "xml");

        // we need to figure out how many options there is so we can as many steps we need
        String camelComponentName = detail.getEndpointComponentName();
        uri = detail.getEndpointUri();

        String json = getCamelCatalog().componentJSonSchema(camelComponentName);
        if (json == null) {
            throw new IllegalArgumentException("Could not find catalog entry for component name: " + camelComponentName);
        }

        LOG.info("Component json: " + json);

        boolean consumerOnly = detail.isConsumerOnly();
        boolean producerOnly = detail.isProducerOnly();

        UIContext ui = context.getUIContext();
        List<InputOptionByGroup> groups = createUIInputsForCamelEndpoint(camelComponentName, uri, MAX_OPTIONS, consumerOnly, producerOnly,
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
            ConfigureEndpointPropertiesStep step = new ConfigureEndpointPropertiesStep(projectFactory, dependencyInstaller, getCamelCatalog(),
                    camelComponentName, current.getGroup(), allInputs, current.getInputs(), last, i, pages);
            builder.add(step);
        }

        NavigationResult navigationResult = builder.build();
        attributeMap.put("navigationResult", navigationResult);
        return navigationResult;
    }

    private NavigationResult nextEditEip(UINavigationContext context, String xmlResourceName, String key, NodeDto editNode, String nodeName) throws Exception {
        Project project = getSelectedProject(context);
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();

        Element selectedElement = getSelectedCamelElementNode(project, xmlResourceName, key);
        if (selectedElement == null) {
            throw new IllegalArgumentException("Cannot find xml for node " + editNode);
        }
        String lineNumber = (String) selectedElement.getUserData(XmlLineNumberParser.LINE_NUMBER);
        String lineNumberEnd = (String) selectedElement.getUserData(XmlLineNumberParser.LINE_NUMBER_END);
        attributeMap.put("lineNumber", lineNumber);
        attributeMap.put("lineNumberEnd", lineNumberEnd);

        // if we edit a node then it may have children. We should then only edit until the first child starts
        if (editNode.getChildren() != null && !editNode.getChildren().isEmpty()) {
            NodeDto child = editNode.getChildren().get(0);
            Element childElement = getSelectedCamelElementNode(project, xmlResourceName, child.getKey());
            String childLineNumber = (String) childElement.getUserData(XmlLineNumberParser.LINE_NUMBER);
            if (childLineNumber != null) {
                int num = Integer.valueOf(childLineNumber) - 1;
                attributeMap.put("lineNumberEnd", "" + num);
            }
            attributeMap.put("nodeChildren", "true");
        } else {
            attributeMap.put("nodeChildren", "false");
        }

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
        List<InputOptionByGroup> groups = createUIInputsForCamelEIP(nodeName, MAX_OPTIONS,
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
        return null;
    }

}

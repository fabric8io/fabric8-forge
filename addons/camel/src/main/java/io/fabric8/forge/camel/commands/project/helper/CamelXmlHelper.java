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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.fabric8.camel.tooling.util.CamelModelHelper;
import io.fabric8.camel.tooling.util.RouteXml;
import io.fabric8.camel.tooling.util.XmlModel;
import io.fabric8.forge.addon.utils.CommandHelpers;
import io.fabric8.forge.camel.commands.project.dto.ContextDto;
import io.fabric8.forge.camel.commands.project.dto.NodeDto;
import io.fabric8.forge.camel.commands.project.dto.RouteDto;
import io.fabric8.utils.Files;
import io.fabric8.utils.Objects;
import io.fabric8.utils.Strings;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spring.CamelContextFactoryBean;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.input.UIInput;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static io.fabric8.forge.addon.utils.Files.joinPaths;

public final class CamelXmlHelper {

    public static Node findEndpointById(Document dom, String endpointId) {
        NodeList list = dom.getElementsByTagName("endpoint");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            if ("endpoint".equals(child.getNodeName())) {
                // okay its an endpoint so if we can match by id attribute
                String id = child.getAttributes().getNamedItem("id").getNodeValue();
                if (endpointId.equals(id)) {
                    return child;
                }
            }
        }
        return null;
    }

    public static List<Element> findAllContexts(Document dom) {
        List<Element> nodes = new ArrayList<>();
        NodeList list = dom.getElementsByTagName("camelContext");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            String ns = child.getNamespaceURI();
            if (ns == null) {
                NamedNodeMap attrs = child.getAttributes();
                if (attrs != null) {
                    Node node = attrs.getNamedItem("xmlns");
                    if (node != null) {
                        ns = node.getNodeValue();
                    }
                }
            }
            // assume no namespace its for camel
            if (ns == null || ns.contains("camel")) {
                if (child instanceof Element) {
                    nodes.add((Element) child);
                }
            }
        }
        return nodes;
    }

    public static List<Node> findAllEndpoints(Document dom) {
        List<Node> nodes = new ArrayList<>();

        NodeList list = dom.getElementsByTagName("endpoint");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            if ("endpoint".equals(child.getNodeName())) {
                // it may not be a camel namespace, so skip those
                String ns = child.getNamespaceURI();
                if (ns == null) {
                    NamedNodeMap attrs = child.getAttributes();
                    if (attrs != null) {
                        Node node = attrs.getNamedItem("xmlns");
                        if (node != null) {
                            ns = node.getNodeValue();
                        }
                    }
                }
                // assume no namespace its for camel
                if (ns == null || ns.contains("camel")) {
                    nodes.add(child);
                }
            }
        }

        list = dom.getElementsByTagName("onException");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            findAllUrisRecursive(child, nodes);
        }
        list = dom.getElementsByTagName("onCompletion");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            findAllUrisRecursive(child, nodes);
        }
        list = dom.getElementsByTagName("intercept");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            findAllUrisRecursive(child, nodes);
        }
        list = dom.getElementsByTagName("interceptFrom");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            findAllUrisRecursive(child, nodes);
        }
        list = dom.getElementsByTagName("interceptSendToEndpoint");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            findAllUrisRecursive(child, nodes);
        }
        list = dom.getElementsByTagName("rest");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            if ("route".equals(child.getNodeName()) || "to".equals(child.getNodeName())) {
                findAllUrisRecursive(child, nodes);
            }
        }
        list = dom.getElementsByTagName("route");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            if ("route".equals(child.getNodeName())) {
                findAllUrisRecursive(child, nodes);
            }
        }

        return nodes;
    }

    private static void findAllUrisRecursive(Node node, List<Node> nodes) {
        // okay its a route so grab all uri attributes we can find
        String url = getSafeAttribute(node, "uri");
        if (url != null) {
            nodes.add(node);
        }

        NodeList children = node.getChildNodes();
        if (children != null) {
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    findAllUrisRecursive(child, nodes);
                }
            }
        }
    }

    public static List<Node> findAllSimpleExpressions(Document dom) {
        List<Node> nodes = new ArrayList<>();

        NodeList list = dom.getElementsByTagName("route");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            if ("route".equals(child.getNodeName())) {
                findAllSimpleExpressionsRecursive(child, nodes);
            }
        }

        return nodes;
    }

    private static void findAllSimpleExpressionsRecursive(Node node, List<Node> nodes) {
        // okay its a route so grab if its <simple>
        if ("simple".equals(node.getNodeName())) {
            nodes.add(node);
        }

        NodeList children = node.getChildNodes();
        if (children != null) {
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    findAllSimpleExpressionsRecursive(child, nodes);
                }
            }
        }
    }

    public static String getSafeAttribute(Node node, String key) {
        if (node != null) {
            Node attr = node.getAttributes().getNamedItem(key);
            if (attr != null) {
                return attr.getNodeValue();
            }
        }
        return null;
    }

    public static String trimEndpointUri(String uri) {
        uri = uri.trim();
        // if the uri is using new-lines then remove whitespace noise before & and ? separator
        uri = uri.replaceAll("(\\s+)(\\&)", "$2");
        uri = uri.replaceAll("(\\&)(\\s+)", "$1");
        uri = uri.replaceAll("(\\?)(\\s+)", "$1");
        return uri;
    }

    public static String createFileName(UIInput<String> directory, UIInput<String> name) {
        return directory.getValue() != null ? directory.getValue() + File.separator + name.getValue() : name.getValue();
    }

    public static List<ContextDto> loadCamelContext(UIContext context, Project project, String xmlResourceName) throws Exception {
        String xmlFileName = joinPaths("src/main/resources", xmlResourceName);
        List<ContextDto> camelContexts = null;
        File xmlFile = CommandHelpers.getProjectContextFile(context, project, xmlFileName);
        if (Files.isFile(xmlFile)) {
            camelContexts = parseCamelContexts(xmlFile);
        }
        return camelContexts;
    }

    protected static List<ContextDto> parseCamelContexts(File xmlFile) throws Exception {
        List<ContextDto> camelContexts = new ArrayList<>();

        RouteXml routeXml = new RouteXml();
        XmlModel xmlModel = routeXml.unmarshal(xmlFile);

        // TODO we don't handle multiple contexts inside an XML file!
        CamelContextFactoryBean contextElement = xmlModel.getContextElement();
        String name = contextElement.getId();
        List<RouteDefinition> routeDefs = contextElement.getRoutes();
        ContextDto context = new ContextDto(name);
        camelContexts.add(context);
        String key = name;
        if (Strings.isNullOrBlank(key)) {
            key = "" + camelContexts.size();
        }
        context.setKey(key);
        List<NodeDto> routes = createRouteDtos(routeDefs, context);
        context.setChildren(routes);
        return camelContexts;
    }

    protected static List<NodeDto> createRouteDtos(List<RouteDefinition> routeDefs, ContextDto context) {
        List<NodeDto> answer = new ArrayList<>();
        for (RouteDefinition def : routeDefs) {
            RouteDto route = new RouteDto();
            route.setId(def.getId());
            route.setLabel(CamelModelHelper.getDisplayText(def));
            route.setDescription(CamelModelHelper.getDescription(def));
            answer.add(route);
            route.defaultKey(context, answer.size());

            addInputs(route, def.getInputs());
            addOutputs(route, def.getOutputs());
        }
        return answer;
    }

    protected static void addInputs(NodeDto owner, List<FromDefinition> inputs) {
        for (FromDefinition input : inputs) {
            addChild(owner, input);
        }
    }

    protected static void addOutputs(NodeDto owner, List<ProcessorDefinition<?>> outputs) {
        for (ProcessorDefinition<?> output : outputs) {
            addChild(owner, output);
        }
    }

    private static NodeDto addChild(NodeDto owner, OptionalIdentifiedDefinition definition) {
        NodeDto node = new NodeDto();
        node.setId(definition.getId());
        node.setLabel(CamelModelHelper.getDisplayText(definition));
        node.setDescription(CamelModelHelper.getDescription(definition));
        node.setPattern(CamelModelHelper.getPatternName(definition));
        owner.addChild(node);
        node.defaultKey(owner, owner.getChildren().size());

        if (definition instanceof ProcessorDefinition) {
            ProcessorDefinition processorDefinition = (ProcessorDefinition) definition;
            addOutputs(node, processorDefinition.getOutputs());
        }
        return node;
    }

    public static Node findCamelNodeInDocument(Document root, String key) {
        Node selectedNode = null;
        if (root != null && Strings.isNotBlank(key)) {
            String[] paths = key.split("/");
            NodeList camels = root.getElementsByTagName("camelContext");
            if (camels != null) {
                for (int i = 0, size = camels.getLength(); i < size; i++) {
                    Node node = camels.item(i);
                    boolean first = true;
                    for (String path : paths) {
                        if (first) {
                            first = false;
                            String actual = getIdOrIndex(node, 0);
                            if (!Objects.equal(actual, path)) {
                                node = null;
                            }
                        } else {
                            node = findCamelNodeForPath(node, path);
                        }
                        if (node == null) {
                            break;
                        }
                    }
                    if (node != null) {
                        return node;
                    }
                }
            }
        }
        return selectedNode;
    }

    protected static Node findCamelNodeForPath(Node node, String path) {
        NodeList childNodes = node.getChildNodes();
        if (childNodes != null) {
            int elementCount = 0;
            for (int i = 0, size = childNodes.getLength(); i < size; i++) {
                Node child = childNodes.item(i);
                if (child instanceof Element) {
                    String actual = getIdOrIndex(child, elementCount++);
                    if (Objects.equal(actual, path)) {
                        return child;
                    }
                }
            }
        }
        System.out.println("Could not find path '" + path + "' in node " + node.getNodeName() + " " + node.getAttributes());
        return null;
    }

    private static String getIdOrIndex(Node node, int index) {
        String answer = null;
        if (node instanceof Element) {
            Element element = (Element) node;
             answer = element.getAttribute("id");
            if (Strings.isNullOrBlank(answer)) {
                answer = "" + (index + 1);
            }
        }
        return answer;
    }

}

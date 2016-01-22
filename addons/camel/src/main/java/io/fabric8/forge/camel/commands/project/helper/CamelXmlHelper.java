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
package io.fabric8.forge.camel.commands.project.helper;

import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import io.fabric8.camel.tooling.util.CamelModelHelper;
import io.fabric8.camel.tooling.util.RouteXml;
import io.fabric8.camel.tooling.util.XmlModel;
import io.fabric8.forge.addon.utils.CommandHelpers;
import io.fabric8.forge.addon.utils.JaxbNoNamespaceWriter;
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
import org.apache.camel.model.ToDefinition;
import org.apache.camel.spring.CamelContextFactoryBean;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.input.UIInput;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static io.fabric8.forge.addon.utils.Files.joinPaths;

public final class CamelXmlHelper {

    public static final String JAXB_CONTEXT_PACKAGES = ""
            + "org.apache.camel:"
            + "org.apache.camel.model:"
            + "org.apache.camel.model.config:"
            + "org.apache.camel.model.dataformat:"
            + "org.apache.camel.model.language:"
            + "org.apache.camel.model.loadbalancer:"
            + "org.apache.camel.model.rest";

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
        List<ContextDto> camelContexts = null;

        // try with src/main/resources or src/main/webapp/WEB-INF
        String xmlFileName = joinPaths("src/main/resources", xmlResourceName);
        File xmlFile = CommandHelpers.getProjectContextFile(context, project, xmlFileName);
        if (!Files.isFile(xmlFile)) {
            xmlFileName = joinPaths("src/main/webapp/", xmlResourceName);
            xmlFile = CommandHelpers.getProjectContextFile(context, project, xmlFileName);
        }
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
            key = "_camelContext" + camelContexts.size();
        }
        context.setKey(key);
        List<NodeDto> routes = createRouteDtos(routeDefs, context);
        context.setChildren(routes);
        return camelContexts;
    }

    protected static List<NodeDto> createRouteDtos(List<RouteDefinition> routeDefs, ContextDto context) {
        List<NodeDto> answer = new ArrayList<>();
        Map<String, Integer> nodeCounts = new HashMap<>();
        for (RouteDefinition def : routeDefs) {
            RouteDto route = new RouteDto();
            route.setId(def.getId());
            route.setLabel(CamelModelHelper.getDisplayText(def));
            route.setDescription(CamelModelHelper.getDescription(def));
            answer.add(route);
            route.defaultKey(context, nodeCounts);

            addInputs(route, def.getInputs());
            addOutputs(route, def.getOutputs());
        }
        return answer;
    }

    protected static void addInputs(NodeDto owner, List<FromDefinition> inputs) {
        Map<String, Integer> nodeCounts = new HashMap<>();
        for (FromDefinition input : inputs) {
            addChild(owner, input, nodeCounts);
        }
    }

    protected static void addOutputs(NodeDto owner, List<ProcessorDefinition<?>> outputs) {
        Map<String, Integer> nodeCounts = new HashMap<>();
        for (ProcessorDefinition<?> output : outputs) {
            addChild(owner, output, nodeCounts);
        }
    }

    private static NodeDto addChild(NodeDto owner, OptionalIdentifiedDefinition definition, Map<String, Integer> nodeCounts) {
        NodeDto node = new NodeDto();
        node.setId(definition.getId());
        node.setLabel(CamelModelHelper.getDisplayText(definition));
        node.setDescription(CamelModelHelper.getDescription(definition));
        node.setPattern(CamelModelHelper.getPatternName(definition));
        owner.addChild(node);
        node.defaultKey(owner, nodeCounts);

        if (definition instanceof FromDefinition) {
            FromDefinition endpointDef = (FromDefinition) definition;
            node.setProperty("uri", endpointDef.getUri());
        }
        if (definition instanceof ToDefinition) {
            ToDefinition endpointDef = (ToDefinition) definition;
            node.setProperty("uri", endpointDef.getUri());
        }
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
                Map<String, Integer> rootNodeCounts = new HashMap<>();
                for (int i = 0, size = camels.getLength(); i < size; i++) {
                    Node node = camels.item(i);
                    boolean first = true;
                    for (String path : paths) {
                        if (first) {
                            first = false;
                            String actual = getIdOrIndex(node, rootNodeCounts);
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
            Map<String, Integer> nodeCounts = new HashMap<>();
            for (int i = 0, size = childNodes.getLength(); i < size; i++) {
                Node child = childNodes.item(i);
                if (child instanceof Element) {
                    String actual = getIdOrIndex(child, nodeCounts);
                    if (Objects.equal(actual, path)) {
                        return child;
                    }
                }
            }
        }
        return null;
    }

    private static String getIdOrIndex(Node node, Map<String, Integer> nodeCounts) {
        String answer = null;
        if (node instanceof Element) {
            Element element = (Element) node;
            String elementName = element.getTagName();
            Integer countObject = nodeCounts.get(elementName);
            int count = countObject != null ? countObject.intValue() : 0;
            nodeCounts.put(elementName, ++count);
            answer = element.getAttribute("id");
            if (Strings.isNullOrBlank(answer)) {
                answer = "_" + elementName + count;
            }
        }
        return answer;
    }

    /**
     * Dumps the definition as XML
     *
     * @param definition  the definition, such as a {@link org.apache.camel.NamedNode}
     * @param classLoader the class loader
     * @return the output in XML (is formatted)
     * @throws JAXBException is throw if error marshalling to XML
     */
    public static String dumpModelAsXml(Object definition, ClassLoader classLoader) throws JAXBException, XMLStreamException {
        JAXBContext jaxbContext = JAXBContext.newInstance(JAXB_CONTEXT_PACKAGES, classLoader);

        StringWriter buffer = new StringWriter();

        // we do not want to output namespace
        XMLStreamWriter delegate = XMLOutputFactory.newInstance().createXMLStreamWriter(buffer);
        JaxbNoNamespaceWriter writer = new JaxbNoNamespaceWriter(delegate);

        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "");
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.FALSE);
        marshaller.marshal(definition, writer);

        String answer = buffer.toString();

        // if there is only 1 element them collapse it, eg <log xxx></log> => <log xxx/>
        if (writer.getElements() == 1) {
            String token = "></" + writer.getRootElementName() + ">";
            answer = answer.replaceFirst(token, "/>");
        }

        return answer;
    }

    /**
     * Turns the xml into EIP model classes
     *
     * @param node        the node representing the XML
     * @param classLoader the class loader
     * @return the EIP model class
     * @throws JAXBException is throw if error unmarshalling XML to Object
     */
    public static Object xmlAsModel(Node node, ClassLoader classLoader) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(JAXB_CONTEXT_PACKAGES, classLoader);

        Unmarshaller marshaller = jaxbContext.createUnmarshaller();
        Object answer = marshaller.unmarshal(node);

        return answer;
    }

}

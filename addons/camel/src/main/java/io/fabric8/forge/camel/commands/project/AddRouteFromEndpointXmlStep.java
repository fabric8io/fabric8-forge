/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.forge.camel.commands.project;

import io.fabric8.forge.addon.utils.LineNumberHelper;
import io.fabric8.forge.addon.utils.XmlLineNumberParser;
import io.fabric8.utils.DomHelper;
import io.fabric8.utils.Strings;
import org.apache.camel.catalog.CamelCatalog;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.resource.FileResource;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.List;

/**
 * A wizard step to add a from endpoint for a newly created route
 */
public class AddRouteFromEndpointXmlStep extends ConfigureEndpointPropertiesStep {
    private final String routeId;
    private final String routeDescription;

    public AddRouteFromEndpointXmlStep(ProjectFactory projectFactory, DependencyInstaller dependencyInstaller, CamelCatalog camelCatalog, String componentName, String group, List<InputComponent> allInputs, List<InputComponent> inputs, boolean last, int index, int total, String routeId, String routeDescription) {
        super(projectFactory, dependencyInstaller, camelCatalog, componentName, group, allInputs, inputs, last, index, total);
        this.routeId = routeId;
        this.routeDescription = routeDescription;
    }

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(ConfigureEndpointPropertiesStep.class).name(
                "Camel: Route from Endpoint options").category(Categories.create(CATEGORY))
                .description(String.format("Configure %s options (%s of %s)", getGroup(), getIndex(), getTotal()));
    }

    @Override
    protected Result addOrEditEndpointXml(FileResource file, String uri, String endpointUrl, String endpointInstanceName, String xml, String lineNumber) throws Exception {

        Document root = XmlLineNumberParser.parseXml(file.getResourceInputStream());
        if (root != null) {
            NodeList camels = root.getElementsByTagName("camelContext");
            // TODO: what about 2+ camel's ?
            if (camels != null && camels.getLength() == 1) {
                Node camel = camels.item(0);
                Node camelContext = null;

                for (int i = 0; i < camel.getChildNodes().getLength(); i++) {
                    if ("camelContext".equals(camel.getNodeName())) {
                        camelContext = camel;
                    }

                    Node child = camel.getChildNodes().item(i);
                    if ("camelContext".equals(child.getNodeName())) {
                        camelContext = child;
                    }
                }

                if (camelContext == null) {
                    return Results.fail("Cannot find <camelContext> in XML file " + xml);
                }

                String indent0 = "\n  ";
                String indent1 = "\n    ";
                String indent2 = "\n      ";
                appendText(camelContext, indent1);
                Element route = DomHelper.addChildElement(camelContext, "route", indent2);
                if (Strings.isNotBlank(routeId)) {
                    route.setAttribute("id", routeId);
                }
                if (Strings.isNotBlank(routeDescription)) {
                    DomHelper.addChildElement(route, "description", routeDescription);
                    appendText(route, indent2);
                }

                Element from = DomHelper.addChildElement(route, "from");
                from.setAttribute("uri", uri);
                appendText(route, indent1);

                appendText(camelContext, indent0);
                String content = DomHelper.toXml(root);
                file.setContents(content);
            }
            return Results.success("Added route");
        } else {
            return Results.fail("Could not load camel XML");
        }
    }

    protected static void appendText(Node element, String text) {
        element.appendChild(element.getOwnerDocument().createTextNode(text));
    }
}

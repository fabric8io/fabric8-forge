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

import java.util.List;

import io.fabric8.forge.addon.utils.LineNumberHelper;
import io.fabric8.forge.addon.utils.XmlLineNumberParser;
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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A wizard step to add a from endpoint for a newly created route
 */
public class AddRouteFromEndpointXmlStep extends ConfigureEndpointPropertiesStep {
    private final String routeId;

    public AddRouteFromEndpointXmlStep(ProjectFactory projectFactory, DependencyInstaller dependencyInstaller, CamelCatalog camelCatalog, String componentName, String group, List<InputComponent> allInputs, List<InputComponent> inputs,
                                       boolean last, int index, int total, String routeId) {
        super(projectFactory, dependencyInstaller, camelCatalog, componentName, group, allInputs, inputs, last, index, total);
        this.routeId = routeId;
    }

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(ConfigureEndpointPropertiesStep.class).name(
                "Camel: Add Route").category(Categories.create(CATEGORY))
                .description(String.format("Configure %s options (%s of %s)", getGroup(), getIndex(), getTotal()));
    }

    @Override
    protected Result addOrEditEndpointXml(FileResource file, String uri, String endpointUrl, String endpointInstanceName, String xml, String lineNumber, String lineNumberEnd) throws Exception {
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

                // we need to add after the parent node, so use line number information from the parent
                lineNumber = (String) camelContext.getUserData(XmlLineNumberParser.LINE_NUMBER);
                lineNumberEnd = (String) camelContext.getUserData(XmlLineNumberParser.LINE_NUMBER_END);

                if (lineNumber != null && lineNumberEnd != null) {

                    String line1;
                    if (routeId != null) {
                        line1 = String.format("<route id=\"%s\">", routeId);
                    } else {
                        line1 = "<route>";
                    }
                    String line2 = String.format("<from uri=\"%s\"/>", uri);
                    String line3 = "</route>";

                    // if we created a new endpoint, then insert a new line with the endpoint details
                    List<String> lines = LineNumberHelper.readLines(file.getResourceInputStream());

                    // the list is 0-based, and line number is 1-based
                    int idx = Integer.valueOf(lineNumberEnd) - 1;
                    int spaces = LineNumberHelper.leadingSpaces(lines, idx);

                    line3 = LineNumberHelper.padString(line3, spaces + 2);
                    line2 = LineNumberHelper.padString(line2, spaces + 4);
                    line1 = LineNumberHelper.padString(line1, spaces + 2);

                    // check if previous line is empty or not
                    String text = lines.get(idx - 1);
                    boolean emptyLine = text == null || text.trim().isEmpty();

                    lines.add(idx, "");
                    lines.add(idx, line3);
                    lines.add(idx, line2);
                    lines.add(idx, line1);
                    if (!emptyLine) {
                        // insert empty lines around the added route (if needed to avoid 2x empty lines)
                        lines.add(idx, "");
                    }

                    // and save the file back
                    String content = LineNumberHelper.linesToString(lines);
                    file.setContents(content);
                }
                return Results.success("Added route");
            }
            return Results.fail("Cannot find Camel node in XML file: " + xml);
        } else {
            return Results.fail("Could not load camel XML");
        }
    }

}

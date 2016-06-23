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
import io.fabric8.forge.camel.commands.project.dto.NodeDto;
import io.fabric8.forge.camel.commands.project.helper.CamelXmlHelper;
import io.fabric8.forge.camel.commands.project.helper.PoorMansLogger;
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
import org.w3c.dom.Node;

/**
 * A wizard step to add a from or to endpoint to a node
 */
public class AddFromOrToEndpointXmlStep extends ConfigureEndpointPropertiesStep {

    private static final PoorMansLogger LOG = new PoorMansLogger(false);

    private final NodeDto parentNode;
    private final boolean isFrom;

    public AddFromOrToEndpointXmlStep(ProjectFactory projectFactory, DependencyInstaller dependencyInstaller, CamelCatalog camelCatalog, String componentName, String group, List<InputComponent> allInputs, List<InputComponent> inputs,
                                      boolean last, int index, int total, NodeDto parentNode, boolean isFrom) {
        super(projectFactory, dependencyInstaller, camelCatalog, componentName, group, allInputs, inputs, last, index, total);
        this.parentNode = parentNode;
        this.isFrom = isFrom;
    }

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(ConfigureEndpointPropertiesStep.class).name(
                "Camel: Add Endpoint").category(Categories.create(CATEGORY))
                .description(String.format("Configure %s options (%s of %s)", getGroup(), getIndex(), getTotal()));
    }

    @Override
    protected Result addOrEditEndpointXml(FileResource file, String uri, String endpointUrl, String endpointInstanceName, String xml, String lineNumber, String lineNumberEnd) throws Exception {
        String key = parentNode.getKey();
        if (Strings.isNullOrBlank(key)) {
            return Results.fail("Parent node has no key! " + parentNode + " in file " + file.getName());
        }

        Document root = XmlLineNumberParser.parseXml(file.getResourceInputStream());
        if (root != null) {
            Node selectedNode = CamelXmlHelper.findCamelNodeInDocument(root, key);
            if (selectedNode != null) {

                // we need to add after the parent node, so use line number information from the parent
                lineNumber = (String) selectedNode.getUserData(XmlLineNumberParser.LINE_NUMBER);
                lineNumberEnd = (String) selectedNode.getUserData(XmlLineNumberParser.LINE_NUMBER_END);

                LOG.info("Add endpoint at line number " + lineNumber + "-" + lineNumberEnd + " at selected node " + selectedNode.getNodeName());

                if (lineNumber != null && lineNumberEnd != null) {

                    String line;
                    if (isFrom) {
                        line = String.format("<from uri=\"%s\"/>", uri);
                    } else {
                        line = String.format("<to uri=\"%s\"/>", uri);
                    }

                    // read all the lines
                    List<String> lines = LineNumberHelper.readLines(file.getResourceInputStream());

                    // the list is 0-based, and line number is 1-based
                    // if from then use the start line number, otherwise use the end line number
                    int idx = isFrom ? Integer.valueOf(lineNumber) : Integer.valueOf(lineNumberEnd);
                    // use the same indent from the parent line
                    int spaces = LineNumberHelper.leadingSpaces(lines, idx - 1);
                    if (isFrom) {
                        // and append 2 if we are starting a new route with <from>
                        spaces += 2;
                    }
                    line = LineNumberHelper.padString(line, spaces);
                    // add the line at the position
                    lines.add(idx, line);

                    // and save the file back
                    String content = LineNumberHelper.linesToString(lines);
                    file.setContents(content);
                    return Results.success("Added: " + line.trim());
                }
            }
            return Results.fail("Cannot find Camel node in XML file: " + key);
        } else {
            return Results.fail("Cannot load Camel XML file: " + file.getName());
        }
    }

}

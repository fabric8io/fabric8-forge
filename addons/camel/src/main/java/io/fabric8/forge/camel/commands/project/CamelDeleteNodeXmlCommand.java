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
import javax.inject.Inject;

import io.fabric8.forge.addon.utils.LineNumberHelper;
import io.fabric8.forge.addon.utils.XmlLineNumberParser;
import io.fabric8.forge.camel.commands.project.dto.ContextDto;
import io.fabric8.forge.camel.commands.project.dto.NodeDto;
import io.fabric8.forge.camel.commands.project.helper.CamelXmlHelper;
import io.fabric8.utils.Strings;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.resource.FileResource;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class CamelDeleteNodeXmlCommand extends AbstractCamelProjectCommand {

    @Inject
    @WithAttributes(label = "XML File", required = true, description = "The XML file to use (either Spring or Blueprint)")
    private UISelectOne<String> xml;

    @Inject
    @WithAttributes(label = "Node", required = true, description = "Node to delete")
    private UISelectOne<String> node;

    private transient List<NodeDto> nodes;

    @Override
    public boolean isEnabled(UIContext context) {
        boolean enabled = super.isEnabled(context);
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
        Project project = getSelectedProject(context);
        String currentFile = getSelectedFile(context);

        String selected = configureXml(project, xml, currentFile);
        nodes = configureXmlNodes(context, project, selected, xml, node);
        builder.add(xml).add(node);
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        Project project = getSelectedProject(context);

        NodeDto nodeValue = null;
        int selectedIdx = node.getSelectedIndex();
        if (selectedIdx != -1) {
            nodeValue = nodes.get(selectedIdx);
        }
        if (nodeValue == null) {
            return Results.fail("No node to delete!");
        }
        String key = nodeValue.getKey();
        if (Strings.isNullOrBlank(key)) {
            return Results.fail("Selected node does not have a key so cannot delete it!");
        }
        String xmlResourceName = xml.getValue();

        FileResource file = getXmlResourceFile(project, xmlResourceName);
        if (file == null || !file.exists()) {
            return Results.fail("Cannot find XML file " + xmlResourceName);
        }

        List<ContextDto> camelContexts = CamelXmlHelper.loadCamelContext(getCamelCatalog(), context.getUIContext(), project, xmlResourceName);
        if (camelContexts == null) {
            return Results.fail("No file found for: " + xmlResourceName);
        }

        Document root = XmlLineNumberParser.parseXml(file.getResourceInputStream());
        if (root != null) {
            Node selectedNode = CamelXmlHelper.findCamelNodeInDocument(root, key);
            if (selectedNode != null) {

                // we need to add after the parent node, so use line number information from the parent
                String lineNumber = (String) selectedNode.getUserData(XmlLineNumberParser.LINE_NUMBER);
                String lineNumberEnd = (String) selectedNode.getUserData(XmlLineNumberParser.LINE_NUMBER_END);

                if (lineNumber != null && lineNumberEnd != null) {

                    // read all the lines
                    List<String> lines = LineNumberHelper.readLines(file.getResourceInputStream());

                    // the list is 0-based, and line number is 1-based
                    int idx = Integer.valueOf(lineNumber) - 1;
                    int idx2 = Integer.valueOf(lineNumberEnd) - 1;
                    int delta = (idx2 - idx) + 1;

                    // remove the lines
                    while (delta > 0) {
                        delta--;
                        lines.remove(idx);
                    }

                    // and save the file back
                    String content = LineNumberHelper.linesToString(lines);
                    file.setContents(content);
                    return Results.success("Removed node");
                }
            }
            return Results.fail("Cannot find Camel node in XML file: " + nodeValue);
        } else {
            return Results.fail("Cannot load Camel XML file: " + file.getName());
        }
    }

}

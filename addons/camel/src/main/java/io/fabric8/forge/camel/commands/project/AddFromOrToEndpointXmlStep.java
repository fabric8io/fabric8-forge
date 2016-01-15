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

import io.fabric8.forge.addon.utils.XmlLineNumberParser;
import io.fabric8.forge.camel.commands.project.dto.NodeDto;
import io.fabric8.forge.camel.commands.project.helper.CamelXmlHelper;
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

import java.util.List;

/**
 * A wizard step to add a from or to endpoint to a node
 */
public class AddFromOrToEndpointXmlStep extends ConfigureEndpointPropertiesStep {
    private final String id;
    private final String description;
    private final NodeDto parentNode;
    private final boolean isFrom;

    public AddFromOrToEndpointXmlStep(ProjectFactory projectFactory, DependencyInstaller dependencyInstaller, CamelCatalog camelCatalog, String componentName, String group, List<InputComponent> allInputs, List<InputComponent> inputs, boolean last, int index, int total, String id, String description, NodeDto parentNode, boolean isFrom) {
        super(projectFactory, dependencyInstaller, camelCatalog, componentName, group, allInputs, inputs, last, index, total);
        this.id = id;
        this.description = description;
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
    protected Result addOrEditEndpointXml(FileResource file, String uri, String endpointUrl, String endpointInstanceName, String xml, String lineNumber) throws Exception {

        String key = parentNode.getKey();
        if (Strings.isNullOrBlank(key)) {
            return Results.fail("Parent node has no key! " + parentNode + " in file " + file.getName());
        }
        Document root = XmlLineNumberParser.parseXml(file.getResourceInputStream());
        if (root != null) {
            Node selectedNode = CamelXmlHelper.findCamelNodeInDocument(root, key);
            if (selectedNode != null) {
                String indent0 = "\n";
                String[] paths = key.split("/");
                for (String path : paths) {
                    indent0 += "  ";
                }
                String indent1 = indent0 + "  ";
                String indent2 = indent1 + "  ";
                appendText(selectedNode, indent1);
                Element endpointElement = DomHelper.addChildElement(selectedNode, isFrom ? "from" : "to", Strings.isNotBlank(description) ? indent2 : "");
                if (Strings.isNotBlank(id)) {
                    endpointElement.setAttribute("id", id);
                }
                endpointElement.setAttribute("uri", uri);
                if (Strings.isNotBlank(description)) {
                    DomHelper.addChildElement(endpointElement, "description", description);
                    appendText(endpointElement, indent1);
                }
                appendText(selectedNode, indent0);

                String content = DomHelper.toXml(root);
                file.setContents(content);
                return Results.success("Node deleted");
            }
            return Results.fail("Cannot find Camel node in XML file " + key);
        } else {
            return Results.fail("Could not load camel XML " + file.getName());
        }
    }

}

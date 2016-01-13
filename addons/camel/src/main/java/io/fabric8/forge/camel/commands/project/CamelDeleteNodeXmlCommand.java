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

import io.fabric8.forge.addon.utils.XmlLineNumberParser;
import io.fabric8.forge.camel.commands.project.completer.XmlFileCompleter;
import io.fabric8.forge.camel.commands.project.converter.NodeDtoConverter;
import io.fabric8.forge.camel.commands.project.converter.NodeDtoLabelConverter;
import io.fabric8.forge.camel.commands.project.dto.ContextDto;
import io.fabric8.forge.camel.commands.project.dto.NodeDto;
import io.fabric8.forge.camel.commands.project.dto.NodeDtos;
import io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper;
import io.fabric8.forge.camel.commands.project.helper.CamelXmlHelper;
import io.fabric8.utils.DomHelper;
import io.fabric8.utils.Strings;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
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

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import static io.fabric8.forge.camel.commands.project.helper.CollectionHelper.first;

public class CamelDeleteNodeXmlCommand extends AbstractCamelProjectCommand {
    @Inject
    @WithAttributes(label = "XML File", required = true, description = "The XML file to use (either Spring or Blueprint)")
    private UISelectOne<String> xml;

    @Inject
    @WithAttributes(label = "Node", required = true, description = "Node to delete")
    private UISelectOne<NodeDto> node;

    @Inject
    private DependencyInstaller dependencyInstaller;

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
        final UIContext context = builder.getUIContext();
        Map<Object, Object> attributeMap = context.getAttributeMap();
        attributeMap.remove("navigationResult");

        final Project project = getSelectedProject(context);

        XmlFileCompleter xmlFileCompleter = createXmlFileCompleter(project);
        Set<String> files = xmlFileCompleter.getFiles();

        // use value choices instead of completer as that works better in web console
        final String first = first(files);
        xml.setValueChoices(files);
        if (files.size() == 1) {
            // lets default the value if there's only one choice
            xml.setDefaultValue(first);
        }

        node.setValueConverter(new NodeDtoConverter(project, context, xml));
        node.setItemLabelConverter(new NodeDtoLabelConverter());
        node.setValueChoices(new Callable<Iterable<NodeDto>>() {
            @Override
            public Iterable<NodeDto> call() throws Exception {
                String xmlResourceName = xml.getValue();
                if (Strings.isNullOrBlank(xmlResourceName)) {
                    xmlResourceName = first;
                }
                List<ContextDto> camelContexts = CamelXmlHelper.loadCamelContext(context, project, xmlResourceName);
                return NodeDtos.toNodeList(camelContexts);
            }
        });
        builder.add(xml).add(node);
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        Project project = getSelectedProject(context);
        NodeDto nodeValue = node.getValue();
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

        List<ContextDto> camelContexts = CamelXmlHelper.loadCamelContext(context.getUIContext(), project, xmlResourceName);
        if (camelContexts == null) {
            return Results.fail("No file found for: " + xmlResourceName);
        }

        Document root = XmlLineNumberParser.parseXml(file.getResourceInputStream());
        if (root != null) {
            Node selectedNode = CamelXmlHelper.findCamelNodeInDocument(root, key);
            if (selectedNode != null) {
                DomHelper.detach(selectedNode);
                String content = DomHelper.toXml(root);
                file.setContents(content);
                return Results.success("Node deleted");
            }
            return Results.fail("Cannot find Camel node in XML file " + nodeValue);
        } else {
            return Results.fail("Could not load camel XML " + xmlResourceName);
        }
    }

}

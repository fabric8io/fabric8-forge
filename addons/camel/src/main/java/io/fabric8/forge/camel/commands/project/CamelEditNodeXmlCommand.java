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
import io.fabric8.forge.camel.commands.project.dto.NodeDto;
import io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper;
import io.fabric8.forge.camel.commands.project.helper.CamelXmlHelper;
import io.fabric8.forge.camel.commands.project.model.EndpointOptionByGroup;
import io.fabric8.utils.Strings;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.resource.FileResource;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.InputComponentFactory;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.input.ValueChangeListener;
import org.jboss.forge.addon.ui.input.events.ValueChangeEvent;
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

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper.createUIInputsForCamelComponent;

/**
 * Edits a from/to endpoint
 */
public class CamelEditNodeXmlCommand extends AbstractCamelProjectCommand implements UIWizard {
    @Inject
    @WithAttributes(label = "XML File", required = true, description = "The XML file to use (either Spring or Blueprint)")
    private UISelectOne<String> xml;

    @Inject
    @WithAttributes(label = "Node", required = true, description = "Node to edit")
    private UISelectOne<NodeDto> node;

    @Inject
    @WithAttributes(label = "Name", required = false, description = "The name of the step")
    private UIInput<String> id;

    @Inject
    @WithAttributes(label = "Description", required = false, description = "The description of the step")
    private UIInput<String> description;

    @Inject
    private InputComponentFactory componentFactory;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelEditNodeXmlCommand.class).name(
                "Camel: Edit Node XML").category(Categories.create(CATEGORY))
                .description("Edits a node in a Camel XML file");
    }

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
        UIContext context = builder.getUIContext();
        Map<Object, Object> attributeMap = context.getAttributeMap();
        attributeMap.remove("navigationResult");

        Project project = getSelectedProject(context);

        String first = configureXml(project, xml);
        node.addValueChangeListener(new ValueChangeListener() {
            @Override
            public void valueChanged(ValueChangeEvent event) {
                NodeDto value = node.getValue();
                if (value != null) {
                    id.setValue(value.getId());

                    // TODO did the user actually add a description?
                    description.setValue(value.getDescription());
                }
            }
        });
        configureNode(context, project, first, xml, node);

        builder.add(xml).add(node);
        builder.add(xml).add(id).add(description);

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

        NodeDto editNode = node.getValue();
        String key = editNode.getKey();
        String pattern = editNode.getPattern();

        // must be same component name to allow reusing existing navigation result
        String previous = getNodeKey(attributeMap.get("node"));
        if (previous != null && previous.equals(previous)) {
            NavigationResult navigationResult = (NavigationResult) attributeMap.get("navigationResult");
            if (navigationResult != null) {
                return navigationResult;
            }
        }

        attributeMap.put("node", key);

        if (pattern.equals("from") || pattern.equals("to")) {
            // producer vs consumer only if selected
            boolean consumerOnly = false;
            boolean producerOnly = false;
            boolean isFrom = false;

            if (pattern.equals("from")) {
                consumerOnly = true;
                isFrom = true;
            } else {
                producerOnly = true;
            }

            Element selectedElement = getSelectedElementNode(project, xmlResourceName, key);
            if (selectedElement == null) {
                throw new IllegalArgumentException("Could not find xml for node " + editNode);
            }
            String uri = selectedElement.getAttribute("uri");
            if (Strings.isNullOrBlank(uri)) {
                throw new IllegalArgumentException("No uri property for node " + editNode);
            }
            String[] split = uri.split(":");
            String camelComponentName = split[0];

            System.out.println("Using endpoint " + uri + " and componentName: " +camelComponentName);

            attributeMap.put("componentName", camelComponentName);
            attributeMap.put("endpointUri", uri);

            UIContext ui = context.getUIContext();
            List<EndpointOptionByGroup> groups = createUIInputsForCamelComponent(camelComponentName, uri, CamelAddEndpointDefinitionXmlCommand.MAX_OPTIONS, consumerOnly, producerOnly,
                    getCamelCatalog(), componentFactory, converterFactory, ui);

            // need all inputs in a list as well
            List<InputComponent> allInputs = new ArrayList<>();
            for (EndpointOptionByGroup group : groups) {
                allInputs.addAll(group.getInputs());
            }


            NavigationResultBuilder builder = Results.navigationBuilder();
            int pages = groups.size();
            for (int i = 0; i < pages; i++) {
                boolean last = i == pages - 1;
                EndpointOptionByGroup current = groups.get(i);
                EditFromOrToEndpointXmlStep step = new EditFromOrToEndpointXmlStep(projectFactory, dependencyInstaller,
                        getCamelCatalog(),
                        camelComponentName, current.getGroup(), allInputs, current.getInputs(), last, i, pages,
                        id.getValue(), description.getValue(), editNode, isFrom);
                builder.add(step);
            }
            NavigationResult navigationResult = builder.build();
            attributeMap.put("navigationResult", navigationResult);
            return navigationResult;
        } else {
            // TODO edit other patterns!
            return null;
        }
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

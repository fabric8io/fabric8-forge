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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import io.fabric8.forge.camel.commands.project.dto.ComponentDto;
import io.fabric8.forge.camel.commands.project.dto.NodeDto;
import io.fabric8.forge.camel.commands.project.helper.PoorMansLogger;
import io.fabric8.forge.camel.commands.project.model.InputOptionByGroup;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
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

import static io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper.createUIInputsForCamelEndpoint;

/**
 * Adds a from/to endpoint into chosen node in the route
 */
public class CamelAddEndpointXmlCommand extends AbstractCamelProjectCommand implements UIWizard {

    private static final PoorMansLogger LOG = new PoorMansLogger(false);

    @Inject
    @WithAttributes(label = "XML File", required = true, description = "The XML file to use (either Spring or Blueprint)")
    private UISelectOne<String> xml;

    @Inject
    @WithAttributes(label = "Node", required = true, description = "Parent node (where to add the endpoint)")
    private UISelectOne<String> node;
    private transient List<NodeDto> nodes;

    @Inject
    @WithAttributes(label = "Name", required = true, description = "Name of component to use for the endpoint")
    private UISelectOne<ComponentDto> componentName;

    @Inject
    private InputComponentFactory componentFactory;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelAddEndpointXmlCommand.class).name(
                "Camel: Add Endpoint XML").category(Categories.create(CATEGORY))
                .description("Adds an Endpoint to an existing route in an XML file");
    }

    @Override
    public boolean isEnabled(UIContext context) {
        boolean enabled = super.isEnabled(context);
        if (enabled) {
            // we should only be enabled in non gui
            boolean gui = isRunningInGui(context);
            enabled = !gui;
        }
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
        String currentFile = getSelectedFile(context);

        configureComponentName(project, componentName, false, false);

        String selected = configureXml(project, xml, currentFile);
        nodes = configureXmlNodes(context, project, selected, xml, node);

        builder.add(xml).add(node).add(componentName);
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();

        // always refresh these as the end user may have edited the instance name
        attributeMap.put("xml", xml.getValue());
        attributeMap.put("mode", "add");
        attributeMap.put("kind", "xml");

        ComponentDto component = componentName.getValue();
        String camelComponentName = component.getScheme();

        // must be same component name to allow reusing existing navigation result
        String previous = (String) attributeMap.get("componentName");
        if (previous != null && previous.equals(camelComponentName)) {
            NavigationResult navigationResult = (NavigationResult) attributeMap.get("navigationResult");
            if (navigationResult != null) {
                return navigationResult;
            }
        }

        attributeMap.put("componentName", camelComponentName);

        NodeDto parentNode = null;
        int selectedIdx = node.getSelectedIndex();
        if (selectedIdx != -1) {
            parentNode = nodes.get(selectedIdx);
        }
        LOG.info("Parent node " + parentNode);

        boolean isFrom = parentNode == null || "route".equals(parentNode.getPattern()) || "routes".equals(parentNode.getPattern());
        // if there is already a route that has a from then its not a from
        if (isFrom && parentNode != null) {
            for (NodeDto child : parentNode.getChildren()) {
                if ("from".equals(child.getPattern())) {
                    isFrom = false;
                }
            }
        }

        // if the parent node is route, then lets add to the end of the route, eg its last child
        if (parentNode != null && "route".equals(parentNode.getPattern()) && !parentNode.getChildren().isEmpty()) {
            int size = parentNode.getChildren().size();
            parentNode = parentNode.getChildren().get(size - 1);
            LOG.info("Parent node changed to " + parentNode);
        }

        // its either from or to
        boolean consumerOnly = isFrom;
        boolean producerOnly = !isFrom;

        LOG.info("Consumer only " + consumerOnly + ", producer only " + producerOnly);
        LOG.info("Parent node " + parentNode);

        // we need to figure out how many options there is so we can as many steps we need
        UIContext ui = context.getUIContext();
        List<InputOptionByGroup> groups = createUIInputsForCamelEndpoint(camelComponentName, null, MAX_OPTIONS, consumerOnly, producerOnly,
                getCamelCatalog(), componentFactory, converterFactory, ui);

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
            AddFromOrToEndpointXmlStep step = new AddFromOrToEndpointXmlStep(projectFactory, dependencyInstaller,
                    getCamelCatalog(),
                    camelComponentName, current.getGroup(), allInputs, current.getInputs(), last, i, pages,
                    parentNode, isFrom);
            builder.add(step);
        }

        NavigationResult navigationResult = builder.build();
        attributeMap.put("navigationResult", navigationResult);
        return navigationResult;
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        return Results.success();
    }

}

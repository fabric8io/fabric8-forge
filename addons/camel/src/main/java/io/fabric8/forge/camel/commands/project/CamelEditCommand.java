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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

import io.fabric8.forge.camel.commands.project.completer.RouteBuilderEndpointsCompleter;
import io.fabric8.forge.camel.commands.project.completer.XmlEndpointsCompleter;
import io.fabric8.forge.camel.commands.project.model.CamelEndpointDetails;
import io.fabric8.forge.camel.commands.project.model.InputOptionByGroup;
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

import static io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper.createUIInputsForCamelComponent;

public class CamelEditCommand extends AbstractCamelProjectCommand implements UIWizard {

    private static final int MAX_OPTIONS = 20;

    @Inject
    @WithAttributes(label = "Endpoints", required = true, description = "The endpoints from the project")
    private UISelectOne<String> endpoints;

    @Inject
    private InputComponentFactory componentFactory;

    @Inject
    private DependencyInstaller dependencyInstaller;

    private RouteBuilderEndpointsCompleter javaCompleter;
    private XmlEndpointsCompleter xmlCompleter;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelEditCommand.class).name(
                "Camel: Edit").category(Categories.create(CATEGORY))
                .description("Edit Camel endpoint or EIP from the current cursor position");
    }

    @Override
    public boolean isEnabled(UIContext context) {
        boolean answer = super.isEnabled(context);
        if (answer) {
            // we are only enabled if there is a file open in the editor
            final String currentFile = getSelectedFile(context);
            answer = currentFile != null;
        }
        return answer;
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        Map<Object, Object> attributeMap = builder.getUIContext().getAttributeMap();
        attributeMap.remove("navigationResult");

        final String currentFile = getSelectedFile(builder.getUIContext());
        final int cursorLineNumber = getCurrentCursorPosition(builder.getUIContext());

        // whether its an xml file or not
        boolean xmlFile = currentFile.endsWith(".xml");

        if (!xmlFile) {
            // find all endpoints
            javaCompleter = createRouteBuilderEndpointsCompleter(builder.getUIContext(), currentFile::equals);

            boolean found = false;
            if (cursorLineNumber != -1) {
                for (CamelEndpointDetails detail : javaCompleter.getEndpoints()) {
                    if (detail.getLineNumber() != null && Integer.valueOf(detail.getLineNumber()) == cursorLineNumber) {
                        endpoints.setValue(detail.getEndpointUri());
                        endpoints.setRequired(false);
                        found = true;
                        break;
                    }
                }
            }
            if (!found && !javaCompleter.getEndpoints().isEmpty()) {

                // must add dummy <select> in the dropdown as otherwise there is problems with auto selecting
                // the first element where its a different between its auto selected vs end user clicked and selected
                // it, which also affects all this next() callback issue from forge
                List<String> uris = javaCompleter.getEndpointUris();
                uris.add(0, "<select>");
                endpoints.setValueChoices(uris);

                // show the UI where you can chose the endpoint to select
                builder.add(endpoints);
            }
        } else {
            // find all endpoints
            xmlCompleter = createXmlEndpointsCompleter(builder.getUIContext(), currentFile::equals);

            boolean found = false;
            if (cursorLineNumber != -1) {
                for (CamelEndpointDetails detail : xmlCompleter.getEndpoints()) {
                    if (detail.getLineNumber() != null && Integer.valueOf(detail.getLineNumber()) == cursorLineNumber) {
                        endpoints.setValue(detail.getEndpointUri());
                        endpoints.setRequired(false);
                        found = true;
                        break;
                    }
                }
            }
            if (!found && !xmlCompleter.getEndpoints().isEmpty()) {

                // must add dummy <select> in the dropdown as otherwise there is problems with auto selecting
                // the first element where its a different between its auto selected vs end user clicked and selected
                // it, which also affects all this next() callback issue from forge
                List<String> uris = xmlCompleter.getEndpointUris();
                uris.add(0, "<select>");
                endpoints.setValueChoices(uris);

                // show the UI where you can chose the endpoint to select
                builder.add(endpoints);
            }
        }
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();

        String selectedUri = endpoints.getValue();
        if ("<select>".equals(selectedUri)) {
            // no choice yet
            attributeMap.remove("navigationResult");
            return null;
        }

        // must be same component name to allow reusing existing navigation result
        String previous = (String) attributeMap.get("endpointUri");
        if (previous != null && previous.equals(endpoints.getValue())) {
            NavigationResult navigationResult = (NavigationResult) attributeMap.get("navigationResult");
            if (navigationResult != null) {
                return navigationResult;
            }
        }

        CamelEndpointDetails detail = null;
        if (javaCompleter != null) {
            detail = javaCompleter.getEndpointDetail(selectedUri);
        } else if (xmlCompleter != null) {
            detail = xmlCompleter.getEndpointDetail(selectedUri);
        }
        if (detail == null) {
            return null;
        }

        attributeMap.put("componentName", detail.getEndpointComponentName());
        attributeMap.put("instanceName", detail.getEndpointInstance());
        attributeMap.put("endpointUri", detail.getEndpointUri());
        attributeMap.put("lineNumber", detail.getLineNumber());
        attributeMap.put("lineNumberEnd", detail.getLineNumberEnd());
        attributeMap.put("mode", "edit");

        if (javaCompleter != null) {
            attributeMap.put("routeBuilder", detail.getFileName());
            attributeMap.put("kind", "java");
        } else {
            attributeMap.put("xml", detail.getFileName());
            attributeMap.put("kind", "xml");
        }

        // we need to figure out how many options there is so we can as many steps we need
        String camelComponentName = detail.getEndpointComponentName();
        String uri = detail.getEndpointUri();

        String json = getCamelCatalog().componentJSonSchema(camelComponentName);
        if (json == null) {
            throw new IllegalArgumentException("Could not find catalog entry for component name: " + camelComponentName);
        }

        boolean consumerOnly = detail.isConsumerOnly();
        boolean producerOnly = detail.isProducerOnly();

        UIContext ui = context.getUIContext();
        List<InputOptionByGroup> groups = createUIInputsForCamelComponent(camelComponentName, uri, MAX_OPTIONS, consumerOnly, producerOnly,
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
            ConfigureEndpointPropertiesStep step = new ConfigureEndpointPropertiesStep(projectFactory, dependencyInstaller, getCamelCatalog(),
                    camelComponentName, current.getGroup(), allInputs, current.getInputs(), last, i, pages);
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

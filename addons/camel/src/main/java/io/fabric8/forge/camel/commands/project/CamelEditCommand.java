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

import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

import io.fabric8.forge.camel.commands.project.completer.RouteBuilderEndpointsCompleter;
import io.fabric8.forge.camel.commands.project.completer.XmlEndpointsCompleter;
import io.fabric8.forge.camel.commands.project.model.CamelEndpointDetails;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.context.UIRegion;
import org.jboss.forge.addon.ui.input.InputComponentFactory;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.wizard.UIWizard;

public class CamelEditCommand extends AbstractCamelProjectCommand implements UIWizard {

    private static final int MAX_OPTIONS = 20;

    @Inject
    @WithAttributes(label = "Debug", required = true)
    private UIInput<String> debug;

    @Inject
    private InputComponentFactory componentFactory;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelEditCommand.class).name(
                "Camel: Edit").category(Categories.create(CATEGORY))
                .description("Edit Camel endpoint or EIP from the current cursor position");
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        Map<Object, Object> attributeMap = builder.getUIContext().getAttributeMap();
        attributeMap.remove("navigationResult");

        final String currentFile = getSelectedFile(builder.getUIContext());
        if (currentFile == null) {
            attributeMap.remove("endpointUri");
            debug.setValue("No current file");
        } else {
            Optional<UIRegion<Object>> region = builder.getUIContext().getSelection().getRegion();
            if (region.isPresent()) {
                int lineNumber = region.get().getStartLine();
                int lineNumberEnd = region.get().getEndLine();
                int pos = region.get().getStartPosition();
                int endPos = region.get().getEndPosition();
                String text = region.get().getText().orElse("");
                String res = region.get().getResource().toString();
                String msg = String.format("line %s(%s)-%s(%s): %s | %s", lineNumber, pos, lineNumberEnd, endPos, text, res);

                // find all the endpoints in the current file
                // and find the endpoints that are on the same line number as the cursor
                boolean found = false;
                RouteBuilderEndpointsCompleter completer = createRouteBuilderEndpointsCompleter(builder.getUIContext(), currentFile::equals);
                for (CamelEndpointDetails detail : completer.getEndpoints()) {
                    if (detail.getLineNumber() != null && Integer.valueOf(detail.getLineNumber()) == lineNumber) {
                        msg = detail.getEndpointUri();
                        found = true;
                        break;
                    }
                }

                // find all the endpoints in the current file
                // and find the endpoints that are on the same line number as the cursor
                if (!found) {
                    XmlEndpointsCompleter completer2 = createXmlEndpointsCompleter(builder.getUIContext(), currentFile::equals);
                    for (CamelEndpointDetails detail : completer2.getEndpoints()) {
                        if (detail.getLineNumber() != null && Integer.valueOf(detail.getLineNumber()) == lineNumber) {
                            msg = detail.getEndpointUri();
                            found = true;
                            break;
                        }
                    }
                }

                if (found) {
                    debug.setValue(currentFile + "@" + msg);
                } else {
                    debug.setValue(currentFile + "<not found>@" + msg);
                }
            } else {
                debug.setValue(currentFile);
            }
        }

        builder.add(debug);
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        return null;
        /*
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();

        NavigationResult navigationResult = (NavigationResult) attributeMap.get("navigationResult");
        if (navigationResult != null) {
            return navigationResult;
        }

        String endpointUri = (String) attributeMap.get("endpointUri");
        if (endpointUri == null) {
            return null;
        }

        attributeMap.put("componentName", detail.getEndpointComponentName());
        attributeMap.put("instanceName", detail.getEndpointInstance());
        attributeMap.put("endpointUri", detail.getEndpointUri());
        attributeMap.put("lineNumber", detail.getLineNumber());
        attributeMap.put("lineNumberEnd", detail.getLineNumberEnd());
        attributeMap.put("routeBuilder", detail.getFileName());
        attributeMap.put("mode", "edit");
        attributeMap.put("kind", "java");

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
        return navigationResult;*/
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        return Results.success();
    }

}

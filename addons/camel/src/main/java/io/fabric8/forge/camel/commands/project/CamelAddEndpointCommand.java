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
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;

import io.fabric8.forge.addon.utils.LineNumberHelper;
import io.fabric8.forge.camel.commands.project.dto.ComponentDto;
import io.fabric8.forge.camel.commands.project.helper.PoorMansLogger;
import io.fabric8.forge.camel.commands.project.model.InputOptionByGroup;
import org.jboss.forge.addon.parser.java.facets.JavaSourceFacet;
import org.jboss.forge.addon.parser.java.resources.JavaResource;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.projects.facets.ResourcesFacet;
import org.jboss.forge.addon.projects.facets.WebResourcesFacet;
import org.jboss.forge.addon.resource.FileResource;
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

public class CamelAddEndpointCommand extends AbstractCamelProjectCommand implements UIWizard {

    private static final int MAX_OPTIONS = 20;

    private static final PoorMansLogger LOG = new PoorMansLogger(false);

    @Inject
    @WithAttributes(label = "Name", required = true, description = "Name of component to use for the endpoint")
    private UISelectOne<ComponentDto> componentName;

    @Inject
    private InputComponentFactory componentFactory;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelAddEndpointCommand.class).name(
                "Camel: Add Endpoint").category(Categories.create(CATEGORY))
                .description("Add Camel endpoint to the current file");
    }

    @Override
    public boolean isEnabled(UIContext context) {
        boolean answer = super.isEnabled(context);
        if (answer) {
            // we are only enabled if there is a file open in the editor and we have a cursor position
            int pos = getCurrentCursorPosition(context);
            answer = pos > -1;
        }
        return answer;
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        Map<Object, Object> attributeMap = builder.getUIContext().getAttributeMap();
        attributeMap.remove("navigationResult");

        Project project = getSelectedProject(builder.getUIContext());
        String selectedFile = getSelectedFile(builder.getUIContext());
        final String currentFile = asRelativeFile(builder.getUIContext(), selectedFile);
        attributeMap.put("currentFile", currentFile);

        boolean xmlFile = currentFile != null && currentFile.endsWith(".xml");

        // determine if the current cursor position is in a route where we should be either consumer or producer only
        AtomicBoolean consumerOnly = new AtomicBoolean();
        AtomicBoolean producerOnly = new AtomicBoolean();
        determineConsumerAndProducerOnly(consumerOnly, producerOnly, builder.getUIContext(), xmlFile, currentFile);
        attributeMap.put("consumerOnly", Boolean.toString(consumerOnly.get()));
        attributeMap.put("producerOnly", Boolean.toString(producerOnly.get()));

        // filter the list of components based on consumer and producer only
        configureComponentName(project, componentName, consumerOnly.get(), producerOnly.get());

        builder.add(componentName);
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();

        ComponentDto component = componentName.getValue();
        String camelComponentName = component.getScheme();

        // must be same component name and endpoint type to allow reusing existing navigation result
        String previous = (String) attributeMap.get("componentName");
        if (previous != null && previous.equals(camelComponentName)) {
            NavigationResult navigationResult = (NavigationResult) attributeMap.get("navigationResult");
            if (navigationResult != null) {
                return navigationResult;
            }
        }

        attributeMap.put("componentName", camelComponentName);

        String currentFile = (String) attributeMap.get("currentFile");
        boolean xmlFile = currentFile != null && currentFile.endsWith(".xml");
        boolean javaFile = currentFile != null && currentFile.endsWith(".java");

        attributeMap.put("mode", "add");
        if (xmlFile) {
            attributeMap.put("xml", currentFile);
            attributeMap.put("kind", "xml");
        } else if (javaFile) {
            attributeMap.put("routeBuilder", currentFile);
            attributeMap.put("kind", "java");
        } else {
            attributeMap.put("currentFile", currentFile);
            attributeMap.put("kind", "other");
        }
        int pos = getCurrentCursorPosition(context.getUIContext());
        attributeMap.put("cursorPosition", pos);

        // producer vs consumer only
        boolean consumerOnly = "true".equals(attributeMap.get("consumerOnly"));
        boolean producerOnly = "true".equals(attributeMap.get("producerOnly"));

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
        return null;
    }

    private void determineConsumerAndProducerOnly(AtomicBoolean consumerOnly, AtomicBoolean producerOnly,
                                                  UIContext context, boolean xmlFile, String currentFile) {

        Project project = getSelectedProject(context);
        final int cursorLineNumber = getCurrentCursorLine(context);
        if (cursorLineNumber == -1) {
            // cannot find the cursor
            return;
        }

        // we can be both kind lets try to see if we can figure out if the current position is in a Camel route
        // where we use EIPs so we can know if its a from/pollEnrich = consumer, and if not = producer)

        if (xmlFile) {
            ResourcesFacet facet = project.getFacet(ResourcesFacet.class);
            WebResourcesFacet webResourcesFacet = null;
            if (project.hasFacet(WebResourcesFacet.class)) {
                webResourcesFacet = project.getFacet(WebResourcesFacet.class);
            }
            FileResource file = facet != null ? facet.getResource(currentFile) : null;
            if (file == null || !file.exists()) {
                file = webResourcesFacet != null ? webResourcesFacet.getWebResource(currentFile) : null;
            }
            try {
                if (file != null && file.exists() && cursorLineNumber > 0) {
                    // read all the lines
                    List<String> lines = LineNumberHelper.readLines(file.getResourceInputStream());
                    // the list is 0-based, and line number is 1-based
                    String line = lines.get(cursorLineNumber - 1);

                    LOG.info("Line " + line);

                    if (line != null) {
                        if (line.contains("<from") || line.contains("<pollEnrich")) {
                            // only from and poll enrich is consumer based
                            consumerOnly.set(true);
                        } else if (!line.contains("<endpoint")) {
                            // assume producer unless its a generic endpoint
                            producerOnly.set(true);
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        } else {
            // java code
            JavaSourceFacet facet = project.getFacet(JavaSourceFacet.class);
            JavaResource file = facet.getJavaResource(currentFile);
            try {
                if (file != null && file.exists() && cursorLineNumber > 0) {
                    // read all the lines
                    List<String> lines = LineNumberHelper.readLines(file.getResourceInputStream());
                    // the list is 0-based, and line number is 1-based
                    String line = lines.get(cursorLineNumber - 1);

                    LOG.info("Line " + line);

                    if (line != null) {
                        if (line.contains("from(") || line.contains("pollEnrich(")) {
                            // only from and poll enrich is consumer based
                            consumerOnly.set(true);
                        } else if (!line.contains("endpoint(")) {
                            // assume producer unless its a generic endpoint
                            producerOnly.set(true);
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }

    }

}

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

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import javax.inject.Inject;

import io.fabric8.forge.addon.utils.CamelProjectHelper;
import io.fabric8.forge.addon.utils.LineNumberHelper;
import io.fabric8.forge.addon.utils.XmlLineNumberParser;
import io.fabric8.forge.camel.commands.project.completer.CurrentLineCompleter;
import io.fabric8.forge.camel.commands.project.completer.RouteBuilderCompleter;
import io.fabric8.forge.camel.commands.project.completer.RouteBuilderEndpointsCompleter;
import io.fabric8.forge.camel.commands.project.completer.XmlEndpointsCompleter;
import io.fabric8.forge.camel.commands.project.completer.XmlFileCompleter;
import io.fabric8.forge.camel.commands.project.converter.NodeDtoConverter;
import io.fabric8.forge.camel.commands.project.converter.NodeDtoLabelConverter;
import io.fabric8.forge.camel.commands.project.dto.ComponentDto;
import io.fabric8.forge.camel.commands.project.dto.ContextDto;
import io.fabric8.forge.camel.commands.project.dto.NodeDto;
import io.fabric8.forge.camel.commands.project.dto.NodeDtos;
import io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper;
import io.fabric8.forge.camel.commands.project.helper.CamelXmlHelper;
import io.fabric8.utils.Strings;
import org.apache.camel.catalog.CamelCatalog;
import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.convert.ConverterFactory;
import org.jboss.forge.addon.dependencies.Coordinate;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.dependencies.builder.CoordinateBuilder;
import org.jboss.forge.addon.parser.java.facets.JavaSourceFacet;
import org.jboss.forge.addon.parser.java.resources.JavaResource;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.projects.Projects;
import org.jboss.forge.addon.projects.facets.ResourcesFacet;
import org.jboss.forge.addon.projects.facets.WebResourcesFacet;
import org.jboss.forge.addon.projects.ui.AbstractProjectCommand;
import org.jboss.forge.addon.resource.FileResource;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UIRegion;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.input.ValueChangeListener;
import org.jboss.forge.addon.ui.input.events.ValueChangeEvent;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static io.fabric8.forge.camel.commands.project.helper.CollectionHelper.first;

public abstract class AbstractCamelProjectCommand extends AbstractProjectCommand {

    public static String CATEGORY = "Camel";
    public static int MAX_OPTIONS = 20;

    @Inject
    protected ProjectFactory projectFactory;

    @Inject
    protected ConverterFactory converterFactory;

    @Inject
    protected CamelCatalog camelCatalog;

    protected void configureXmlNode(final UIContext context, final Project project, final String selected, final UISelectOne<String> xml, final UISelectOne<NodeDto> node) {
        node.setValueConverter(new NodeDtoConverter(camelCatalog, project, context, xml));
        node.setItemLabelConverter(new NodeDtoLabelConverter());
        node.setValueChoices(new Callable<Iterable<NodeDto>>() {
            @Override
            public Iterable<NodeDto> call() throws Exception {
                String xmlResourceName = xml.getValue();
                if (Strings.isNullOrBlank(xmlResourceName)) {
                    xmlResourceName = selected;
                }
                List<ContextDto> camelContexts = CamelXmlHelper.loadCamelContext(camelCatalog, context, project, xmlResourceName);
                List<NodeDto> nodes = NodeDtos.toNodeList(camelContexts);
                // if there is one CamelContext then pre-select the first node (which is the route)
                if (camelContexts.size() == 1 && nodes.size() > 1) {
                    node.setDefaultValue(nodes.get(1));
                }
                return nodes;
            }

            ;
        });
    }

    @Override
    protected boolean isProjectRequired() {
        return true;
    }

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelDeleteNodeXmlCommand.class).name(
                "Camel: Delete Node XML").category(Categories.create(CATEGORY))
                .description("Deletes a node from a Camel XML file");
    }

    @Override
    public boolean isEnabled(UIContext context) {
        boolean enabled = super.isEnabled(context);
        if (!enabled) {
            return false;
        }
        if (requiresCamelSetup()) {
            // requires camel is already setup
            Project project = getSelectedProjectOrNull(context);
            if (project != null) {
                return findCamelCoreDependency(project) != null;
            }
        }
        return false;
    }

    protected Project getSelectedProjectOrNull(UIContext context) {
        return Projects.getSelectedProject(this.getProjectFactory(), context);
    }

    protected boolean isRunningInGui(UIContext context) {
        return context.getProvider().isGUI();
    }

    protected boolean requiresCamelSetup() {
        return true;
    }

    @Override
    protected ProjectFactory getProjectFactory() {
        return projectFactory;
    }

    protected ConverterFactory getConverterFactory() {
        return converterFactory;
    }

    protected CamelCatalog getCamelCatalog() {
        return camelCatalog;
    }

    protected PrintStream getOutput(UIExecutionContext context) {
        return context.getUIContext().getProvider().getOutput().out();
    }

    protected boolean isCamelProject(Project project) {
        // is there any camel dependency?
        return !findCamelArtifacts(project).isEmpty();
    }

    protected Dependency findCamelCoreDependency(Project project) {
        return CamelProjectHelper.findCamelCoreDependency(project);
    }

    protected Set<Dependency> findCamelArtifacts(Project project) {
        return CamelProjectHelper.findCamelArtifacts(project);
    }

    protected Coordinate createCoordinate(String groupId, String artifactId, String version) {
        CoordinateBuilder builder = CoordinateBuilder.create()
                .setGroupId(groupId)
                .setArtifactId(artifactId);
        if (version != null) {
            builder = builder.setVersion(version);
        }

        return builder;
    }

    protected Coordinate createCamelCoordinate(String artifactId, String version) {
        return createCoordinate("org.apache.camel", artifactId, version);
    }

    protected RouteBuilderCompleter createRouteBuilderCompleter(Project project) {
        JavaSourceFacet facet = project.getFacet(JavaSourceFacet.class);
        return new RouteBuilderCompleter(facet);
    }

    protected RouteBuilderEndpointsCompleter createRouteBuilderEndpointsCompleter(UIContext context, Function<String, Boolean> filter) {
        Project project = getSelectedProject(context);
        return createRouteBuilderEndpointsCompleter(project, filter);
    }

    protected RouteBuilderEndpointsCompleter createRouteBuilderEndpointsCompleter(Project project, Function<String, Boolean> filter) {
        JavaSourceFacet facet = project.getFacet(JavaSourceFacet.class);
        return new RouteBuilderEndpointsCompleter(facet, filter);
    }

    protected XmlEndpointsCompleter createXmlEndpointsCompleter(UIContext context, Function<String, Boolean> filter) {
        Project project = getSelectedProject(context);
        return createXmlEndpointsCompleter(project, filter);
    }

    protected XmlEndpointsCompleter createXmlEndpointsCompleter(Project project, Function<String, Boolean> filter) {
        final ResourcesFacet resourcesFacet = project.getFacet(ResourcesFacet.class);
        WebResourcesFacet webResourcesFacet = null;
        if (project.hasFacet(WebResourcesFacet.class)) {
            webResourcesFacet = project.getFacet(WebResourcesFacet.class);
        }
        return new XmlEndpointsCompleter(resourcesFacet, webResourcesFacet, filter);
    }

    protected XmlFileCompleter createXmlFileCompleter(Project project, Function<String, Boolean> filter) {
        final ResourcesFacet resourcesFacet = project.getFacet(ResourcesFacet.class);
        WebResourcesFacet webResourcesFacet = null;
        if (project.hasFacet(WebResourcesFacet.class)) {
            webResourcesFacet = project.getFacet(WebResourcesFacet.class);
        }
        return new XmlFileCompleter(resourcesFacet, webResourcesFacet, filter);
    }

    protected XmlFileCompleter createXmlFileCompleter(UIContext context, Function<String, Boolean> filter) {
        Project project = getSelectedProject(context);
        return createXmlFileCompleter(project, filter);
    }

    protected CurrentLineCompleter createCurrentLineCompleter(int lineNumber, String file, UIContext context) throws Exception {
        Project project = getSelectedProject(context);
        final ResourcesFacet resourcesFacet = project.getFacet(ResourcesFacet.class);
        WebResourcesFacet webResourcesFacet = null;
        if (project.hasFacet(WebResourcesFacet.class)) {
            webResourcesFacet = project.getFacet(WebResourcesFacet.class);
        }
        String relativeFile = asRelativeFile(context, file);
        return new CurrentLineCompleter(lineNumber, relativeFile, resourcesFacet, webResourcesFacet);
    }

    protected FileResource getXmlResourceFile(Project project, String xmlResourceName) {
        ResourcesFacet facet = project.getFacet(ResourcesFacet.class);
        WebResourcesFacet webResourcesFacet = null;
        if (project.hasFacet(WebResourcesFacet.class)) {
            webResourcesFacet = project.getFacet(WebResourcesFacet.class);
        }
        FileResource file = facet != null ? facet.getResource(xmlResourceName) : null;
        if (file == null || !file.exists()) {
            file = webResourcesFacet != null ? webResourcesFacet.getWebResource(xmlResourceName) : null;
        }
        return file;
    }

    protected String configureXml(Project project, UISelectOne<String> xml, String currentFile) {
        XmlFileCompleter xmlFileCompleter = createXmlFileCompleter(project, null);
        Set<String> files = xmlFileCompleter.getFiles();

        // use value choices instead of completer as that works better in web console
        final String first = first(files);
        String answer = first;

        xml.setValueChoices(files);
        if (files.size() == 1) {
            // lets default the value if there's only one choice
            xml.setDefaultValue(first);
        } else if (currentFile != null) {
            // lets default to the current file
            for (String name : files) {
                if (currentFile.endsWith(name)) {
                    xml.setDefaultValue(name);
                    answer = name;
                    break;
                }
            }
        }
        return answer;
    }

    protected String configureRouteBuilder(Project project, UISelectOne<String> routeBuilders, String currentFile) {
        RouteBuilderCompleter completer = createRouteBuilderCompleter(project);
        Set<String> builders = completer.getRouteBuilders();

        // use value choices instead of completer as that works better in web console
        final String first = first(builders);
        String answer = first;

        routeBuilders.setValueChoices(builders);
        if (builders.size() == 1) {
            // lets default the value if there's only one choice
            routeBuilders.setDefaultValue(first);
        } else if (currentFile != null) {
            // lets default to the current file
            if (currentFile.endsWith(".java")) {
                currentFile = currentFile.substring(0, currentFile.length() - 5);
            }
            for (String name : builders) {
                if (currentFile.endsWith(name)) {
                    routeBuilders.setDefaultValue(name);
                    answer = name;
                    break;
                }
            }
        }
        return answer;
    }

    protected void configureComponentName(Project project, final UISelectOne<ComponentDto> componentName, boolean consumerOnly, boolean producerOnly) throws Exception {

        // filter the list of components based on consumer and producer only
        Iterable<ComponentDto> it = CamelCommandsHelper.createComponentDtoValues(project, getCamelCatalog(), null, false, consumerOnly, producerOnly).call();
        final Map<String, ComponentDto> components = new LinkedHashMap<>();
        for (ComponentDto dto : it) {
            components.put(dto.getScheme(), dto);
        }

        componentName.setValueChoices(components.values());
        // include converter from string->dto
        componentName.setValueConverter(new Converter<String, ComponentDto>() {
            @Override
            public ComponentDto convert(String text) {
                return components.get(text);
            }
        });
        // show note about the chosen component
        componentName.addValueChangeListener(new ValueChangeListener() {
            @Override
            public void valueChanged(ValueChangeEvent event) {
                ComponentDto component = (ComponentDto) event.getNewValue();
                if (component != null) {
                    String description = component.getDescription();
                    componentName.setNote(description != null ? description : "");
                } else {
                    componentName.setNote("");
                }
            }
        });
    }

    protected Element getSelectedCamelElementNode(Project project, String xmlResourceName, String key) throws Exception {
        FileResource file = getXmlResourceFile(project, xmlResourceName);
        Document root = XmlLineNumberParser.parseXml(file.getResourceInputStream(), "camelContext,routes,rests", "http://camel.apache.org/schema/spring");
        Element selectedElement = null;
        if (root != null) {
            Node selectedNode = CamelXmlHelper.findCamelNodeInDocument(root, key);
            if (selectedNode instanceof Element) {
                selectedElement = (Element) selectedNode;
            }
        }
        return selectedElement;
    }

    protected String getSelectedFile(UIContext context) {
        String currentFile = null;
        // get selected file
        Optional<UIRegion<Object>> region = context.getSelection().getRegion();
        if (region.isPresent()) {
            Object resource = region.get().getResource();
            currentFile = resource.toString();
        }
        return currentFile;
    }

    protected int getCurrentCursorLine(UIContext context) {
        int answer = -1;
        Optional<UIRegion<Object>> region = context.getSelection().getRegion();
        if (region.isPresent()) {
            answer = region.get().getStartLine();
        }
        return answer;
    }

    protected int getCurrentCursorPosition(UIContext context) {
        int answer = -1;
        Optional<UIRegion<Object>> region = context.getSelection().getRegion();
        if (region.isPresent()) {
            answer = region.get().getStartPosition();
        }
        return answer;
    }

    protected String asRelativeFile(UIContext context, String currentFile) {
        boolean javaFile = currentFile != null && currentFile.endsWith(".java");

        // if its not a java file, then we need to have the relative path name
        String target = null;
        if (!javaFile) {
            Project project = getSelectedProject(context);
            ResourcesFacet facet = project.getFacet(ResourcesFacet.class);
            if (facet != null) {
                // we only want the relative dir name from the resource directory, eg WEB-INF/foo.xml
                String baseDir = facet.getResourceDirectory().getFullyQualifiedName();
                String fqn = currentFile;
                if (fqn != null && fqn.startsWith(baseDir)) {
                    target = fqn.substring(baseDir.length() + 1);
                }
            }
            if (target == null) {
                // try web-resource
                WebResourcesFacet facet2 = project.getFacet(WebResourcesFacet.class);
                if (facet2 != null) {
                    // we only want the relative dir name from the resource directory, eg WEB-INF/foo.xml
                    String baseDir = facet2.getWebRootDirectory().getFullyQualifiedName();
                    String fqn = currentFile;
                    if (fqn != null && fqn.startsWith(baseDir)) {
                        target = fqn.substring(baseDir.length() + 1);
                    }
                }
            }
        }

        return target != null ? target : currentFile;
    }

}

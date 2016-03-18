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

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.fabric8.forge.addon.utils.CamelProjectHelper;
import io.fabric8.forge.addon.utils.LineNumberHelper;
import io.fabric8.forge.addon.utils.XmlLineNumberParser;
import io.fabric8.forge.camel.commands.project.helper.CamelJavaParserHelper;
import io.fabric8.forge.camel.commands.project.helper.PoorMansLogger;
import io.fabric8.forge.camel.commands.project.helper.StringHelper;
import io.fabric8.forge.camel.commands.project.model.CamelComponentDetails;
import org.apache.camel.catalog.CamelCatalog;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.parser.java.facets.JavaSourceFacet;
import org.jboss.forge.addon.parser.java.resources.JavaResource;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.projects.facets.ResourcesFacet;
import org.jboss.forge.addon.projects.facets.WebResourcesFacet;
import org.jboss.forge.addon.resource.FileResource;
import org.jboss.forge.addon.resource.ResourceFacet;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.wizard.UIWizardStep;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.util.Strings;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static io.fabric8.forge.addon.utils.CamelProjectHelper.findCamelArtifactDependency;
import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.getPrefix;
import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.isDefaultValue;
import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.isMultiValue;
import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.isNonePlaceholderEnumValue;
import static io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper.ensureCamelArtifactIdAdded;
import static io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper.loadCamelComponentDetails;

public class ConfigureEndpointPropertiesStep extends AbstractCamelProjectCommand implements UIWizardStep {

    private static final PoorMansLogger LOG = new PoorMansLogger(false);

    private final DependencyInstaller dependencyInstaller;
    private final CamelCatalog camelCatalog;

    private final String componentName;
    private final String group;
    private final List<InputComponent> allInputs;
    private final List<InputComponent> inputs;
    private final boolean last;
    private final int index;
    private final int total;

    public ConfigureEndpointPropertiesStep(ProjectFactory projectFactory,
                                           DependencyInstaller dependencyInstaller,
                                           CamelCatalog camelCatalog,
                                           String componentName, String group,
                                           List<InputComponent> allInputs,
                                           List<InputComponent> inputs,
                                           boolean last, int index, int total) {
        this.projectFactory = projectFactory;
        this.dependencyInstaller = dependencyInstaller;
        this.camelCatalog = camelCatalog;
        this.componentName = componentName;
        this.group = group;
        this.allInputs = allInputs;
        this.inputs = inputs;
        this.last = last;
        // we want to 1-based
        this.index = index + 1;
        this.total = total;
    }

    public String getGroup() {
        return group;
    }

    public int getIndex() {
        return index;
    }

    public int getTotal() {
        return total;
    }

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(ConfigureEndpointPropertiesStep.class).name(
                "Camel: Endpoint options").category(Categories.create(CATEGORY))
                .description(String.format("Configure %s options (%s of %s)", group, index, total));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initializeUI(UIBuilder builder) throws Exception {
        if (inputs != null) {
            for (InputComponent input : inputs) {
                builder.add(input);
            }
        }
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        return null;
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();

        // only execute if we are last
        if (last) {
            String kind = mandatoryAttributeValue(attributeMap, "kind");
            if ("xml".equals(kind)) {
                return executeXml(context, attributeMap);
            } else if ("java".equals(kind)) {
                return executeJava(context, attributeMap);
            } else {
                return executeOther(context, attributeMap);
            }
        } else {
            return null;
        }
    }

    protected Result executeXml(UIExecutionContext context, Map<Object, Object> attributeMap) throws Exception {
        String camelComponentName = optionalAttributeValue(attributeMap, "componentName");
        String endpointInstanceName = optionalAttributeValue(attributeMap, "instanceName");
        String mode = mandatoryAttributeValue(attributeMap, "mode");
        String xml = mandatoryAttributeValue(attributeMap, "xml");

        // edit mode includes the existing uri and line number
        String lineNumber = null;
        String lineNumberEnd = null;
        String endpointUrl = null;
        if ("edit".equals(mode)) {
            lineNumber = mandatoryAttributeValue(attributeMap, "lineNumber");
            lineNumberEnd = optionalAttributeValue(attributeMap, "lineNumberEnd");
            endpointUrl = mandatoryAttributeValue(attributeMap, "endpointUri");

            // since this is XML we need to escape & as &amp;
            // to be safe that & is not already &amp; we need to revert it first
            endpointUrl = StringHelper.replaceAll(endpointUrl, "&amp;", "&");
            endpointUrl = StringHelper.replaceAll(endpointUrl, "&", "&amp;");
            endpointUrl = StringHelper.replaceAll(endpointUrl, "<", "&lt;");
            endpointUrl = StringHelper.replaceAll(endpointUrl, ">", "&gt;");
        }

        Project project = getSelectedProject(context);
        ResourcesFacet facet = project.getFacet(ResourcesFacet.class);
        WebResourcesFacet webResourcesFacet = null;
        if (project.hasFacet(WebResourcesFacet.class)) {
            webResourcesFacet = project.getFacet(WebResourcesFacet.class);
        }

        // lets find the camel component class
        CamelComponentDetails details = new CamelComponentDetails();
        Result result = loadCamelComponentDetails(camelCatalog, camelComponentName, details);
        if (result != null) {
            return result;
        }
        // and make sure its dependency is added
        result = ensureCamelArtifactIdAdded(project, details, dependencyInstaller);
        if (result != null) {
            return result;
        }

        // collect all the options that was set
        Map<String, String> options = new HashMap<String, String>();
        for (InputComponent input : allInputs) {
            String key = input.getName();
            // only use the value if a value was set (and the value is not the same as the default value)
            if (input.hasValue()) {
                String value = input.getValue().toString();
                if (value != null) {
                    // special for multivalued options
                    boolean isMultiValue = isMultiValue(camelCatalog, camelComponentName, key);
                    if (isMultiValue) {
                        String prefix = getPrefix(camelCatalog, camelComponentName, key);

                        // ensure the value has prefix for all its options
                        // and make sure to adjust & to &amp; if in xml

                        // since this is XML we need to escape & as &amp;
                        // to be safe that & is not already &amp; we need to revert it first
                        value = StringHelper.replaceAll(value, "&amp;", "&");
                        value = StringHelper.replaceAll(value, "&", "&amp;");

                        // rebuild value (accordingly to above comment)
                        StringBuilder sb = new StringBuilder();
                        String[] parts = value.split("&amp;");
                        for (int i = 0; i < parts.length; i++) {
                            String part = parts[i];
                            if (!part.startsWith(prefix)) {
                                part = prefix + part;
                            }
                            sb.append(part);
                            if (i < parts.length - 1) {
                                // since this is xml then use &amp; as separator
                                sb.append("&amp;");
                            }
                        }
                        value = sb.toString();
                    }

                    boolean matchDefault = isDefaultValue(camelCatalog, camelComponentName, key, value);
                    if ("none".equals(value)) {
                        // special for enum that may have a none as dummy placeholder which we should not add
                        boolean nonePlaceholder = isNonePlaceholderEnumValue(camelCatalog, camelComponentName, key);
                        if (!matchDefault && !nonePlaceholder) {
                            options.put(key, value);
                        }
                    } else if (!matchDefault) {
                        options.put(key, value);
                    }
                }
            } else if (input.isRequired() && input.hasDefaultValue()) {
                // if its required then we need to grab the value
                String value = input.getValue().toString();
                if (value != null) {
                    options.put(key, value);
                }
            }
        }

        LOG.info("Creating uri with component: " + componentName + " and options:" + options);
        String uri = camelCatalog.asEndpointUriXml(camelComponentName, options, false);
        LOG.info("Uri created: " + uri);
        if (uri == null) {
            return Results.fail("Cannot create endpoint uri");
        }

        LOG.info("Loading XML file " + xml);
        FileResource file = facet != null ? facet.getResource(xml) : null;
        if (file == null || !file.exists()) {
            file = webResourcesFacet != null ? webResourcesFacet.getWebResource(xml) : null;
        }
        if (file == null || !file.exists()) {
            return Results.fail("Cannot find XML file " + xml);
        }

        String cursorPosition = optionalAttributeValue(attributeMap, "cursorPosition");

        if (cursorPosition != null) {
            return addEndpointXml(file, uri, endpointInstanceName, xml, cursorPosition);
        } else {
            return addOrEditEndpointXml(file, uri, endpointUrl, endpointInstanceName, xml, lineNumber, lineNumberEnd);
        }
    }

    protected Result addOrEditEndpointXml(FileResource file, String uri, String endpointUrl, String endpointInstanceName, String xml, String lineNumber, String lineNumberEnd) throws Exception {
        // if we have a line number then use that to edit the existing value
        if (lineNumber != null) {
            List<String> lines = LineNumberHelper.readLines(file.getResourceInputStream());
            return editEndpointXml(lines, lineNumber, endpointUrl, uri, file, xml);
        } else {
            // we are in add mode, so parse dom to find <camelContext> and insert the endpoint where its needed
            Document root = XmlLineNumberParser.parseXml(file.getResourceInputStream());
            return addEndpointXml(root, endpointInstanceName, uri, file, xml);
        }
    }

    private Result editEndpointXml(List<String> lines, String lineNumber, String endpointUrl, String uri, FileResource file, String xml) {
        // the list is 0-based, and line number is 1-based
        int idx = Integer.valueOf(lineNumber) - 1;
        String line = lines.get(idx);

        // replace uri with new value
        line = StringHelper.replaceAll(line, endpointUrl, uri);
        lines.set(idx, line);

        LOG.info("Updating " + endpointUrl + " to " + uri + " at line " + lineNumber + " in file " + xml);

        // and save the file back
        String content = LineNumberHelper.linesToString(lines);
        file.setContents(content);

        return Results.success("Update endpoint uri: " + uri + " in file " + xml);
    }

    private Result addEndpointXml(Document root, String endpointInstanceName, String uri, FileResource file, String xml) throws Exception {
        String lineNumber;

        // The DOM api is so fucking terrible!
        if (root != null) {
            NodeList camels = root.getElementsByTagName("camelContext");
            // TODO: what about 2+ camel's ?
            if (camels != null && camels.getLength() == 1) {
                Node camel = camels.item(0);
                Node camelContext = null;
                boolean created = false;

                // find existing by id
                Node found = null;
                for (int i = 0; i < camel.getChildNodes().getLength(); i++) {
                    if ("camelContext".equals(camel.getNodeName())) {
                        camelContext = camel;
                    }

                    Node child = camel.getChildNodes().item(i);
                    if ("camelContext".equals(child.getNodeName())) {
                        camelContext = child;
                    }
                    if ("endpoint".equals(child.getNodeName())) {
                        // okay its an endpoint so if we can match by id attribute
                        String id = child.getAttributes().getNamedItem("id").getNodeValue();
                        if (endpointInstanceName.equals(id)) {
                            found = child;
                            break;
                        }
                    }
                }

                int extraSpaces = 0;
                int extraLines = 0;
                if (found == null) {
                    created = true;
                    found = insertEndpointBefore(camel);
                    if (found == null) {
                        // empty so use <camelContext> node
                        found = camelContext;
                        extraSpaces = 2;
                        extraLines = 1;
                    }
                }

                if (found == null) {
                    return Results.fail("Cannot find <camelContext> in XML file " + xml);
                }

                lineNumber = (String) found.getUserData(XmlLineNumberParser.LINE_NUMBER);

                // if we created a new endpoint, then insert a new line with the endpoint details
                List<String> lines = LineNumberHelper.readLines(file.getResourceInputStream());
                String line = String.format("<endpoint id=\"%s\" uri=\"%s\"/>", endpointInstanceName, uri);

                // the list is 0-based, and line number is 1-based
                int idx = lineNumber != null ? Integer.valueOf(lineNumber) - 1 : 0;
                idx += extraLines;
                int spaces = LineNumberHelper.leadingSpaces(lines, idx) + extraSpaces;
                line = LineNumberHelper.padString(line, spaces);
                if (created) {
                    lines.add(idx, line);
                } else {
                    lines.set(idx, line);
                }

                // and save the file back
                String content = LineNumberHelper.linesToString(lines);
                file.setContents(content);
            }
        }

        return Results.success("Added endpoint uri: " + uri + " in XML file " + xml);
    }

    private Result addEndpointXml(FileResource file, String uri, String endpointInstanceName, String xml, String cursorPosition) throws Exception {

        LOG.info("Adding uri " + uri + " at position " + cursorPosition + " in file " + xml);

        // we do not want to change the current code formatting so we need to search
        // replace the unformatted class source code
        StringBuilder sb = new StringBuilder(file.getContents());

        int pos = Integer.valueOf(cursorPosition);

        // move to end if pos is after the content
        pos = Math.min(sb.length(), pos);

        LOG.info("Adding endpoint at pos: " + pos + " in file: " + xml);

        // check if prev and next is a quote and if not then add it automatic
        int prev = pos - 1;
        int next = pos + 1;
        char ch = sb.charAt(prev);
        char ch2 = next < sb.length() ? sb.charAt(next) : ' ';
        if (ch != '"' && ch2 != '"') {
            uri = "\"" + uri + "\"";
        }

        // insert uri at position
        sb.insert(pos, uri);

        String text = sb.toString();

        // use this code currently to save content unformatted
        file.setContents(text);

        return Results.success("Added endpoint " + uri + " in " + xml);
    }

    protected Result executeJava(UIExecutionContext context, Map<Object, Object> attributeMap) throws Exception {
        String camelComponentName = mandatoryAttributeValue(attributeMap, "componentName");
        String endpointInstanceName = optionalAttributeValue(attributeMap, "instanceName");
        String mode = mandatoryAttributeValue(attributeMap, "mode");
        String routeBuilder = mandatoryAttributeValue(attributeMap, "routeBuilder");

        // edit mode includes the existing uri and line number
        String lineNumber = null;
        String lineNumberEnd = null;
        String endpointUrl = null;
        if ("edit".equals(mode)) {
            lineNumber = mandatoryAttributeValue(attributeMap, "lineNumber");
            lineNumberEnd = optionalAttributeValue(attributeMap, "lineNumberEnd");
            endpointUrl = mandatoryAttributeValue(attributeMap, "endpointUri");
        }

        Project project = getSelectedProject(context);
        JavaSourceFacet facet = project.getFacet(JavaSourceFacet.class);

        // lets find the camel component class
        CamelComponentDetails details = new CamelComponentDetails();
        Result result = loadCamelComponentDetails(camelCatalog, camelComponentName, details);
        if (result != null) {
            return result;
        }
        // and make sure its dependency is added
        result = ensureCamelArtifactIdAdded(project, details, dependencyInstaller);
        if (result != null) {
            return result;
        }

        // collect all the options that was set
        Map<String, String> options = new HashMap<String, String>();
        for (InputComponent input : allInputs) {
            String key = input.getName();
            // only use the value if a value was set (and the value is not the same as the default value)
            if (input.hasValue()) {
                String value = input.getValue().toString();
                if (value != null) {
                    // special for multivalued options
                    boolean isMultiValue = isMultiValue(camelCatalog, camelComponentName, key);
                    if (isMultiValue) {
                        String prefix = getPrefix(camelCatalog, camelComponentName, key);

                        // ensure the value has prefix for all its options
                        // and make sure to adjust & (we replace to xml style)
                        value = StringHelper.replaceAll(value, "&amp;", "&");
                        value = StringHelper.replaceAll(value, "&", "&amp;");

                        // rebuild value (accordingly to above comment)
                        StringBuilder sb = new StringBuilder();
                        String[] parts = value.split("&amp;");
                        for (int i = 0; i < parts.length; i++) {
                            String part = parts[i];
                            if (!part.startsWith(prefix)) {
                                part = prefix + part;
                            }
                            sb.append(part);
                            if (i < parts.length - 1) {
                                // since this is java then use & as separator
                                sb.append("&");
                            }
                        }
                        value = sb.toString();
                    }
                    boolean matchDefault = isDefaultValue(camelCatalog, camelComponentName, key, value);
                    if ("none".equals(value)) {
                        // special for enum that may have a none as dummy placeholder which we should not add
                        boolean nonePlaceholder = isNonePlaceholderEnumValue(camelCatalog, camelComponentName, key);
                        if (!matchDefault && !nonePlaceholder) {
                            options.put(key, value);
                        }
                    } else if (!matchDefault) {
                        options.put(key, value);
                    }
                }
            } else if (input.isRequired() && input.hasDefaultValue()) {
                // if its required then we need to grab the value
                String value = input.getValue().toString();
                if (value != null) {
                    options.put(key, value);
                }
            }
        }

        LOG.info("Creating uri with component: " + componentName + " and options:" + options);
        String uri = camelCatalog.asEndpointUri(camelComponentName, options, false);
        LOG.info("Uri created: " + uri);
        if (uri == null) {
            return Results.fail("Cannot create endpoint uri");
        }

        LOG.info("Loading Java file " + routeBuilder);
        JavaResource file = facet.getJavaResource(routeBuilder);
        if (file == null || !file.exists()) {
            return Results.fail("RouteBuilder " + routeBuilder + " does not exist");
        }

        String cursorPosition = optionalAttributeValue(attributeMap, "cursorPosition");
        if (cursorPosition != null) {
            return addEndpointJava(project, facet, file, uri, endpointInstanceName, routeBuilder, cursorPosition);
        } else {
            return addOrEditEndpointJava(project, facet, file, uri, endpointUrl, endpointInstanceName, routeBuilder, lineNumber, lineNumberEnd);
        }
    }

    protected Result addOrEditEndpointJava(Project project, JavaSourceFacet facet, JavaResource file, String uri, String endpointUrl,
                                           String endpointInstanceName, String routeBuilder, String lineNumber, String lineNumberEnd) throws Exception {

        JavaClassSource clazz = file.getJavaType();

        boolean updated = true;
        boolean newCode = false;

        if (endpointInstanceName != null) {
            // add the endpoint as a field
            // special for CDI as we use different set of annotations
            FieldSource field = clazz.getField(endpointInstanceName);
            AnnotationSource annotation;
            if (field == null) {
                field = clazz.addField();
                field.setName(endpointInstanceName);
                field.setType("org.apache.camel.Endpoint");
                field.setPrivate();
                updated = false;
                newCode = true;
            }
            boolean cdi = findCamelArtifactDependency(project, "camel-cdi") != null;
            if (cdi) {
                // cdi uses @Inject @Uri
                if (!field.hasAnnotation("javax.inject.Inject")) {
                    field.addAnnotation("javax.inject.Inject");
                    newCode = true;
                }
                if (!field.hasAnnotation("org.apache.camel.cdi.Uri")) {
                    field.addAnnotation("org.apache.camel.cdi.Uri");
                    newCode = true;
                }
                annotation = field.getAnnotation("org.apache.camel.cdi.Uri");
                annotation.setStringValue(uri);
            } else {
                if (!field.hasAnnotation("org.apache.camel.EndpointInject")) {
                    field.addAnnotation("org.apache.camel.EndpointInject");
                    newCode = true;
                }
                annotation = field.getAnnotation("org.apache.camel.EndpointInject");
                annotation.setStringValue("uri", uri);
            }

            if (!clazz.hasImport("org.apache.camel.Endpoint")) {
                // make sure to import what we use
                clazz.addImport("org.apache.camel.Endpoint");
                newCode = true;
            }

            if (cdi) {
                if (!clazz.hasImport("javax.inject.Inject")) {
                    clazz.addImport("javax.inject.Inject");
                    newCode = true;
                }
                if (!clazz.hasImport("org.apache.camel.cdi.Uri")) {
                    clazz.addImport("org.apache.camel.cdi.Uri");
                    newCode = true;
                }
            } else {
                if (!clazz.hasImport("org.apache.camel.EndpointInject")) {
                    clazz.addImport("org.apache.camel.EndpointInject");
                    newCode = true;
                }
            }

            // attempt to read the line number and do a search/replace and save, to avoid re-formatting the source code.
            if (!newCode && lineNumber != null) {
                List<String> lines = LineNumberHelper.readLines(file.getResourceInputStream());

                // the list is 0-based, and line number is 1-based
                int idx = Integer.valueOf(lineNumber) - 1;
                if (idx <= lines.size()) {
                    String line = lines.get(idx);

                    // wonder if we should have better way, than a replaceFirst?
                    String find = Pattern.quote(endpointUrl);
                    line = line.replaceFirst(find, uri);

                    // update the line
                    lines.set(idx, line);

                    // and save the file back
                    String content = LineNumberHelper.linesToString(lines);
                    file.setContents(content);
                } else {
                    // okay give up and just use roaster to save it as it if was new-code
                    newCode = true;
                }
            }

            if (newCode) {
                facet.saveJavaSource(clazz);
            }

            if (updated) {
                return Results.success("Updated endpoint " + endpointInstanceName + " in " + routeBuilder);
            } else {
                return Results.success("Added endpoint " + endpointInstanceName + " in " + routeBuilder);
            }

        } else {
            MethodSource<JavaClassSource> method = CamelJavaParserHelper.findConfigureMethod(clazz);
            if (method != null) {

                // we do not want to change the current code formatting so we need to search
                // replace the unformatted class soucre code
                String code = clazz.toUnformattedString();

                // TODO: we can use the line number to find where we start at least

                // wonder if we should have better way, than a replaceFirst?
                String find = Pattern.quote(endpointUrl);
                code = code.replaceFirst(find, uri);

                // use this code currently to save content unformatted
                file.setContents(new ByteArrayInputStream(code.getBytes()), null);

                return Results.success("Updated endpoint " + endpointUrl + " -> " + uri + " in " + routeBuilder);
            }
        }

        return Results.fail("Cannot update endpoint");
    }

    protected Result addEndpointJava(Project project, JavaSourceFacet facet, JavaResource file, String uri,
                                     String endpointInstanceName, String routeBuilder, String cursorPosition) throws Exception {

        JavaClassSource clazz = file.getJavaType();

        // we do not want to change the current code formatting so we need to search
        // replace the unformatted class source code
        StringBuilder sb = new StringBuilder(clazz.toUnformattedString());

        int pos = Integer.valueOf(cursorPosition);

        // move to end if pos is after the content
        pos = Math.min(sb.length(), pos);

        LOG.info("Adding endpoint at pos: " + pos + " in file: " + routeBuilder);

        // check if prev and next is a quote and if not then add it automatic
        int prev = pos - 1;
        int next = pos + 1;
        char ch = sb.charAt(prev);
        char ch2 = next < sb.length() ? sb.charAt(next) : ' ';
        if (ch != '"' && ch2 != '"') {
            uri = "\"" + uri + "\"";
        }

        // insert uri at position
        sb.insert(pos, uri);

        // use this code currently to save content unformatted
        file.setContents(sb.toString());

        return Results.success("Added endpoint " + uri + " in " + routeBuilder);
    }

    protected Result executeOther(UIExecutionContext context, Map<Object, Object> attributeMap) throws Exception {
        String camelComponentName = mandatoryAttributeValue(attributeMap, "componentName");
        String mode = mandatoryAttributeValue(attributeMap, "mode");
        String currentFile = mandatoryAttributeValue(attributeMap, "currentFile");

        // TODO: add support for edit

        // edit mode includes the existing uri and line number
        String lineNumber = null;
        String lineNumberEnd = null;
        String endpointUrl = null;
        if ("edit".equals(mode)) {
            lineNumber = mandatoryAttributeValue(attributeMap, "lineNumber");
            lineNumberEnd = optionalAttributeValue(attributeMap, "lineNumberEnd");
            endpointUrl = mandatoryAttributeValue(attributeMap, "endpointUri");
        }

        Project project = getSelectedProject(context);
        ResourcesFacet facet = project.getFacet(ResourcesFacet.class);
        WebResourcesFacet webResourcesFacet = null;
        if (project.hasFacet(WebResourcesFacet.class)) {
            webResourcesFacet = project.getFacet(WebResourcesFacet.class);
        }

        // lets find the camel component class
        CamelComponentDetails details = new CamelComponentDetails();
        Result result = loadCamelComponentDetails(camelCatalog, camelComponentName, details);
        if (result != null) {
            return result;
        }
        // and make sure its dependency is added
        result = ensureCamelArtifactIdAdded(project, details, dependencyInstaller);
        if (result != null) {
            return result;
        }

        // collect all the options that was set
        Map<String, String> options = new HashMap<String, String>();
        for (InputComponent input : allInputs) {
            String key = input.getName();
            // only use the value if a value was set (and the value is not the same as the default value)
            if (input.hasValue()) {
                String value = input.getValue().toString();
                if (value != null) {
                    // special for multivalued options
                    boolean isMultiValue = isMultiValue(camelCatalog, camelComponentName, key);
                    if (isMultiValue) {
                        String prefix = getPrefix(camelCatalog, camelComponentName, key);

                        // ensure the value has prefix for all its options
                        // and make sure to adjust & (we replace to xml style)
                        value = StringHelper.replaceAll(value, "&amp;", "&");
                        value = StringHelper.replaceAll(value, "&", "&amp;");

                        // rebuild value (accordingly to above comment)
                        StringBuilder sb = new StringBuilder();
                        String[] parts = value.split("&amp;");
                        for (int i = 0; i < parts.length; i++) {
                            String part = parts[i];
                            if (!part.startsWith(prefix)) {
                                part = prefix + part;
                            }
                            sb.append(part);
                            if (i < parts.length - 1) {
                                // since this is java then use & as separator
                                sb.append("&");
                            }
                        }
                        value = sb.toString();
                    }
                    boolean matchDefault = isDefaultValue(camelCatalog, camelComponentName, key, value);
                    if ("none".equals(value)) {
                        // special for enum that may have a none as dummy placeholder which we should not add
                        boolean nonePlaceholder = isNonePlaceholderEnumValue(camelCatalog, camelComponentName, key);
                        if (!matchDefault && !nonePlaceholder) {
                            options.put(key, value);
                        }
                    } else if (!matchDefault) {
                        options.put(key, value);
                    }
                }
            } else if (input.isRequired() && input.hasDefaultValue()) {
                // if its required then we need to grab the value
                String value = input.getValue().toString();
                if (value != null) {
                    options.put(key, value);
                }
            }
        }

        LOG.info("Creating uri with component: " + componentName + " and options:" + options);
        String uri = camelCatalog.asEndpointUri(camelComponentName, options, false);
        LOG.info("Uri created: " + uri);
        if (uri == null) {
            return Results.fail("Cannot create endpoint uri");
        }

        LOG.info("Loading resource file " + currentFile);
        FileResource file = facet != null ? facet.getResource(currentFile) : null;
        if (file == null || !file.exists()) {
            file = webResourcesFacet != null ? webResourcesFacet.getWebResource(currentFile) : null;
        }
        if (file == null || !file.exists()) {
            return Results.fail("Cannot find resource file " + currentFile);
        }

        String cursorPosition = optionalAttributeValue(attributeMap, "cursorPosition");
        if ("edit".equals(mode)) {
            return editEndpointOther(project, facet, file, uri, endpointUrl, currentFile, lineNumber);
        } else {
            return addEndpointOther(project, facet, file, uri, currentFile, cursorPosition);
        }
    }

    protected Result editEndpointOther(Project project, ResourcesFacet facet, FileResource file, String uri, String endpointUrl,
                                      String currentFile, String lineNumber) throws Exception {

        List<String> lines = LineNumberHelper.readLines(file.getResourceInputStream());

        // the list is 0-based, and line number is 1-based
        int idx = Integer.valueOf(lineNumber) - 1;
        String line = lines.get(idx);

        // replace uri with new value
        line = StringHelper.replaceAll(line, endpointUrl, uri);
        lines.set(idx, line);

        LOG.info("Updating " + endpointUrl + " to " + uri + " at line " + lineNumber + " in file " + currentFile);

        // and save the file back
        String content = LineNumberHelper.linesToString(lines);
        file.setContents(content);

        return Results.success("Update endpoint uri: " + uri + " in file " + currentFile);
    }

    protected Result addEndpointOther(Project project, ResourcesFacet facet, FileResource file, String uri,
                                      String currentFile, String cursorPosition) throws Exception {

        StringBuilder sb = new StringBuilder(file.getContents());

        int pos = Integer.valueOf(cursorPosition);

        // move to end if pos is after the content
        pos = Math.min(sb.length(), pos);

        LOG.info("Adding endpoint at pos: " + pos + " in file: " + currentFile);

        // insert uri at position
        sb.insert(pos, uri);

        // use this code currently to save content unformatted
        file.setContents(sb.toString());

        return Results.success("Added endpoint " + uri + " in " + currentFile);
    }

    /**
     * To find the closet node that we need to insert the endpoints before, so the Camel schema is valid.
     */
    private Node insertEndpointBefore(Node camel) {
        // if there is endpoints then the cut-off is after the last
        Node endpoint = null;
        for (int i = 0; i < camel.getChildNodes().getLength(); i++) {
            Node found = camel.getChildNodes().item(i);
            String name = found.getNodeName();
            if ("endpoint".equals(name)) {
                endpoint = found;
            }
        }
        if (endpoint != null) {
            return endpoint;
        }

        Node last = null;
        // if no endpoints then try to find cut-off according the XSD rules
        for (int i = 0; i < camel.getChildNodes().getLength(); i++) {
            Node found = camel.getChildNodes().item(i);
            String name = found.getNodeName();
            if ("dataFormats".equals(name) || "redeliveryPolicyProfile".equals(name)
                    || "onException".equals(name) || "onCompletion".equals(name)
                    || "intercept".equals(name) || "interceptFrom".equals(name)
                    || "interceptSendToEndpoint".equals(name) || "restConfiguration".equals(name)
                    || "rest".equals(name) || "route".equals(name)) {
                return found;
            }
            if (found.getNodeType() == Node.ELEMENT_NODE) {
                last = found;
            }
        }

        return last;
    }

    /**
     * Returns the mandatory String value of the given name
     *
     * @throws IllegalArgumentException if the value is not available in the given attribute map
     */
    public static String mandatoryAttributeValue(Map<Object, Object> attributeMap, String name) {
        Object value = attributeMap.get(name);
        if (value != null) {
            String text = value.toString();
            if (!Strings.isBlank(text)) {
                return text;
            }
        }
        throw new IllegalArgumentException("The attribute value '" + name + "' did not get passed on from the previous wizard page");
    }

    /**
     * Returns the optional String value of the given name
     */
    public static String optionalAttributeValue(Map<Object, Object> attributeMap, String name) {
        Object value = attributeMap.get(name);
        if (value != null) {
            String text = value.toString();
            if (!Strings.isBlank(text)) {
                return text;
            }
        }
        return null;
    }
}

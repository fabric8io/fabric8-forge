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
package io.fabric8.forge.camel.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.fabric8.forge.camel.commands.project.helper.RouteBuilderParser;
import io.fabric8.forge.camel.commands.project.helper.XmlRouteParser;
import io.fabric8.forge.camel.commands.project.model.CamelEndpointDetails;
import io.fabric8.forge.camel.commands.project.model.CamelSimpleDetails;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.catalog.SimpleValidationResult;
import org.apache.camel.catalog.lucene.LuceneSuggestionStrategy;
import org.apache.camel.catalog.maven.MavenVersionManager;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.source.JavaClassSource;

/**
 * Analyses the project source code for Camel routes, and validates the endpoint uris and simple expressions.
 */
@Mojo(name = "validate", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES,
        requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class EndpointMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Whether to fail if invalid Camel endpoints was found. By default the plugin logs the errors at WARN level
     */
    @Parameter(property = "failOnError", defaultValue = "false", readonly = true, required = false)
    private boolean failOnError;

    /**
     * Whether to log endpoint URIs which was un-parsable and therefore not possible to validate
     */
    @Parameter(property = "logUnparseable", defaultValue = "false", readonly = true, required = false)
    private boolean logUnparseable;

    /**
     * Whether to include Java files to be validated for invalid Camel endpoints
     */
    @Parameter(property = "includeJava", defaultValue = "true", readonly = true, required = false)
    private boolean includeJava;

    /**
     * Whether to include XML files to be validated for invalid Camel endpoints
     */
    @Parameter(property = "includeXml", defaultValue = "true", readonly = true, required = false)
    private boolean includeXml;

    /**
     * Whether to include test source code
     */
    @Parameter(property = "includeTest", defaultValue = "false", readonly = true, required = false)
    private boolean includeTest;

    /**
     * To filter the names of java and xml files to only include files matching any of the given list of patterns (wildcard and regular expression).
     * Multiple values can be separated by comma.
     */
    @Parameter(property = "includes", readonly = true, required = false)
    private String includes;

    /**
     * To filter the names of java and xml files to exclude files matching any of the given list of patterns (wildcard and regular expression).
     * Multiple values can be separated by comma.
     */
    @Parameter(property = "excludes", readonly = true, required = false)
    private String excludes;

    /**
     * Whether to ignore unknown components
     */
    @Parameter(property = "ignoreUnknownComponent", defaultValue = "true", readonly = true, required = false)
    private boolean ignoreUnknownComponent;

    /**
     * Whether to ignore incapable of parsing the endpoint uri
     */
    @Parameter(property = "ignoreIncapable", defaultValue = "true", readonly = true, required = false)
    private boolean ignoreIncapable;

    /**
     * Whether to ignore components that uses lenient properties. When this is true, then the uri validation is stricter
     * but would fail on properties that are not part of the component but in the uri because of using lenient properties.
     * For example using the HTTP components to provide query parameters in the endpoint uri.
     */
    @Parameter(property = "ignoreLenientProperties", defaultValue = "true", readonly = true, required = false)
    private boolean ignoreLenientProperties;

    /**
     * Whether to show all endpoints and simple expressions (both invalid and valid).
     */
    @Parameter(property = "showAll", defaultValue = "false", readonly = true, required = false)
    private boolean showAll;

    /**
     * Whether to allow downloading Camel catalog version from the internet. This is needed if the project
     * uses a different Camel version than this plugin is using by default.
     */
    @Parameter(property = "downloadVersion", defaultValue = "true", readonly = true, required = false)
    private boolean downloadVersion;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        CamelCatalog catalog = new DefaultCamelCatalog();
        // add activemq as known component
        catalog.addComponent("activemq", "org.apache.activemq.camel.component.ActiveMQComponent");
        // enable did you mean
        catalog.setSuggestionStrategy(new LuceneSuggestionStrategy());
        // enable loading other catalog versions dynamically
        catalog.setVersionManager(new MavenVersionManager());
        // enable caching
        catalog.enableCache();

        if (downloadVersion) {
            String catalogVersion = catalog.getCatalogVersion();
            String version = findCamelVersion(project);
            if (version != null && !version.equals(catalogVersion)) {
                // the project uses a different Camel version so attempt to load it
                getLog().info("Downloading Camel version: " + version);
                boolean loaded = catalog.loadVersion(version);
                if (!loaded) {
                    getLog().warn("Error downloading Camel version: " + version);
                }
            }
        }

        // if using the same version as the fabric8-camel-maven-plugin we must still load it
        if (catalog.getLoadedVersion() == null) {
            catalog.loadVersion(catalog.getCatalogVersion());
        }

        if (catalog.getLoadedVersion() != null) {
            getLog().info("Using Camel version: " + catalog.getLoadedVersion());
        } else {
            // force load version from the fabric8-camel-maven-plugin
            getLog().info("Using Camel version: " + catalog.getCatalogVersion());
        }

        List<CamelEndpointDetails> endpoints = new ArrayList<>();
        List<CamelSimpleDetails> simpleExpressions = new ArrayList<>();
        Set<File> javaFiles = new LinkedHashSet<File>();
        Set<File> xmlFiles = new LinkedHashSet<File>();

        // find all java route builder classes
        if (includeJava) {
            for (String dir : project.getCompileSourceRoots()) {
                findJavaFiles(new File(dir), javaFiles);
            }
            if (includeTest) {
                for (String dir : project.getTestCompileSourceRoots()) {
                    findJavaFiles(new File(dir), javaFiles);
                }
            }
        }
        // find all xml routes
        if (includeXml) {
            for (Resource dir : project.getResources()) {
                findXmlFiles(new File(dir.getDirectory()), xmlFiles);
            }
            if (includeTest) {
                for (Resource dir : project.getTestResources()) {
                    findXmlFiles(new File(dir.getDirectory()), xmlFiles);
                }
            }
        }

        for (File file : javaFiles) {
            if (matchFile(file)) {
                try {
                    List<CamelEndpointDetails> fileEndpoints = new ArrayList<>();
                    List<CamelSimpleDetails> fileSimpleExpressions = new ArrayList<>();
                    List<String> unparsable = new ArrayList<>();

                    // parse the java source code and find Camel RouteBuilder classes
                    String fqn = file.getPath();
                    String baseDir = ".";
                    JavaType out = Roaster.parse(file);
                    // we should only parse java classes (not interfaces and enums etc)
                    if (out != null && out instanceof JavaClassSource) {
                        JavaClassSource clazz = (JavaClassSource) out;
                        RouteBuilderParser.parseRouteBuilderEndpoints(clazz, baseDir, fqn, fileEndpoints, unparsable, includeTest);
                        RouteBuilderParser.parseRouteBuilderSimpleExpressions(clazz, baseDir, fqn, fileSimpleExpressions);

                        // add what we found in this file to the total list
                        endpoints.addAll(fileEndpoints);
                        simpleExpressions.addAll(fileSimpleExpressions);

                        // was there any unparsable?
                        if (logUnparseable && !unparsable.isEmpty()) {
                            for (String uri : unparsable) {
                                getLog().warn("Cannot parse endpoint uri " + uri + " in java file " + file);
                            }
                        }
                    }
                } catch (Exception e) {
                    getLog().warn("Error parsing java file " + file + " code due " + e.getMessage(), e);
                }
            }
        }
        for (File file : xmlFiles) {
            if (matchFile(file)) {
                try {
                    List<CamelEndpointDetails> fileEndpoints = new ArrayList<>();
                    List<CamelSimpleDetails> fileSimpleExpressions = new ArrayList<>();

                    // parse the xml source code and find Camel routes
                    String fqn = file.getPath();
                    String baseDir = ".";

                    InputStream is = new FileInputStream(file);
                    XmlRouteParser.parseXmlRouteEndpoints(is, baseDir, fqn, fileEndpoints);
                    is.close();
                    // need a new stream
                    is = new FileInputStream(file);
                    XmlRouteParser.parseXmlRouteSimpleExpressions(is, baseDir, fqn, fileSimpleExpressions);
                    is.close();

                    // add what we found in this file to the total list
                    endpoints.addAll(fileEndpoints);
                    simpleExpressions.addAll(fileSimpleExpressions);
                } catch (Exception e) {
                    getLog().warn("Error parsing xml file " + file + " code due " + e.getMessage(), e);
                }
            }
        }

        int endpointErrors = 0;
        int unknownComponents = 0;
        int incapableErrors = 0;
        for (CamelEndpointDetails detail : endpoints) {
            EndpointValidationResult result = catalog.validateEndpointProperties(detail.getEndpointUri(), ignoreLenientProperties);

            boolean ok = result.isSuccess();
            if (!ok && ignoreUnknownComponent && result.getUnknownComponent() != null) {
                // if we failed due unknown component then be okay if we should ignore that
                unknownComponents++;
                ok = true;
            }
            if (!ok && ignoreIncapable && result.getIncapable() != null) {
                // if we failed due incapable then be okay if we should ignore that
                incapableErrors++;
                ok = true;
            }
            if (!ok) {
                if (result.getUnknownComponent() != null) {
                    unknownComponents++;
                } else if (result.getIncapable() != null) {
                    incapableErrors++;
                } else {
                    endpointErrors++;
                }

                StringBuilder sb = new StringBuilder();
                sb.append("Endpoint validation error at: ");
                if (detail.getClassName() != null && detail.getLineNumber() != null) {
                    // this is from java code
                    sb.append(detail.getClassName());
                    if (detail.getMethodName() != null) {
                        sb.append(".").append(detail.getMethodName());
                    }
                    sb.append("(").append(asSimpleClassName(detail.getClassName())).append(".java:");
                    sb.append(detail.getLineNumber()).append(")");
                } else if (detail.getLineNumber() != null) {
                    // this is from xml
                    String fqn = stripRootPath(asRelativeFile(detail.getFileName()));
                    if (fqn.endsWith(".xml")) {
                        fqn = fqn.substring(0, fqn.length() - 4);
                        fqn = asPackageName(fqn);
                    }
                    sb.append(fqn);
                    sb.append("(").append(asSimpleClassName(fqn)).append(".xml:");
                    sb.append(detail.getLineNumber()).append(")");
                } else {
                    sb.append(detail.getFileName());
                }
                sb.append("\n\n");
                String out = result.summaryErrorMessage(false);
                sb.append(out);
                sb.append("\n\n");

                getLog().warn(sb.toString());
            } else if (showAll) {
                StringBuilder sb = new StringBuilder();
                sb.append("Endpoint validation passsed at: ");
                if (detail.getClassName() != null && detail.getLineNumber() != null) {
                    // this is from java code
                    sb.append(detail.getClassName());
                    if (detail.getMethodName() != null) {
                        sb.append(".").append(detail.getMethodName());
                    }
                    sb.append("(").append(asSimpleClassName(detail.getClassName())).append(".java:");
                    sb.append(detail.getLineNumber()).append(")");
                } else if (detail.getLineNumber() != null) {
                    // this is from xml
                    String fqn = stripRootPath(asRelativeFile(detail.getFileName()));
                    if (fqn.endsWith(".xml")) {
                        fqn = fqn.substring(0, fqn.length() - 4);
                        fqn = asPackageName(fqn);
                    }
                    sb.append(fqn);
                    sb.append("(").append(asSimpleClassName(fqn)).append(".xml:");
                    sb.append(detail.getLineNumber()).append(")");
                } else {
                    sb.append(detail.getFileName());
                }
                sb.append("\n");
                sb.append("\n\t").append(result.getUri());
                sb.append("\n\n");

                getLog().info(sb.toString());
            }
        }
        String endpointSummary;
        if (endpointErrors == 0) {
            int ok = endpoints.size() - endpointErrors - incapableErrors - unknownComponents;
            endpointSummary = String.format("Endpoint validation success: (%s = passed, %s = invalid, %s = incapable, %s = unknown components)", ok, endpointErrors, incapableErrors, unknownComponents);
        } else {
            int ok = endpoints.size() - endpointErrors - incapableErrors - unknownComponents;
            endpointSummary = String.format("Endpoint validation error: (%s = passed, %s = invalid, %s = incapable, %s = unknown components)", ok, endpointErrors, incapableErrors, unknownComponents);
        }

        if (endpointErrors > 0) {
            getLog().warn(endpointSummary);
        } else {
            getLog().info(endpointSummary);
        }

        int simpleErrors = 0;
        for (CamelSimpleDetails detail : simpleExpressions) {
            SimpleValidationResult result = catalog.validateSimpleExpression(detail.getSimple());
            if (!result.isSuccess()) {
                simpleErrors++;

                StringBuilder sb = new StringBuilder();
                sb.append("Simple validation error at: ");
                if (detail.getClassName() != null && detail.getLineNumber() != null) {
                    // this is from java code
                    sb.append(detail.getClassName());
                    if (detail.getMethodName() != null) {
                        sb.append(".").append(detail.getMethodName());
                    }
                    sb.append("(").append(asSimpleClassName(detail.getClassName())).append(".java:");
                    sb.append(detail.getLineNumber()).append(")");
                } else if (detail.getLineNumber() != null) {
                    // this is from xml
                    String fqn = stripRootPath(asRelativeFile(detail.getFileName()));
                    if (fqn.endsWith(".xml")) {
                        fqn = fqn.substring(0, fqn.length() - 4);
                        fqn = asPackageName(fqn);
                    }
                    sb.append(fqn);
                    sb.append("(").append(asSimpleClassName(fqn)).append(".xml:");
                    sb.append(detail.getLineNumber()).append(")");
                } else {
                    sb.append(detail.getFileName());
                }
                sb.append("\n");
                String[] lines = result.getError().split("\n");
                for (String line : lines) {
                    sb.append("\n\t").append(line);
                }
                sb.append("\n");

                getLog().warn(sb.toString());
            } else if (showAll) {
                StringBuilder sb = new StringBuilder();
                sb.append("Simple validation passed at: ");
                if (detail.getClassName() != null && detail.getLineNumber() != null) {
                    // this is from java code
                    sb.append(detail.getClassName());
                    if (detail.getMethodName() != null) {
                        sb.append(".").append(detail.getMethodName());
                    }
                    sb.append("(").append(asSimpleClassName(detail.getClassName())).append(".java:");
                    sb.append(detail.getLineNumber()).append(")");
                } else if (detail.getLineNumber() != null) {
                    // this is from xml
                    String fqn = stripRootPath(asRelativeFile(detail.getFileName()));
                    if (fqn.endsWith(".xml")) {
                        fqn = fqn.substring(0, fqn.length() - 4);
                        fqn = asPackageName(fqn);
                    }
                    sb.append(fqn);
                    sb.append("(").append(asSimpleClassName(fqn)).append(".xml:");
                    sb.append(detail.getLineNumber()).append(")");
                } else {
                    sb.append(detail.getFileName());
                }
                sb.append("\n");
                sb.append("\n\t").append(result.getSimple());
                sb.append("\n\n");

                getLog().info(sb.toString());
            }
        }

        String simpleSummary;
        if (simpleErrors == 0) {
            int ok = simpleExpressions.size() - simpleErrors;
            simpleSummary = String.format("Simple validation success: (%s = passed, %s = invalid)", ok, simpleErrors);
        } else {
            int ok = simpleExpressions.size() - simpleErrors;
            simpleSummary = String.format("Simple validation error: (%s = passed, %s = invalid)", ok, simpleErrors);
        }

        if (failOnError && (endpointErrors > 0 || simpleErrors > 0)) {
            throw new MojoExecutionException(endpointSummary + "\n" + simpleSummary);
        }

        if (simpleErrors > 0) {
            getLog().warn(simpleSummary);
        } else {
            getLog().info(simpleSummary);
        }
    }

    private static String findCamelVersion(MavenProject project) {
        Dependency candidate = null;

        for (Dependency dep : project.getDependencies()) {
            if ("org.apache.camel".equals(dep.getGroupId())) {
                if ("camel-core".equals(dep.getArtifactId())) {
                    // favor camel-core
                    candidate = dep;
                    break;
                } else {
                    candidate = dep;
                }
            }
        }
        if (candidate != null) {
            return candidate.getVersion();
        }

        return null;
    }

    private void findJavaFiles(File dir, Set<File> javaFiles) {
        File[] files = dir.isDirectory() ? dir.listFiles() : null;
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                } else if (file.isDirectory()) {
                    findJavaFiles(file, javaFiles);
                }
            }
        }
    }

    private void findXmlFiles(File dir, Set<File> xmlFiles) {
        File[] files = dir.isDirectory() ? dir.listFiles() : null;
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".xml")) {
                    xmlFiles.add(file);
                } else if (file.isDirectory()) {
                    findXmlFiles(file, xmlFiles);
                }
            }
        }
    }

    private boolean matchFile(File file) {
        if (excludes == null && includes == null) {
            return true;
        }

        // exclude take precedence
        if (excludes != null) {
            for (String exclude : excludes.split(",")) {
                exclude = exclude.trim();
                // try both with and without directory in the name
                String fqn = stripRootPath(asRelativeFile(file.getAbsolutePath()));
                boolean match = EndpointHelper.matchPattern(fqn, exclude) || EndpointHelper.matchPattern(file.getName(), exclude);
                if (match) {
                    return false;
                }
            }
        }

        // include
        if (includes != null) {
            for (String include : includes.split(",")) {
                include = include.trim();
                // try both with and without directory in the name
                String fqn = stripRootPath(asRelativeFile(file.getAbsolutePath()));
                boolean match = EndpointHelper.matchPattern(fqn, include) || EndpointHelper.matchPattern(file.getName(), include);
                if (match) {
                    return true;
                }
            }
            // did not match any includes
            return false;
        }

        // was not excluded nor failed include so its accepted
        return true;
    }

    private String asRelativeFile(String name) {
        String answer = name;

        String base = project.getBasedir().getAbsolutePath();
        if (name.startsWith(base)) {
            answer = name.substring(base.length());
            // skip leading slash for relative path
            if (answer.startsWith(File.separator)) {
                answer = answer.substring(1);
            }
        }
        return answer;
    }

    private String stripRootPath(String name) {
        // strip out any leading source / resource directory

        for (String dir : project.getCompileSourceRoots()) {
            dir = asRelativeFile(dir);
            if (name.startsWith(dir)) {
                return name.substring(dir.length() + 1);
            }
        }
        for (String dir : project.getTestCompileSourceRoots()) {
            dir = asRelativeFile(dir);
            if (name.startsWith(dir)) {
                return name.substring(dir.length() + 1);
            }
        }
        for (Resource resource : project.getResources()) {
            String dir = asRelativeFile(resource.getDirectory());
            if (name.startsWith(dir)) {
                return name.substring(dir.length() + 1);
            }
        }
        for (Resource resource : project.getTestResources()) {
            String dir = asRelativeFile(resource.getDirectory());
            if (name.startsWith(dir)) {
                return name.substring(dir.length() + 1);
            }
        }

        return name;
    }

    private static String asPackageName(String name) {
        return name.replace(File.separator, ".");
    }

    private static String asSimpleClassName(String className) {
        int dot = className.lastIndexOf('.');
        if (dot > 0) {
            return className.substring(dot + 1);
        } else {
            return className;
        }
    }

}

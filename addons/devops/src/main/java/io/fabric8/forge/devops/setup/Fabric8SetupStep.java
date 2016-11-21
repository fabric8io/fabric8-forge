/**
 * Copyright 2005-2015 Red Hat, Inc.
 * <p>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.forge.devops.setup;

import io.fabric8.forge.addon.utils.MavenHelpers;
import io.fabric8.forge.addon.utils.StopWatch;
import io.fabric8.forge.addon.utils.VersionHelper;
import io.fabric8.forge.addon.utils.validator.ClassNameOrMavenPropertyValidator;
import io.fabric8.forge.devops.NewIntegrationTestClassCommand;
import io.fabric8.utils.Objects;
import io.fabric8.utils.Strings;
import org.apache.maven.model.Build;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.Site;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.dependencies.builder.DependencyBuilder;
import org.jboss.forge.addon.facets.FacetFactory;
import org.jboss.forge.addon.facets.constraints.FacetConstraint;
import org.jboss.forge.addon.maven.plugins.Configuration;
import org.jboss.forge.addon.maven.plugins.ConfigurationElement;
import org.jboss.forge.addon.maven.plugins.ExecutionBuilder;
import org.jboss.forge.addon.maven.plugins.MavenPlugin;
import org.jboss.forge.addon.maven.plugins.MavenPluginBuilder;
import org.jboss.forge.addon.maven.projects.MavenFacet;
import org.jboss.forge.addon.maven.projects.MavenPluginFacet;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.projects.facets.ResourcesFacet;
import org.jboss.forge.addon.resource.ResourceFactory;
import org.jboss.forge.addon.templates.TemplateFactory;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.UICompleter;
import org.jboss.forge.addon.ui.input.UIInput;
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
import org.jboss.forge.addon.ui.wizard.UIWizardStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.fabric8.forge.addon.utils.MavenHelpers.getVersion;
import static io.fabric8.forge.devops.setup.DockerSetupHelper.getDockerFromImage;
import static io.fabric8.forge.devops.setup.DockerSetupHelper.hasSpringBoot;
import static io.fabric8.forge.devops.setup.DockerSetupHelper.hasSpringBootWeb;
import static io.fabric8.forge.devops.setup.DockerSetupHelper.hasWildlySwarm;
import static io.fabric8.forge.devops.setup.DockerSetupHelper.setupDocker;
import static io.fabric8.forge.devops.setup.SetupProjectHelper.isFunktionParentPom;

@FacetConstraint({MavenFacet.class, MavenPluginFacet.class, ResourcesFacet.class})
public class Fabric8SetupStep extends AbstractFabricProjectCommand implements UIWizardStep {
    private static final transient Logger LOG = LoggerFactory.getLogger(Fabric8SetupStep.class);

    public static final String EXTENSION_DAV_GROUP_ID = "org.apache.maven.wagon";
    public static final String EXTENSION_DAV_ARTIFACT_ID = "wagon-webdav-jackrabbit";
    public static final String EXTENSION_DAV_VERSION =
            getVersion(EXTENSION_DAV_GROUP_ID, EXTENSION_DAV_ARTIFACT_ID, "2.10");

    public static final String PLUGIN_JAVADOC_GROUP_ID = "org.apache.maven.plugins";
    public static final String PLUGIN_JAVADOC_ARTIFACT_ID = "maven-javadoc-plugin";
    public static final String PLUGIN_JAVADOC_VERSION =
            getVersion(PLUGIN_JAVADOC_GROUP_ID, PLUGIN_JAVADOC_ARTIFACT_ID, "2.10.3");

    private String[] jarImages = new String[]{};
    private String[] bundleImages = new String[]{DockerSetupHelper.DEFAULT_KARAF_IMAGE};
    private String[] warImages = new String[]{DockerSetupHelper.DEFAULT_TOMCAT_IMAGE, DockerSetupHelper.DEFAULT_WILDFLY_IMAGE};

    @Inject
    @WithAttributes(label = "Docker Image From", required = false, description = "The Docker image to use as base line")
    private UIInput<String> from;

    @Inject
    @WithAttributes(label = "Main class", required = false, description = "Main class to use for Java standalone")
    private UIInput<String> main;

    @Inject
    @WithAttributes(label = "Integration Test", required = false, defaultValue = "true", description = "Whether to create Kubernetes integration test")
    private UIInput<Boolean> integrationTest;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Inject
    private TemplateFactory factory;

    @Inject
    ResourceFactory resourceFactory;

    @Inject
    FacetFactory facetFactory;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(Fabric8SetupStep.class).name(
                "Fabric8: Setup").category(Categories.create(CATEGORY))
                .description("Configure the Fabric8 and Docker options for the project");
    }

    @Override
    public boolean isEnabled(UIContext context) {
        // this is a step in a wizard, you cannot run this standalone
        return false;
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        Boolean value = integrationTest.getValue();
        if (value != null && value) {
            return NavigationResultBuilder.create().add(NewIntegrationTestClassCommand.class).build();
        }
        return null;
    }

    @Override
    public void initializeUI(final UIBuilder builder) throws Exception {
        StopWatch watch = new StopWatch();

        final Project project = getSelectedProject(builder.getUIContext());

        String packaging = getProjectPackaging(project);
        boolean springBoot = hasSpringBoot(project);
        boolean wildflySwarm = hasWildlySwarm(project);

        // limit the choices depending on the project packaging
        final List<String> choices = new ArrayList<String>();
        if (packaging == null || springBoot || wildflySwarm || "jar".equals(packaging)) {
            String currentImage = getDockerFromImage(project);
            if (currentImage != null) {
                choices.add(currentImage);
            } else {
                choices.addAll(Arrays.asList(jarImages));
            }
        }
        if (packaging == null || "bundle".equals(packaging)) {
            choices.add(bundleImages[0]);
        }
        if ((!springBoot && !wildflySwarm) && (packaging == null || "war".equals(packaging))) {
            choices.addAll(Arrays.asList(warImages));
        }
        from.setCompleter(new UICompleter<String>() {
            @Override
            public Iterable<String> getCompletionProposals(UIContext context, InputComponent<?, String> input, String value) {
                return choices;
            }
        });

        // is it possible to pre select a choice?
        if (choices.size() > 0) {
            String defaultChoice = choices.get(0);
            if (defaultChoice != null) {
                from.setDefaultValue(defaultChoice);
            }
        }

        from.addValueChangeListener(new ValueChangeListener() {
            @Override
            public void valueChanged(ValueChangeEvent event) {
                // use a listener so the docker step knows what we selected as it want to reuse
                builder.getUIContext().getAttributeMap().put("docker.from", event.getNewValue());
            }
        });
        builder.add(from);

        if (packaging == null || (!packaging.equals("war") && !packaging.equals("ear"))) {
            boolean jarImage = DockerSetupHelper.isJarImage(from.getValue());
            // TODO until we can detect reliably executable JARS versus mains lets not make this mandatory
/*
            main.setRequired(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return jarImage;
                }
            });
*/
            // only enable main if its required
            // TODO we could disable if we knew this was an executable jar
            main.setEnabled(jarImage);
            if (project != null) {
                main.setDefaultValue(DockerSetupHelper.defaultMainClass(project));
            }
            main.addValidator(new ClassNameOrMavenPropertyValidator(true));
            main.addValueChangeListener(new ValueChangeListener() {
                @Override
                public void valueChanged(ValueChangeEvent event) {
                    // use a listener so the docker step knows what we selected as it want to reuse
                    builder.getUIContext().getAttributeMap().put("docker.main", event.getNewValue());
                }
            });
            builder.add(main);
        }

        builder.add(integrationTest);
        log.info("initializeUI took " + watch.taken());
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        StopWatch watch = new StopWatch();
        log.debug("Starting to setup fabric8 project");

        Project project = getSelectedProject(context.getUIContext());
        if (project == null) {
            return Results.fail("No pom.xml available so cannot edit the project!");
        }

        // setup fabric8-maven-plugin
        setupFabricMavenPlugin(project);
        log.debug("fabric8-maven-plugin now setup");

        setupDocker(project, from.getValue(), main.getValue());
        log.debug("docker configuration now setup");

        MavenFacet maven = project.getFacet(MavenFacet.class);
        Model pom = maven.getModel();

        // make sure we have resources as we need it later
        facetFactory.install(project, ResourcesFacet.class);

        log.debug("setting up fabric8 properties");
        setupFabricProperties(project, maven);

        String msg = "Added Fabric8 Maven support";
        log.info("execute took " + watch.taken());
        return Results.success(msg);
    }

    private void importFabricBom(Project project, Model pom) {
        if (!MavenHelpers.hasManagedDependency(pom, "io.fabric8", "fabric8-project")) {
            Dependency bom = DependencyBuilder.create()
                    .setCoordinate(MavenHelpers.createCoordinate("io.fabric8", "fabric8-project", VersionHelper.fabric8Version(), "pom"))
                    .setScopeType("import");
            dependencyInstaller.installManaged(project, bom);
        }
    }

    public static MavenPluginBuilder setupFabricMavenPlugin(Project project) {
        MavenPluginBuilder pluginBuilder;
        MavenPlugin plugin = MavenHelpers.findPlugin(project, "io.fabric8", "fabric8-maven-plugin");
        if (plugin != null) {
            // if there is an existing then leave it as-is
            LOG.info("Found existing fabric8-maven-plugin");
            pluginBuilder = null;
        } else {
            LOG.info("Adding fabric8-maven-plugin");
            // add fabric8 plugin
            pluginBuilder = MavenPluginBuilder.create()
                    .setCoordinate(MavenHelpers.createCoordinate("io.fabric8", "fabric8-maven-plugin", VersionHelper.fabric8MavenPluginVersion()))
                    .addExecution(ExecutionBuilder.create().addGoal("resource").addGoal("build").addGoal("helm"));
        }

        if (pluginBuilder != null) {
            MavenPluginFacet pluginFacet = project.getFacet(MavenPluginFacet.class);
            pluginFacet.addPlugin(pluginBuilder);
        }
        return pluginBuilder;
    }

    public static void setupSitePlugin(Project project) {
        StopWatch watch = new StopWatch();
        if (project != null) {
            MavenFacet mavenFacet = project.getFacet(MavenFacet.class);
            if (mavenFacet != null) {
                Model model = mavenFacet.getModel();
                if (model != null) {
                    Build build = model.getBuild();
                    if (build == null) {
                        build = new Build();
                        model.setBuild(build);
                    }
                    List<Extension> extensions = build.getExtensions();
                    if (extensions == null) {
                        extensions = new ArrayList<>();
                    }

                    // lets check if we have a wagon extension
                    boolean found = false;
                    for (Extension extension : extensions) {
                        String artifactId = extension.getArtifactId();
                        String groupId = extension.getGroupId();
                        if (Objects.equal(artifactId, EXTENSION_DAV_ARTIFACT_ID) &&
                                Objects.equal(groupId, EXTENSION_DAV_GROUP_ID)) {
                            found = true;
                            break;
                        }
                    }
                    boolean changed = false;
                    if (!found) {
                        Extension extension = new Extension();
                        extension.setGroupId(EXTENSION_DAV_GROUP_ID);
                        extension.setArtifactId(EXTENSION_DAV_ARTIFACT_ID);
                        extension.setVersion(EXTENSION_DAV_VERSION);
                        extensions.add(extension);
                        build.setExtensions(extensions);
                        changed = true;
                    }

                    // lets add any missing reports
                    Reporting reporting = model.getReporting();
                    if (reporting == null) {
                        reporting = new Reporting();
                        model.setReporting(reporting);
                    }
                    Map<String, ReportPlugin> reportPluginsAsMap = reporting.getReportPluginsAsMap();

                    // TODO should we check if the project has no java maybe? I guess its not a biggie if there isn't
                    if (!reportPluginsAsMap.containsKey(PLUGIN_JAVADOC_GROUP_ID + ":" + PLUGIN_JAVADOC_ARTIFACT_ID)) {
                        ReportPlugin reportPlugin = new ReportPlugin();
                        reportPlugin.setGroupId(PLUGIN_JAVADOC_GROUP_ID);
                        reportPlugin.setArtifactId(PLUGIN_JAVADOC_ARTIFACT_ID);
                        reportPlugin.setVersion(PLUGIN_JAVADOC_VERSION);
                        reporting.addPlugin(reportPlugin);

                        // lets set configuration
                        Xpp3Dom config = new Xpp3Dom("configuration");
                        addChildElement(config, "detectLinks", "true");
                        addChildElement(config, "detectJavaApiLink", "true");
                        addChildElement(config, "linksource", "true");

                        reportPlugin.setConfiguration(config);
                        changed = true;
                    }

                    // lets ensure there's a site distribution
                    DistributionManagement distributionManagement = model.getDistributionManagement();
                    if (distributionManagement == null) {
                        Parent parent = model.getParent();
                        if (parent == null || isFunktionParentPom(project)) {
                            // lets only add a distributionManagement if there is no parent
                            // as usually we add the distributionManagement in a parent pom once to reuse across projects
                            distributionManagement = new DistributionManagement();
                            model.setDistributionManagement(distributionManagement);
                        }
                    }
                    if (distributionManagement != null) {
                        Site site = distributionManagement.getSite();
                        if (site == null) {
                            site = new Site();
                            distributionManagement.setSite(site);
                        }
                        String siteId = site.getId();
                        if (Strings.isNullOrBlank(siteId)) {
                            site.setId("website");
                            changed = true;
                        }
                        String siteUrl = site.getUrl();
                        if (Strings.isNullOrBlank(siteUrl)) {
                            site.setUrl("dav:http://content-repository/sites/${project.groupId}/${project.artifactId}/${project.version}");
                            changed = true;
                        }
                    }

                    if (changed) {
                        mavenFacet.setModel(model);
                    }
                }
            }
        }
        LOG.info("setupSitePlugin took " + watch.taken());
    }

    public static void addChildElement(Xpp3Dom config, String name, String value) {
        Xpp3Dom includeDependencySources = new Xpp3Dom(name);
        includeDependencySources.setValue(value);
        config.addChild(includeDependencySources);
    }

    private void setupFabricProperties(Project project, MavenFacet maven) {
        String servicePort = getDefaultServicePort(project);
        if (servicePort != null && hasSpringBoot(project)) {
            MavenHelpers.ensureMavenDependencyAdded(project, dependencyInstaller, "org.springframework.boot", "spring-boot-starter-actuator", null);
        }
    }


    /**
     * Try to determine the default service port.
     *
     * If this is a WAR, EAR or spring-boot then lets assume 8080.
     *
     * For Karaf we cannot assume its 8181 as web is not installed by default
     * and there is no default index html on the port to use etc
     */
    protected static String getDefaultServicePort(Project project) {
        if (hasWildlySwarm(project)) {
            // lets find the swarm plugin
            MavenPlugin plugin = MavenHelpers.findPlugin(project, "org.wildfly.swarm", "wildfly-swarm-plugin");
            if (plugin != null) {
                Configuration config = plugin.getConfig();
                if (config != null) {
                    ConfigurationElement properties = config.getConfigurationElement("properties");
                    if (properties != null) {
                        ConfigurationElement portElement = properties.getChildByName("swarm.http.port");
                        if (portElement != null) {
                            String text = portElement.getText();
                            if (Strings.isNotBlank(text)) {
                                return text;
                            }
                        }
                    }
                }
            }
        }
        String packaging = getProjectPackaging(project);
        if (Strings.isNotBlank(packaging)) {
            if (Objects.equal("war", packaging) || Objects.equal("ear", packaging)) {
                return "8080";
            }
        }
        boolean springBoot = hasSpringBootWeb(project);
        if (springBoot) {
            return "8080";
        }
        return null;
    }

    private static String asContainer(String fromImage) {
        int idx = fromImage.indexOf('/');
        if (idx > 0) {
            fromImage = fromImage.substring(idx + 1);
        }
        idx = fromImage.indexOf('-');
        if (idx > 0) {
            fromImage = fromImage.substring(0, idx);
        }
        return fromImage;
    }

    private static String getProjectPackaging(Project project) {
        if (project != null) {
            MavenFacet maven = project.getFacet(MavenFacet.class);
            return maven.getModel().getPackaging();
        }
        return null;
    }
}

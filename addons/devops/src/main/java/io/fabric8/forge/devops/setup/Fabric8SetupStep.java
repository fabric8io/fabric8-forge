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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import io.fabric8.forge.addon.utils.MavenHelpers;
import io.fabric8.forge.addon.utils.StopWatch;
import io.fabric8.forge.addon.utils.VersionHelper;
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
import org.jboss.forge.addon.facets.FacetFactory;
import org.jboss.forge.addon.facets.constraints.FacetConstraint;
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
import org.jboss.forge.addon.ui.input.UIInput;
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

import static io.fabric8.forge.addon.utils.MavenHelpers.getVersion;
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
            getVersion(PLUGIN_JAVADOC_GROUP_ID, PLUGIN_JAVADOC_ARTIFACT_ID, "2.10.4");

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
                .description("Configure the Fabric8 options for the project");
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
        builder.add(integrationTest);
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

        // make sure we have resources as we need it later
        facetFactory.install(project, ResourcesFacet.class);

        log.info("execute took " + watch.taken());
        return Results.success();
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
                    .addExecution(ExecutionBuilder.create().setId("fmp").addGoal("resource").addGoal("helm").addGoal("build"));
        }

        if (pluginBuilder != null) {
            MavenPluginFacet pluginFacet = project.getFacet(MavenPluginFacet.class);
            pluginFacet.addPlugin(pluginBuilder);
        }
        return pluginBuilder;
    }

    @Deprecated
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

}

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
package io.fabric8.forge.devops;

import io.fabric8.devops.ProjectConfig;
import io.fabric8.devops.ProjectConfigs;
import io.fabric8.forge.addon.utils.CommandHelpers;
import io.fabric8.forge.addon.utils.ProfilesProjectHelper;
import io.fabric8.forge.devops.dto.PipelineDTO;
import io.fabric8.forge.devops.dto.ProjectOverviewDTO;
import io.fabric8.kubernetes.api.Controller;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.utils.Files;
import io.fabric8.utils.GitHelpers;
import io.fabric8.utils.Objects;
import io.fabric8.utils.Strings;
import io.fabric8.utils.TablePrinter;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.jboss.forge.addon.maven.projects.MavenFacet;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.projects.Projects;
import org.jboss.forge.addon.projects.ui.AbstractProjectCommand;
import org.jboss.forge.addon.resource.Resource;
import org.jboss.forge.addon.resource.util.ResourceUtil;
import org.jboss.forge.addon.ui.UIProvider;
import org.jboss.forge.addon.ui.command.UICommand;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIContextProvider;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UISelection;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.output.UIOutput;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.fabric8.forge.addon.utils.CamelProjectHelper.findCamelCoreDependency;
import static io.fabric8.forge.addon.utils.CamelProjectHelper.hasFunktionDependency;

/**
 * An abstract base class for DevOps related commands
 */
public abstract class AbstractDevOpsCommand extends AbstractProjectCommand implements UICommand {
    private static final transient Logger LOG = LoggerFactory.getLogger(AbstractDevOpsCommand.class);
    public static final int ROOT_LEVEL = 1;

    public static String CATEGORY = "DevOps";

    private KubernetesClient kubernetes;

    @Inject
    private ProjectFactory projectFactory;

    /*
        @Inject
    */
    UIProvider uiProvider;

    @Inject
    @WithAttributes(name = "kubernetesUrl", label = "The URL where the kubernetes master is running")
    UIInput<String> kubernetesUrl;

    @Override
    protected boolean isProjectRequired() {
        return false;
    }

    @Override
    protected ProjectFactory getProjectFactory() {
        return projectFactory;
    }

    public KubernetesClient getKubernetes() {
        if (kubernetes == null) {
            String kubernetesAddress = kubernetesUrl.getValue();
            if (Strings.isNotBlank(kubernetesAddress)) {
                kubernetes = new DefaultKubernetesClient(new ConfigBuilder().withMasterUrl(kubernetesAddress).build());
            } else {
                kubernetes = new DefaultKubernetesClient();
            }
        }
        Objects.notNull(kubernetes, "kubernetes");
        return kubernetes;
    }

    public Controller createController() {
        Controller controller = new Controller(getKubernetes());
        controller.setThrowExceptionOnError(true);
        return controller;
    }

    public void setKubernetes(KubernetesClient kubernetes) {
        this.kubernetes = kubernetes;
    }

    public boolean isGUI() {
        return getUiProvider().isGUI();
    }

    public UIOutput getOutput() {
        UIProvider provider = getUiProvider();
        return provider != null ? provider.getOutput() : null;
    }

    public UIProvider getUiProvider() {
        return uiProvider;
    }

    public void setUiProvider(UIProvider uiProvider) {
        this.uiProvider = uiProvider;
    }

    @Override
    public void initializeUI(UIBuilder uiBuilder) throws Exception {
    }

    public Project getCurrentSelectedProject(UIContext context) {
        Project project = null;
        Map<Object, Object> attributeMap = context.getAttributeMap();
        if (attributeMap != null) {
            Object object = attributeMap.get(Project.class);
            if (object instanceof Project) {
                project = (Project) object;
                return project;
            }
        }
        UISelection<Object> selection = context.getSelection();
        Object selectedObject = selection.get();
        try {
            LOG.info("START getCurrentSelectedProject: on " + getProjectFactory() + " selection: " + selectedObject + ". This may result in mvn artifacts being downloaded to ~/.m2/repository");
            project = Projects.getSelectedProject(getProjectFactory(), context);
            if (project != null && attributeMap != null) {
                attributeMap.put(Project.class, project);
            }
            return project;
        } finally {
            LOG.info("END   getCurrentSelectedProject: on " + getProjectFactory() + " selection: " + selectedObject);
        }
    }

    public static File getProjectConfigFile(UIContext context, Project project) {
        return CommandHelpers.getProjectContextFile(context, project, ProjectConfigs.FILE_NAME);
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        return Results.success();
    }

    /**
     * Prints the given table and returns success
     */
    protected Result tableResults(TablePrinter table) {
        table.print(getOut());
        return Results.success();
    }

    public PrintStream getOut() {
        UIOutput output = getOutput();
        if (output != null) {
            return output.out();
        } else {
            return System.out;
        }
    }

    public MavenFacet getMavenFacet(UIContextProvider builder) {
        final Project project = getSelectedProject(builder);
        if (project != null) {
            MavenFacet maven = project.getFacet(MavenFacet.class);
            return maven;
        }
        return null;
    }

    public Model getMavenModel(UIContextProvider builder) {
        MavenFacet mavenFacet = getMavenFacet(builder);
        if (mavenFacet != null) {
            return mavenFacet.getModel();
        }
        return null;
    }

    protected String getOrFindGitUrl(UIExecutionContext context, String gitUrlText) {
        if (Strings.isNullOrBlank(gitUrlText)) {
            final Project project = getSelectedProject(context);
            if (project != null) {
                Resource<?> root = project.getRoot();
                if (root != null) {
                    try {
                        Resource<?> gitFolder = root.getChild(".git");
                        if (gitFolder != null) {
                            Resource<?> config = gitFolder.getChild("config");
                            if (config != null) {
                                String configText = config.getContents();
                                gitUrlText = GitHelpers.extractGitUrl(configText);
                            }
                        }
                    } catch (Exception e) {
                        LOG.debug("Ignoring missing git folders: " + e, e);
                    }
                }
            }
        }
        if (Strings.isNullOrBlank(gitUrlText)) {
            Model mavenModel = getMavenModel(context);
            if (mavenModel != null) {
                Scm scm = mavenModel.getScm();
                if (scm != null) {
                    String connection = scm.getConnection();
                    if (Strings.isNotBlank(connection)) {
                        gitUrlText = connection;
                    }
                }
            }
        }
        if (Strings.isNullOrBlank(gitUrlText)) {
            throw new IllegalArgumentException("Could not find git URL");
        }
        return gitUrlText;
    }

    protected void updateConfiguration(UIExecutionContext context, ProjectConfig config) {
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();
        ProjectConfigs.configureProperties(config, attributeMap);
        Object pipelineValue = attributeMap.get("pipeline");
        if (pipelineValue instanceof PipelineDTO) {
            PipelineDTO pipeline = (PipelineDTO) pipelineValue;
            if (pipeline != null) {
                config.setPipeline(pipeline.getValue());
            }
        }
    }

    protected ProjectOverviewDTO getProjectOverview(UIContext uiContext) {
        ProjectOverviewDTO projectOveriew = new ProjectOverviewDTO();
        File rootFolder = getSelectionFolder(uiContext);
        if (rootFolder != null) {
            List<GetOverviewCommand.FileProcessor> processors = loadFileMatches();
            scanProject(rootFolder, processors, projectOveriew, 0, 3);
        }
        if (hasProjectFile(uiContext, "pom.xml")) {
            projectOveriew.addBuilder("maven");
            projectOveriew.addPerspective("forge");

            if (containsProject(uiContext)) {
                Project project = getSelectedProject(uiContext);
                if (findCamelCoreDependency(project) != null) {
                    if (hasFunktionDependency(project)) {
                        projectOveriew.addPerspective("funktion");
                    } else {
                    }
                    // TOD should we show funktion instead of camel?
                    projectOveriew.addPerspective("camel");
                }
                if (ProfilesProjectHelper.isProfilesProject(project)) {
                    projectOveriew.addPerspective("fabric8-profiles");
                }
            }
        }
        return projectOveriew;
    }

    protected List<GetOverviewCommand.FileProcessor> loadFileMatches() {
        List<GetOverviewCommand.FileProcessor> answer = new ArrayList<>();
        answer.add(new GetOverviewCommand.FileProcessor() {
                       @Override
                       public boolean processes(ProjectOverviewDTO overview, File file, String name, String extension, int level) {
                           if (level == ROOT_LEVEL && java.util.Objects.equals(name, "Jenkinsfile")) {
                               overview.addBuilder("jenkinsfile");
                               return true;
                           }
                           return false;
                       }
                   }
        );
        answer.add(new GetOverviewCommand.FileProcessor() {
                       @Override
                       public boolean processes(ProjectOverviewDTO overview, File file, String name, String extension, int level) {
                           if ((level == ROOT_LEVEL && java.util.Objects.equals(name, "package.json")) || java.util.Objects.equals(extension, "js")) {
                               overview.addBuilder("node");
                               return true;
                           }
                           return false;
                       }
                   }
        );
        answer.add(new GetOverviewCommand.FileProcessor() {
                       @Override
                       public boolean processes(ProjectOverviewDTO overview, File file, String name, String extension, int level) {
                           if (java.util.Objects.equals(extension, "go")) {
                               overview.addBuilder("golang");
                               return true;
                           }
                           return false;
                       }
                   }
        );
        answer.add(new GetOverviewCommand.FileProcessor() {
                       @Override
                       public boolean processes(ProjectOverviewDTO overview, File file, String name, String extension, int level) {
                           if (java.util.Objects.equals(name, "Rakefile") || java.util.Objects.equals(extension, "rb")) {
                               overview.addBuilder("ruby");
                               return true;
                           }
                           return false;
                       }
                   }
        );
        answer.add(new GetOverviewCommand.FileProcessor() {
                       @Override
                       public boolean processes(ProjectOverviewDTO overview, File file, String name, String extension, int level) {
                           if (java.util.Objects.equals(extension, "swift")) {
                               overview.addBuilder("swift");
                               return true;
                           }
                           return false;
                       }
                   }
        );
        answer.add(new GetOverviewCommand.FileProcessor() {
                       @Override
                       public boolean processes(ProjectOverviewDTO overview, File file, String name, String extension, int level) {
                           if (java.util.Objects.equals(name, "urls.py") || java.util.Objects.equals(extension, "wsgi.py")) {
                               overview.addBuilder("django");
                               return true;
                           }
                           return false;
                       }
                   }
        );
        return answer;
    }

    protected void scanProject(File file, List<GetOverviewCommand.FileProcessor> processors, ProjectOverviewDTO overview, int level, int maxLevels) {
        if (file.isFile()) {
            String name = file.getName();
            String extension = Files.getExtension(name);
            for (GetOverviewCommand.FileProcessor processor : new ArrayList<>(processors)) {
                if (processor.processes(overview, file, name, extension, level)) {
                    processors.remove(processor);
                }
            }
        } else if (file.isDirectory()) {
            int newLevel = level + 1;
            if (newLevel <= maxLevels && !processors.isEmpty()) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File child : files) {
                        scanProject(child, processors, overview, newLevel, maxLevels);
                    }
                }
            }
        }
    }

    protected boolean hasProjectFile(UIContext context, String fileName) {
        UISelection<Object> selection = context.getSelection();
        if (selection != null) {
            Object object = selection.get();
            if (object instanceof Resource) {
                File folder = ResourceUtil.getContextFile((Resource<?>) object);
                if (folder != null && Files.isDirectory(folder)) {
                    File file = new File(folder, fileName);
                    return file != null && file.exists() && file.isFile();
                }
            }
        }
        return false;
    }

    protected File getSelectionFolder(UIContext context) {
        UISelection<Object> selection = context.getSelection();
        if (selection != null) {
            Object object = selection.get();
            if (object instanceof Resource) {
                File folder = ResourceUtil.getContextFile((Resource<?>) object);
                if (folder != null && Files.isDirectory(folder)) {
                    return folder;
                }
            }
        }
        return null;
    }

    protected interface FileProcessor {
        boolean processes(ProjectOverviewDTO overview, File file, String name, String extension, int level);
    }
}

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
import io.fabric8.devops.connector.DevOpsConnector;
import io.fabric8.forge.addon.utils.CommandHelpers;
import io.fabric8.forge.addon.utils.MavenHelpers;
import io.fabric8.forge.devops.dto.PipelineDTO;
import io.fabric8.forge.devops.dto.PipelineMetadata;
import io.fabric8.forge.devops.dto.ProjectOverviewDTO;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.letschat.LetsChatClient;
import io.fabric8.letschat.LetsChatKubernetes;
import io.fabric8.letschat.RoomDTO;
import io.fabric8.taiga.ProjectDTO;
import io.fabric8.taiga.TaigaClient;
import io.fabric8.taiga.TaigaKubernetes;
import io.fabric8.utils.Files;
import io.fabric8.utils.Filter;
import io.fabric8.utils.GitHelpers;
import io.fabric8.utils.IOHelpers;
import io.fabric8.utils.Objects;
import io.fabric8.utils.Strings;
import org.apache.maven.model.Model;
import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.maven.projects.MavenFacet;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.facets.MetadataFacet;
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
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.wizard.UIWizardStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static io.fabric8.forge.devops.setup.SetupProjectHelper.isFabric8MavenPlugin3OrGreater;
import static io.fabric8.forge.devops.setup.SetupProjectHelper.isFunktionParentPom;
import static io.fabric8.kubernetes.api.KubernetesHelper.loadYaml;

public class DevOpsEditStep extends AbstractDevOpsCommand implements UIWizardStep {
    private static final transient Logger LOG = LoggerFactory.getLogger(DevOpsEditStep.class);
    private static final String DEFAULT_MAVEN_FLOW = "workflows/maven/CanaryReleaseStageAndApprovePromote.groovy";
    private static final boolean copyPipelineToProject = true;

    public static final String JENKINSFILE = "Jenkinsfile";

    @Inject
    @WithAttributes(label = "Pipeline", required = false, description = "The Jenkinsfile used to define the Continous Delivery pipeline")
    private UIInput<PipelineDTO> pipeline;

    @Inject
    @WithAttributes(label = "Chat room", required = false, description = "Name of chat room to use for this project")
    private UIInput<String> chatRoom;

    @Inject
    @WithAttributes(label = "IssueTracker Project name", required = false, description = "Name of the issue tracker project")
    private UIInput<String> issueProjectName;

    @Inject
    @WithAttributes(label = "Code review", required = false, description = "Enable code review of all commits")
    private UIInput<Boolean> codeReview;

    private String namespace = KubernetesHelper.defaultNamespace();
    private LetsChatClient letsChat;
    private TaigaClient taiga;
    private List<InputComponent> inputComponents;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(getClass())
                .category(Categories.create(AbstractDevOpsCommand.CATEGORY))
                .name(AbstractDevOpsCommand.CATEGORY + ": Configure")
                .description("Configure the Pipeline for the new project");
    }


    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        final UIContext context = builder.getUIContext();
        pipeline.setCompleter(new UICompleter<PipelineDTO>() {
            @Override
            public Iterable<PipelineDTO> getCompletionProposals(UIContext context, InputComponent<?, PipelineDTO> input, String value) {
                return getPipelines(context, true);
            }
        });
        pipeline.setValueConverter(new Converter<String, PipelineDTO>() {
            @Override
            public PipelineDTO convert(String text) {
                return getPipelineForValue(context, text);
            }
        });
        pipeline.addValueChangeListener(new ValueChangeListener() {
            @Override
            public void valueChanged(ValueChangeEvent event) {
                String value = event.getNewValue() != null ? event.getNewValue().toString() : null;
                if (value != null) {
                    String description = getDescriptionForFlow(value);
                    pipeline.setNote(description != null ? description : "");
                } else {
                    pipeline.setNote("");
                }
            }
        });
        if (getCurrentSelectedProject(context) != null) {
            PipelineDTO defaultValue = getPipelineForValue(context, DEFAULT_MAVEN_FLOW);
            if (defaultValue != null) {
                pipeline.setDefaultValue(defaultValue);
                pipeline.setValue(defaultValue);
            }
        }
        chatRoom.setCompleter(new UICompleter<String>() {
            @Override
            public Iterable<String> getCompletionProposals(UIContext context, InputComponent<?, String> input, String value) {
                return filterCompletions(getChatRoomNames(), value);
            }
        });
        chatRoom.addValueChangeListener(new ValueChangeListener() {
            @Override
            public void valueChanged(ValueChangeEvent event) {
                String value = event.getNewValue() != null ? event.getNewValue().toString() : null;
                if (value != null) {
                    String description = getDescriptionForChatRoom(value);
                    chatRoom.setNote(description != null ? description : "");
                } else {
                    chatRoom.setNote("");
                }
            }
        });
        issueProjectName.setCompleter(new UICompleter<String>() {
            @Override
            public Iterable<String> getCompletionProposals(UIContext context, InputComponent<?, String> input, String value) {
                return filterCompletions(getIssueProjectNames(), value);
            }
        });
        issueProjectName.addValueChangeListener(new ValueChangeListener() {
            @Override
            public void valueChanged(ValueChangeEvent event) {
                String value = event.getNewValue() != null ? event.getNewValue().toString() : null;
                if (value != null) {
                    String description = getDescriptionForIssueProject(value);
                    issueProjectName.setNote(description != null ? description : "");
                } else {
                    issueProjectName.setNote("");
                }
            }
        });

        // lets initialise the data from the current config if it exists
        ProjectConfig config = null;
        Project project = getCurrentSelectedProject(context);
        File configFile = getProjectConfigFile(context, getSelectedProject(context));
        if (configFile != null && configFile.exists()) {
            config = ProjectConfigs.parseProjectConfig(configFile);
        }
        if (config != null) {
            PipelineDTO flow = getPipelineForValue(context, config.getPipeline());
            if (flow != null) {
                CommandHelpers.setInitialComponentValue(this.pipeline, flow);
            }
            CommandHelpers.setInitialComponentValue(chatRoom, config.getChatRoom());
            CommandHelpers.setInitialComponentValue(issueProjectName, config.getIssueProjectName());
            CommandHelpers.setInitialComponentValue(codeReview, config.getCodeReview());
        }
        inputComponents = new ArrayList<>();
        File jenkinsFile = CommandHelpers.getProjectContextFile(context, project, "Jenkinsfile");
        boolean hasJenkinsFile = Files.isFile(jenkinsFile);
        LOG.debug("Has Jenkinsfile " + hasJenkinsFile + " with file: " + jenkinsFile);
        if (!hasJenkinsFile) {
            inputComponents.addAll(CommandHelpers.addInputComponents(builder, pipeline));
        }
        inputComponents.addAll(CommandHelpers.addInputComponents(builder, chatRoom, issueProjectName, codeReview));
    }

    public static Iterable<String> filterCompletions(Iterable<String> values, String inputValue) {
        boolean ignoreFilteringAsItBreaksHawtio = true;
        if (ignoreFilteringAsItBreaksHawtio) {
            return values;
        } else {
            List<String> answer = new ArrayList<>();
            String lowerInputValue = inputValue.toLowerCase();
            for (String value : values) {
                if (value != null) {
                    if (value.toLowerCase().contains(lowerInputValue)) {
                        answer.add(value);
                    }
                }
            }
            return answer;
        }
    }


    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        return null;
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        LOG.info("Creating the fabric8.yml file");

        String fileName = ProjectConfigs.FILE_NAME;
        Project project = getSelectedProject(context);
        File configFile = getProjectConfigFile(context.getUIContext(), getSelectedProject(context));
        if (configFile == null) {
            // lets not fail as we typically want to execute SaveDevOpsStep next...
            return Results.success();
        }
        ProjectConfig config = null;
        boolean hasFile = false;
        if (configFile.exists()) {
            config = ProjectConfigs.parseProjectConfig(configFile);
            hasFile = true;
        }
        if (config == null) {
            config = new ProjectConfig();
        }

        CommandHelpers.putComponentValuesInAttributeMap(context, inputComponents);
        updateConfiguration(context, config);
        LOG.info("Result: " + config);

        String message;
        if (config.isEmpty() && !hasFile) {
            message = "No " + fileName + " need be generated as there is no configuration";
            return Results.success(message);
        } else {
            String operation = "Updated";
            if (!configFile.exists()) {
                operation = "Created";
            }
            ProjectConfigs.saveConfig(config, configFile);
            message = operation + " " + fileName;
        }

        // now lets update the devops stuff
        UIContext uiContext = context.getUIContext();
        Map<Object, Object> attributeMap = uiContext.getAttributeMap();

        String gitUrl = getStringAttribute(attributeMap, "gitUrl");
        if (Strings.isNullOrBlank(gitUrl)) {
            gitUrl = getStringAttribute(attributeMap, "gitAddress");
        }

        Object object = attributeMap.get(Project.class);
        String user = getStringAttribute(attributeMap, "gitUser");
        String named = getStringAttribute(attributeMap, "projectName");
        ;
        File basedir = CommandHelpers.getBaseDir(project);
        if (basedir == null && configFile != null) {
            basedir = configFile.getParentFile();
        }

        if (object instanceof Project) {
            Project newProject = (Project) object;
            MetadataFacet facet = newProject.getFacet(MetadataFacet.class);
            if (facet != null) {
                if (Strings.isNullOrBlank(named)) {
                    named = facet.getProjectName();
                }
                if (Strings.isNullOrBlank(gitUrl)) {
                    String address = getStringAttribute(attributeMap, "gitAddress");
                    gitUrl = address + user + "/" + named + ".git";
                }
            } else {
                LOG.error("No MetadataFacet for newly created project " + newProject);
            }
        } else {
            // updating an existing project - so lets try find the git url from the current source code
            if (Strings.isNullOrBlank(gitUrl)) {
                gitUrl = GitHelpers.extractGitUrl(basedir);
            }
            if (basedir != null) {
                if (Strings.isNullOrBlank(named)) {
                    named = basedir.getName();
                }
            }
        }
        // lets default the environments from the pipeline
        PipelineDTO pipelineValue = pipeline.getValue();
        LOG.info("Using pipeline " + pipelineValue);
        String buildName = config.getBuildName();
        if (Strings.isNotBlank(buildName)) {
            if (pipelineValue != null) {
                List<String> environments = pipelineValue.getEnvironments();
                if (environments == null) {
                    environments = new ArrayList<>();
                }
                LinkedHashMap<String, String> environmentMap = new LinkedHashMap<>();
                if (environments.isEmpty()) {
                    environmentMap.put("Current", namespace);
                } else {
                    for (String environment : environments) {
                        String envNamespace = namespace + "-" + environment.toLowerCase();
                        environmentMap.put(environment, envNamespace);
                    }
                }
                config.setEnvironments(environmentMap);
            }
        }
        LOG.info("Configured project " + buildName + " environments: " + config.getEnvironments());
        ProjectConfigs.defaultEnvironments(config, namespace);

        String projectName = config.getBuildName();
        if (Strings.isNullOrBlank(projectName)) {
            projectName = named;
            config.setBuildName(projectName);
        }

        LOG.info("Project name is: " + projectName);
        if (Strings.isNotBlank(projectName) && project != null) {
            MavenFacet maven = project.getFacet(MavenFacet.class);
            Model pom = maven.getModel();
            if (pom != null && !isFunktionParentPom(project) && !isFabric8MavenPlugin3OrGreater(project)) {
                Properties properties = pom.getProperties();
                boolean updated = false;
                updated = MavenHelpers.updatePomProperty(properties, "fabric8.label.project", projectName, updated);
                updated = MavenHelpers.updatePomProperty(properties, "fabric8.label.version", "${project.version}", updated);
                if (updated) {
                    LOG.info("Updating pom.xml properties!");
                    maven.setModel(pom);
                } else {
                    LOG.warn("Did not update pom.xml properties!");
                }
            } else {
                LOG.warn("No pom.xml found!");
            }
        }

        if (copyPipelineToProject) {
            if (basedir == null || !basedir.isDirectory()) {
                LOG.warn("Cannot copy the pipeline to the project as no basedir!");
            } else {
                String flow = null;
                PipelineDTO pipelineDTO = pipelineValue;
                if (pipelineDTO != null) {
                    flow = pipelineDTO.getValue();
                }
                if (Strings.isNullOrBlank(flow)) {
                    LOG.warn("Cannot copy the pipeline to the project as no pipeline selected!");
                } else {
                    String flowText = getFlowContent(flow, uiContext);
                    if (Strings.isNullOrBlank(flowText))  {
                        LOG.warn("Cannot copy the pipeline to the project as no pipeline text could be loaded!");
                    } else {
                        flowText = Strings.replaceAllWithoutRegex(flowText, "GIT_URL", "'" + gitUrl + "'");
                        File newFile = new File(basedir, ProjectConfigs.LOCAL_FLOW_FILE_NAME);
                        Files.writeToFile(newFile, flowText.getBytes());
                        LOG.info("Written pipeline to " + newFile);
                        config.setPipeline(null);
                        config.setUseLocalFlow(true);
                    }
                }
            }
        }

        final DevOpsConnector connector = new DevOpsConnector();
        connector.setProjectConfig(config);
        connector.setTryLoadConfigFileFromRemoteGit(false);
        connector.setUsername(user);
        connector.setPassword(getStringAttribute(attributeMap, "gitPassword"));
        connector.setBranch(getStringAttribute(attributeMap, "gitBranch", "master"));
        connector.setBasedir(basedir);
        connector.setGitUrl(gitUrl);
        connector.setRepoName(named);

        connector.setRegisterWebHooks(true);

        // lets not trigger the jenkins webhook yet as the git push should trigger the build
        connector.setTriggerJenkinsJob(false);

        LOG.info("Using connector: " + connector);

/*
        attributeMap.put("registerWebHooks", new Runnable() {
            @Override
            public void run() {
                LOG.info("Now registering webhooks!");
                connector.registerWebHooks();
            }
        });
*/
        try {
            connector.execute();
        } catch (Exception e) {
            LOG.error("Failed to update DevOps resources: " + e, e);
        }

        return Results.success(message);
    }


    protected String getStringAttribute(Map<Object, Object> attributeMap, String name, String defaultValue) {
        String answer = getStringAttribute(attributeMap, name);
        return Strings.isNullOrBlank(answer) ? defaultValue : answer;
    }

    protected String getStringAttribute(Map<Object, Object> attributeMap, String name) {
        Object value = attributeMap.get(name);
        if (value != null) {
            return value.toString();
        }
        return null;
    }

    protected String getDescriptionForFlow(String flow) {
        return null;
    }

    protected String getFlowContent(String flow, UIContext context) {
        File dir = getJenkinsWorkflowFolder(context);
        if (dir != null) {
            File file = new File(dir, flow);
            if (file.isFile() && file.exists()) {
                try {
                    return IOHelpers.readFully(file);
                } catch (IOException e) {
                    LOG.warn("Failed to load local pipeline " + file + ". " + e, e);
                }
            }
        }
        return null;
    }

    protected PipelineDTO getPipelineForValue(UIContext context, String value) {
        if (Strings.isNotBlank(value)) {
            Iterable<PipelineDTO> pipelines = getPipelines(context, false);
            for (PipelineDTO pipelineDTO : pipelines) {
                if (pipelineDTO.getValue().equals(value) || pipelineDTO.toString().equals(value)) {
                    return pipelineDTO;
                }
            }
        }
        return null;
    }

    protected Iterable<PipelineDTO> getPipelines(UIContext context, boolean filterPipelines) {
        Set<String> builders =  null;
        ProjectOverviewDTO projectOveriew;
        if (filterPipelines) {
            projectOveriew = getProjectOverview(context);
            builders = projectOveriew.getBuilders();
        }
        File dir = getJenkinsWorkflowFolder(context);
        Set<String> buildersFound = new HashSet<>();
        if (dir != null) {
            Filter<File> filter = new Filter<File>() {
                @Override
                public boolean matches(File file) {
                    return file.isFile() && Objects.equal(JENKINSFILE, file.getName());
                }
            };
            Set<File> files =  Files.findRecursive(dir, filter);
            List<PipelineDTO> pipelines = new ArrayList<>();
            for (File file : files) {
                try {
                    String relativePath = Files.getRelativePath(dir, file);
                    String value = Strings.stripPrefix(relativePath, "/");
                    String label = value;
                    String postfix = "/" + JENKINSFILE;
                    if (label.endsWith(postfix)) {
                        label = label.substring(0, label.length() - postfix.length());
                    }
                    // Lets ignore the fabric8 specific pipelines
                    if (label.startsWith("fabric8-release/")) {
                        continue;
                    }
                    String builder = null;
                    int idx = label.indexOf("/");
                    if (idx > 0) {
                        builder = label.substring(0, idx);
                        if (filterPipelines && !builders.contains(builder)) {
                            // ignore this builder
                            continue;
                        } else {
                            buildersFound.add(builder);
                        }
                    }
                    String descriptionMarkdown = null;
                    File markdownFile = new File(file.getParentFile(), "ReadMe.md");
                    if (Files.isFile(markdownFile)) {
                        descriptionMarkdown = IOHelpers.readFully(markdownFile);
                    }
                    PipelineDTO pipeline = new PipelineDTO(value, label, builder, descriptionMarkdown);

                    File yamlFile = new File(file.getParentFile(), "metadata.yml");
                    if (Files.isFile(yamlFile)) {
                        PipelineMetadata metadata = null;
                        try {
                            metadata = loadYaml(yamlFile, PipelineMetadata.class);
                        } catch (IOException e) {
                            LOG.warn("Failed to parse yaml file " + yamlFile + ". " + e, e);
                        }
                        if (metadata != null) {
                            metadata.configurePipeline(pipeline);
                        }
                    }
                    pipelines.add(pipeline);
                } catch (IOException e) {
                    LOG.warn("Failed to find relative path for folder " + dir + " and file " + file + ". " + e, e);
                }
            }
            if (buildersFound.size() == 1) {
                // lets trim the builder prefix from the labels
                for (String first : buildersFound) {
                    String prefix = first + "/";
                    for (PipelineDTO pipeline : pipelines) {
                        String label = pipeline.getLabel();
                        if (label.startsWith(prefix)) {
                            label = label.substring(prefix.length());
                            pipeline.setLabel(label);
                        }
                    }
                    break;
                }
            }
            Collections.sort(pipelines);
            return pipelines;
        } else {
            LOG.warn("No jenkinsfilesFolder!");
            return new ArrayList<>();
        }
    }

    protected File getJenkinsWorkflowFolder(UIContext context) {
        File dir = null;
        Object workflowFolder = context.getAttributeMap().get("jenkinsfilesFolder");
        if (workflowFolder instanceof File) {
            dir = (File) workflowFolder;
        }
        return dir;
    }

    protected String getDescriptionForIssueProject(String value) {
        return null;
    }

    protected Iterable<String> getIssueProjectNames() {
        Set<String> answer = new TreeSet<>();
        try {
            TaigaClient letschat = getTaiga();
            if (letschat != null) {
                List<ProjectDTO> projects = null;
                try {
                    projects = letschat.getProjects();
                } catch (Exception e) {
                    LOG.warn("Failed to load chat projects! " + e, e);
                }
                if (projects != null) {
                    for (ProjectDTO project : projects) {
                        String name = project.getName();
                        if (name != null) {
                            answer.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to get issue project names: " + e, e);
        }
        return answer;

    }

    protected String getDescriptionForChatRoom(String chatRoom) {
        return null;
    }

    protected Iterable<String> getChatRoomNames() {
        Set<String> answer = new TreeSet<>();
        try {
            LetsChatClient letschat = getLetsChat();
            if (letschat != null) {
                List<RoomDTO> rooms = null;
                try {
                    rooms = letschat.getRooms();
                } catch (Exception e) {
                    LOG.warn("Failed to load chat rooms! " + e, e);
                }
                if (rooms != null) {
                    for (RoomDTO room : rooms) {
                        String name = room.getSlug();
                        if (name != null) {
                            answer.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to find chat room names: " + e, e);
        }
        return answer;
    }

    public LetsChatClient getLetsChat() {
        if (letsChat == null) {
            letsChat = LetsChatKubernetes.createLetsChat(getKubernetes());
        }
        return letsChat;
    }

    public void setLetsChat(LetsChatClient letsChat) {
        this.letsChat = letsChat;
    }

    public TaigaClient getTaiga() {
        if (taiga == null) {
            taiga = TaigaKubernetes.createTaiga(getKubernetes(),namespace);
        }
        return taiga;
    }

    public void setTaiga(TaigaClient taiga) {
        this.taiga = taiga;
    }

}

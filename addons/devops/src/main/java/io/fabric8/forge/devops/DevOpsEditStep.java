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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

import io.fabric8.devops.ProjectConfig;
import io.fabric8.devops.ProjectConfigs;
import io.fabric8.forge.addon.utils.CommandHelpers;
import io.fabric8.forge.addon.utils.StopWatch;
import io.fabric8.forge.devops.dto.PipelineDTO;
import io.fabric8.forge.devops.dto.PipelineMetadata;
import io.fabric8.forge.devops.dto.ProjectOverviewDTO;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.utils.Files;
import io.fabric8.utils.Filter;
import io.fabric8.utils.IOHelpers;
import io.fabric8.utils.Objects;
import io.fabric8.utils.Strings;
import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.UICompleter;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.wizard.UIWizardStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.fabric8.kubernetes.api.KubernetesHelper.loadYaml;

public class DevOpsEditStep extends AbstractDevOpsCommand implements UIWizardStep {
    private static final transient Logger LOG = LoggerFactory.getLogger(DevOpsEditStep.class);

    private static final String DEFAULT_MAVEN_FLOW = "workflows/maven/CanaryReleaseStageAndApprovePromote.groovy";
    public static final String JENKINSFILE = "Jenkinsfile";

    @Inject
    @WithAttributes(label = "Pipeline", description = "The Jenkinsfile used to define the Continous Delivery pipeline")
    private UIInput<PipelineDTO> pipeline;

    private List<InputComponent> inputComponents;
    boolean hasJenkinsFile;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(getClass())
                .category(Categories.create(AbstractDevOpsCommand.CATEGORY))
                .name(AbstractDevOpsCommand.CATEGORY + ": Configure")
                .description("Configure the Pipeline for the new project");
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        StopWatch watch = new StopWatch();

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
        if (getCurrentSelectedProject(context) != null) {
            PipelineDTO defaultValue = getPipelineForValue(context, DEFAULT_MAVEN_FLOW);
            if (defaultValue != null) {
                pipeline.setDefaultValue(defaultValue);
                pipeline.setValue(defaultValue);
            }
        }

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
        }
        inputComponents = new ArrayList<>();

        hasJenkinsFile = hasLocalJenkinsFile(context, project);

        if (!hasJenkinsFile) {
            inputComponents.addAll(CommandHelpers.addInputComponents(builder, pipeline));
        }

        log.info("initializeUI took " + watch.taken());
    }

    private boolean hasLocalJenkinsFile(UIContext context, Project project) {
        File jenkinsFile = CommandHelpers.getProjectContextFile(context, project, "Jenkinsfile");
        boolean hasJenkinsFile = Files.isFile(jenkinsFile);
        LOG.debug("Has Jenkinsfile " + hasJenkinsFile + " with file: " + jenkinsFile);
        return hasJenkinsFile;
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        return null;
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        context.getUIContext().getAttributeMap().put("hasJenkinsFile", hasJenkinsFile);
        CommandHelpers.putComponentValuesInAttributeMap(context, inputComponents);
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
        StopWatch watch = new StopWatch();

        Set<String> builders =  null;
        ProjectOverviewDTO projectOverview;
        if (filterPipelines) {
            projectOverview = getProjectOverview(context);
            builders = projectOverview.getBuilders();
        }
        File dir = getJenkinsWorkflowFolder(context);
        Set<String> buildersFound = new HashSet<>();
        try {
            if (dir != null) {
                Filter<File> filter = new Filter<File>() {
                    @Override
                    public boolean matches(File file) {
                        return file.isFile() && Objects.equal(JENKINSFILE, file.getName());
                    }
                };
                Set<File> files = Files.findRecursive(dir, filter);
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
        } finally {
            log.info("getPipelines took " + watch.taken());
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

}

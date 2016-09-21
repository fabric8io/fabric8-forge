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
package io.fabric8.forge.rest.main;

import io.fabric8.forge.rest.Constants;
import io.fabric8.forge.rest.dto.ExecutionRequest;
import io.fabric8.forge.rest.dto.ExecutionResult;
import io.fabric8.forge.rest.hooks.CommandCompletePostProcessor;
import io.fabric8.forge.rest.ui.RestUIContext;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.project.support.BuildConfigHelper;
import io.fabric8.project.support.GitUtils;
import io.fabric8.project.support.UserDetails;
import org.jboss.forge.addon.ui.controller.CommandController;
import org.jboss.forge.furnace.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotAuthorizedException;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * For new projects; lets git add, git commit, git push otherwise lets git add/commit/push any new/udpated changes
 */
// TODO we should try add this into the ConfigureDevOpsStep.execute() block instead!
public class GitCommandCompletePostProcessor implements CommandCompletePostProcessor {
    private static final transient Logger LOG = LoggerFactory.getLogger(GitCommandCompletePostProcessor.class);
    public static final String DEFAULT_JENKINS_SEED_JOB = "seed";
    private final KubernetesClient kubernetes;
    private final GitUserHelper gitUserHelper;
    private final ProjectFileSystem projectFileSystem;

    @Inject
    public GitCommandCompletePostProcessor(KubernetesClient kubernetes,
                                           GitUserHelper gitUserHelper,
                                           ProjectFileSystem projectFileSystem) {
        this.kubernetes = kubernetes;
        this.gitUserHelper = gitUserHelper;
        this.projectFileSystem = projectFileSystem;
    }

    @Override
    public UserDetails preprocessRequest(String name, ExecutionRequest executionRequest, HttpServletRequest request) {
        UserDetails userDetails = gitUserHelper.createUserDetails(request);
        // TODO this isn't really required if there's a secret associated with the BuildConfig source
        if (Strings.isNullOrEmpty(userDetails.getUser()) || Strings.isNullOrEmpty(userDetails.getUser())) {
            throw new NotAuthorizedException("You must authenticate to be able to perform this command");
        }

        if (Objects.equals(name, Constants.PROJECT_NEW_COMMAND)) {
            List<Map<String, Object>> inputList = executionRequest.getInputList();
            if (inputList != null) {
                Map<String, Object> page1 = inputList.get(0);
                if (page1 != null) {
                    if (page1.containsKey(Constants.TARGET_LOCATION_PROPERTY)) {
                        page1.put(Constants.TARGET_LOCATION_PROPERTY, projectFileSystem.getUserProjectFolderLocation(userDetails));
                    }
                }
            }
        }
        return userDetails;
    }


    @Override
    public void firePostCompleteActions(String name, ExecutionRequest executionRequest, RestUIContext context, CommandController controller, ExecutionResult results, HttpServletRequest request) {
        UserDetails userDetails = gitUserHelper.createUserDetails(request);

        String origin = projectFileSystem.getRemote();

        try {
            if (name.equals(Constants.PROJECT_NEW_COMMAND)) {

                String targetLocation = projectFileSystem.getUserProjectFolderLocation(userDetails);
                String named = null;
                List<Map<String, Object>> inputList = executionRequest.getInputList();

                for (Map<String, Object> map : inputList) {
                    if (Strings.isNullOrEmpty(named)) {
                        Object value = map.get("named");
                        if (value != null) {
                            named = value.toString();
                        }
                    }
                }
                if (Strings.isNullOrEmpty(targetLocation)) {
                    LOG.warn("No targetLocation could be found!");
                } else if (Strings.isNullOrEmpty(named)) {
                    LOG.warn("No named could be found!");
                } else {
                    File basedir = new File(targetLocation, named);
                    if (!basedir.isDirectory() || !basedir.exists()) {
                        LOG.warn("Generated project folder does not exist: " + basedir.getAbsolutePath());
                    } else {
                        String namespace = firstNotBlank(context.getProjectName(), executionRequest.getNamespace());
                        String projectName = firstNotBlank(named, context.getProjectName(), executionRequest.getProjectName());
                        String message = ExecutionRequest.createCommitMessage(name, executionRequest);

                        BuildConfigHelper.CreateGitProjectResults createProjectResults = BuildConfigHelper.importNewGitProject(this.kubernetes, userDetails, basedir, namespace, projectName, origin, message, true);

                        results.setOutputProperty("fullName", createProjectResults.getFullName());
                        results.setOutputProperty("cloneUrl", createProjectResults.getCloneUrl());
                        results.setOutputProperty("htmlUrl", createProjectResults.getHtmlUrl());

                        results.setProjectName(projectName);

                        LOG.info("Creating any pending webhooks");
                        registerWebHooks(context);

                        LOG.info("Done creating webhooks!");
                    }
                }
            } else {
                registerWebHooks(context);
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

    public static String firstNotBlank(String... texts) {
        for (String text : texts) {
            if (!Strings.isNullOrEmpty(text)) {
                return text;
            }
        }
        return null;
    }

    protected void registerWebHooks(RestUIContext context) {
        Map<Object, Object> attributeMap = context.getAttributeMap();
        Object registerWebHooksValue = attributeMap.get("registerWebHooks");
        if (registerWebHooksValue instanceof Runnable) {
            Runnable runnable = (Runnable) registerWebHooksValue;
            projectFileSystem.invokeLater(runnable, 1000L);
        }
    }

    protected void handleException(Throwable e) {
        LOG.warn("Caught: " + e, e);
        throw new RuntimeException(e);
    }
}

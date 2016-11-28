/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.forge.rest.client;

import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.JobWithDetails;
import io.fabric8.forge.rest.dto.CommandInputDTO;
import io.fabric8.forge.rest.dto.ExecutionRequest;
import io.fabric8.forge.rest.dto.ExecutionResult;
import io.fabric8.forge.rest.dto.PropertyDTO;
import io.fabric8.forge.rest.dto.ValidationResult;
import io.fabric8.utils.Asserts;
import io.fabric8.utils.Block;
import io.fabric8.utils.Files;
import io.fabric8.utils.IOHelpers;
import org.apache.cxf.helpers.IOUtils;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.fabric8.forge.rest.client.CommandConstants.DevopsEdit;
import static io.fabric8.forge.rest.client.CommandConstants.DevopsEditProperties.Pipeline.CanaryReleaseAndStage;
import static io.fabric8.forge.rest.client.CommandConstants.ProjectNew;
import static io.fabric8.forge.rest.client.ForgeClientAsserts.asserGetAppGitCloneURL;
import static io.fabric8.forge.rest.client.ForgeClientAsserts.assertChooseValue;
import static io.fabric8.forge.rest.client.ForgeClientAsserts.assertJob;
import static io.fabric8.forge.rest.client.ForgeClientAsserts.getBasedir;
import static io.fabric8.forge.rest.client.ForgeClientHelpers.addPage;
import static io.fabric8.forge.rest.client.ForgeClientHelpers.createPage;
import static io.fabric8.forge.rest.client.ForgeClientHelpers.getCommandProperties;
import static io.fabric8.forge.rest.client.ForgeClientHelpers.updatePageValues;
import static org.assertj.core.api.Assertions.assertThat;

/**
 */
public class ForgeTestSupport {
    private static final transient Logger LOG = LoggerFactory.getLogger(ForgeTestSupport.class);

    protected ForgeClient forgeClient = new ForgeClient();

    public static String generateProjectName(String prefix) {
        SimpleDateFormat format = new SimpleDateFormat("MMM-dd-'at'-HH-mm-ss");
        String answer = prefix + format.format(new Date()).toLowerCase();
        LOG.info("Creating project: " + answer);
        return answer;
    }

    public void assertCreateAndBuildProject(String prefix, final String projectType) throws Exception {
        String projectName = generateProjectName(prefix);

        String commandName = ProjectNew;

        ValueProvider projectTypeValues = new ValueProvider() {
            @Override
            public Object getValue(String propertyName, PropertyDTO property, int pageNumber) {
                switch (propertyName) {
                    case "named":
                        return projectName;
                    case "targetLocation":
                        return null;
                    case "type":
                        return assertChooseValue(propertyName, property, pageNumber, projectType);
                }
                return super.getValue(propertyName, property, pageNumber);

            }
        };

        executeWizardCommand(projectName, commandName, projectTypeValues, 1);

        ValueProvider pipelineValues = new ValueProvider() {
            @Override
            public Object getValue(String propertyName, PropertyDTO property, int pageNumber) {
                switch (propertyName) {
                    case "pipeline":
                        return assertChooseValue(propertyName, property, pageNumber, CanaryReleaseAndStage);
                }
                return super.getValue(propertyName, property, pageNumber);

            }
        };

        executeWizardCommand(projectName, DevopsEdit, pipelineValues, 1);

        Build firstBuild = ForgeClientAsserts.assertBuildCompletes(forgeClient, projectName);

        assertCodeChangeTriggersWorkingBuild(projectName, firstBuild);
    }

    protected Build assertCodeChangeTriggersWorkingBuild(final String projectName, Build firstBuild) throws Exception {
        File cloneDir = new File(getBasedir(), "target/projects/" + projectName);

        String gitUrl = asserGetAppGitCloneURL(forgeClient, projectName);
        Git git = ForgeClientAsserts.assertGitCloneRepo(gitUrl, cloneDir);

        // lets make a dummy commit...
        File readme = new File(cloneDir, "ReadMe.md");
        boolean mustAdd = false;
        String text = "";
        if (readme.exists()) {
            text = IOHelpers.readFully(readme);
        } else {
            mustAdd = true;
        }
        text += "\nupdated at: " + new Date();
        Files.writeToFile(readme, text, Charset.defaultCharset());

        if (mustAdd) {
            AddCommand add = git.add().addFilepattern("*").addFilepattern(".");
            add.call();
        }


        LOG.info("Committing change to " + readme);

        CommitCommand commit = git.commit().setAll(true).setAuthor(forgeClient.getPersonIdent()).setMessage("dummy commit to trigger a rebuild");
        commit.call();
        PushCommand command = git.push();
        command.setCredentialsProvider(forgeClient.createCredentialsProvider());
        command.setRemote("origin").call();

        LOG.info("Git pushed change to " + readme);

        // now lets wait for the next build to start
        int nextBuildNumber = firstBuild.getNumber() + 1;


        Asserts.assertWaitFor(10 * 60 * 1000, new Block() {
            @Override
            public void invoke() throws Exception {
                JobWithDetails job = assertJob(projectName);
                Build lastBuild = job.getLastBuild();
                assertThat(lastBuild.getNumber()).describedAs("Waiting for latest build for job " + projectName + " to start").isGreaterThanOrEqualTo(nextBuildNumber);
            }
        });

        return ForgeClientAsserts.assertBuildCompletes(forgeClient, projectName);
    }

    protected ExecutionRequest executeWizardCommand(String projectName, String commandName, ValueProvider valueProvider, int numberOfPages) throws Exception {
        try {
            String namespace = forgeClient.getNamespace();
            CommandInputDTO commandInput;
            if (commandName.equals(ProjectNew)) {
                commandInput = forgeClient.getCommandInput(commandName);
            } else {
                commandInput = forgeClient.getCommandInput(commandName, namespace, projectName);
            }
            ExecutionRequest executionRequest = new ExecutionRequest();
            executionRequest.setNamespace(namespace);
            if (!ProjectNew.equals(commandName)) {
                executionRequest.setProjectName(projectName);
            }
            List<Map<String, Object>> inputList = new ArrayList<>();
            executionRequest.setInputList(inputList);

            addPage(inputList, getCommandProperties(commandInput), valueProvider);

            for (int page = 1; page < numberOfPages; page++) {
                if (page > 1) {
                    executionRequest.setProjectName(projectName);
                }
                ExecutionResult executionResult = validateAndExecute(commandName, executionRequest, valueProvider);
                ForgeClientAsserts.assertCanMoveToNextStep(executionResult);

                addNextPage(commandName, executionRequest, valueProvider, inputList, executionResult);
            }

            ExecutionResult executionResult = validateAndExecute(commandName, executionRequest, valueProvider);
            ForgeClientAsserts.assertExecutionWorked(executionResult);
            return executionRequest;
        } catch (WebApplicationException e) {
            LOG.error("Failed: " + e, e);
            Response response = e.getResponse();
            if (response != null) {
                LOG.error("Response entity: " + entityToString(response.getEntity()));
            }
            throw e;
        }
    }

    public void addNextPage(String commandName, ExecutionRequest executionRequest, ValueProvider valueProvider, List<Map<String, Object>> inputList, ExecutionResult executionResult) throws Exception {
        Map<String, PropertyDTO> commandProperties = getCommandProperties(executionResult);
        if (commandProperties.isEmpty()) {
            // lets add an empty page then lets validate
            Map<String, Object> emptyPage = new HashMap<>();
            String dummyKey = "_dummy";
            emptyPage.put(dummyKey, "1234");
            inputList.add(emptyPage);

            ValidationResult validationResult = forgeClient.validateCommand(commandName, executionRequest);
            commandProperties = getCommandProperties(validationResult);
            assertThat(commandProperties).describedAs("ValidationResults.commandProperties").isNotNull().isNotEmpty();
            Map<String, Object> page = createPage(inputList, commandProperties, valueProvider);
            emptyPage.remove(dummyKey);
            emptyPage.putAll(page);
        } else {
            addPage(inputList, commandProperties, valueProvider);
        }
    }

    private String entityToString(Object entity) {
        try {
            if (entity instanceof InputStream) {
                return IOUtils.readStringFromStream((InputStream) entity);
            }
            if (entity == null) {
                return "null";
            }
            return entity.toString();
        } catch (IOException e) {
            return "Could not read entity: " + e;
        }
    }

    protected ExecutionResult validateAndExecute(String commandName, ExecutionRequest executionRequest, ValueProvider valueProvider) throws Exception {
        Map<String, Object> page = ForgeClientHelpers.getLastPage(executionRequest);
        LOG.info("Forge wizard step inputs: " + page);
        ValidationResult result = forgeClient.validateCommand(commandName, executionRequest);
        LOG.info("Forge Result: " + result);
        Map<String, PropertyDTO> commandProperties = getCommandProperties(result);

        // lets update the page with any completed values
        updatePageValues(executionRequest.getInputList(), commandProperties, valueProvider, page);

        ForgeClientAsserts.assertValidAndExecutable(result);
        return forgeClient.executeCommand(commandName, executionRequest);
    }
}

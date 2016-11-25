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

import io.fabric8.forge.rest.dto.CommandInputDTO;
import io.fabric8.forge.rest.dto.ExecutionRequest;
import io.fabric8.forge.rest.dto.ExecutionResult;
import io.fabric8.forge.rest.dto.PropertyDTO;
import io.fabric8.forge.rest.dto.ValidationResult;
import org.apache.cxf.helpers.IOUtils;
import org.junit.Ignore;
import org.junit.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.fabric8.forge.rest.client.CommandConstants.DevopsEdit;
import static io.fabric8.forge.rest.client.CommandConstants.DevopsEditProperties.Pipeline.CanaryReleaseAndStage;
import static io.fabric8.forge.rest.client.CommandConstants.ProjectNew;
import static io.fabric8.forge.rest.client.CommandConstants.ProjectNewProperties.Catalog.Fabric8;
import static io.fabric8.forge.rest.client.CommandConstants.ProjectNewProperties.Type.FromArchetypeCatalog;
import static io.fabric8.forge.rest.client.CommandConstants.ProjectNewProperties.Type.Microservice;
import static io.fabric8.forge.rest.client.ForgeClientAsserts.assertChooseValue;
import static io.fabric8.forge.rest.client.ForgeClientHelpers.addPage;
import static io.fabric8.forge.rest.client.ForgeClientHelpers.createPage;
import static io.fabric8.forge.rest.client.ForgeClientHelpers.getCommandProperties;
import static io.fabric8.forge.rest.client.ForgeClientHelpers.updatePageValues;
import static org.assertj.core.api.Assertions.assertThat;

/**
 */
public class ForgeClientKT {
    protected ForgeClient forgeClient = new ForgeClient();

    @Test
    public void testNewProject() throws Exception {
        String projectName = generateProjectName();
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
                        return assertChooseValue(propertyName, property, pageNumber, Microservice);
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

    }

    protected String generateProjectName() {
        SimpleDateFormat format = new SimpleDateFormat("EEE-dd-MM-yy-kk-mm-ss");
        return format.format(new Date()).toLowerCase();
    }

    /**
     * TODO posting the archetype catalog generates:
     * <p>
     * java.lang.ClassCastException: org.jboss.forge.furnace.proxy.ForgeProxy_$$_javassist_4511e8db-4647-48b3-aa6c-b5ba9f325205 cannot be cast to org.jboss.forge.addon.maven.archetype.ArchetypeCatalogFactory
     * at org.jboss.forge.addon.maven.projects.archetype.ui.ArchetypeCatalogSelectionWizardStep$1.convert(ArchetypeCatalogSelectionWizardStep.java:79)
     * at sun.reflect.GeneratedMethodAccessor586.invoke(Unknown Source)
     * at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)[:1.8.0_101]
     * at java.lang.reflect.Method.invoke(Method.java:498)[:1.8.0_101]
     * at org.jboss.forge.furnace.proxy.ClassLoaderAdapterCallback$2.call(ClassLoaderAdapterCallback.java:124)[furnace-proxy-2.24.2.Final.jar:2.24.2.Final]
     * at org.jboss.forge.furnace.util.ClassLoaders.executeIn(ClassLoaders.java:42)[furnace-api-2.24.2.Final.jar:2.24.2.Final]
     * at org.jboss.forge.furnace.proxy.ClassLoaderAdapterCallback.invoke(ClassLoaderAdapterCallback.java:97)[furnace-proxy-2.24.2.Final.jar:2.24.2.Final]
     * at org.jboss.forge.addon.convert.Converter_$$_javassist_f8ce79f6-7527-458e-8b8d-37ea1872b873.convert(Converter_$$_javassist_f8ce79f6-7527-458e-8b8d-37ea1872b873.java)[convert-api-3.3.3.Final.jar:3.3.3.Final]
     * at org.jboss.forge.addon.ui.util.InputComponents.convertToUIInputValue(InputComponents.java:205)[ui-api-3.3.3.Final.jar:3.3.3.Final]
     * at org.jboss.forge.addon.ui.util.InputComponents.setSingleInputValue(InputComponents.java:118)[ui-api-3.3.3.Final.jar:3.3.3.Final]
     * at org.jboss.forge.addon.ui.util.InputComponents.setValueFor(InputComponents.java:84)[ui-api-3.3.3.Final.jar:3.3.3.Final]
     * at io.fabric8.forge.rest.dto.UICommands.populateController(UICommands.java:337)[fabric8-forge-core-2.3.77.jar:2.3.77]
     */
    @Ignore
    public void testNewArchetypeProject() throws Exception {
        String projectName = "cheese4";
        String commandName = ProjectNew;

        ValueProvider valueProvider = new ValueProvider() {
            @Override
            public Object getValue(String propertyName, PropertyDTO property, int pageNumber) {
                switch (pageNumber) {
                    case 0:
                        switch (propertyName) {
                            case "named":
                                return projectName;
                            case "targetLocation":
                                return null;
                            case "type":
                                return assertChooseValue(propertyName, property, pageNumber, FromArchetypeCatalog);
                        }
                        break;

                    case 1:
                        switch (propertyName) {
                            case "catalog":
                                return assertChooseValue(propertyName, property, pageNumber, Fabric8);
                        }
                        break;
                }
                return super.getValue(propertyName, property, pageNumber);

            }
        };

        executeWizardCommand(projectName, commandName, valueProvider, 3);

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
            System.out.println("Failed: " + e);
            Response response = e.getResponse();
            if (response != null) {
                System.out.println("Response entity: " + entityToString(response.getEntity()));
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
            System.out.println("Now page is: " + page);
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

    @Ignore
    public void testListCommands() throws Exception {
        List<String> commandNames = forgeClient.getCommandNames();
        System.out.println("Command names: " + commandNames);
    }

    protected ExecutionResult validateAndExecute(String commandName, ExecutionRequest executionRequest, ValueProvider valueProvider) throws Exception {
        Map<String, Object> page = ForgeClientHelpers.getLastPage(executionRequest);
        System.out.println("Page inputs: " + page);
        ValidationResult result = forgeClient.validateCommand(commandName, executionRequest);
        System.out.println("Result: " + result);
        Map<String, PropertyDTO> commandProperties = getCommandProperties(result);

        // lets update the page with any completed values
        updatePageValues(executionRequest.getInputList(), commandProperties, valueProvider, page);

        ForgeClientAsserts.assertValidAndExecutable(result);
        return forgeClient.executeCommand(commandName, executionRequest);
    }

}

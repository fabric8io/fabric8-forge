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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.fabric8.forge.rest.CommandsAPI;
import io.fabric8.forge.rest.dto.CommandInfoDTO;
import io.fabric8.forge.rest.dto.CommandInputDTO;
import io.fabric8.forge.rest.dto.ExecutionRequest;
import io.fabric8.forge.rest.dto.ExecutionResult;
import io.fabric8.forge.rest.dto.ValidationResult;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.utils.Strings;
import io.fabric8.utils.URLUtils;
import io.fabric8.utils.cxf.JsonHelper;
import io.fabric8.utils.cxf.WebClients;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.List;

import static io.fabric8.utils.cxf.WebClients.disableSslChecks;

/**
 * A simple Java Facade for interacting with the Fabric8 Forge
 */
public class ForgeClient {
    private static final transient Logger LOG = LoggerFactory.getLogger(ForgeClient.class);

    private String address = "http://fabric8-forge/";
    private CommandsAPI clientAPI;
    private String namespace;
    private String secret = "default-gogs-git";
    private String secretNamespace = "user-secrets-source-admin";
    private String kubeUserName = "admin";
    private boolean debugResponses;

    public ForgeClient() {
        String addressEnv = System.getenv("FABRIC8_FORGE_URL");
        if (Strings.isNotBlank(addressEnv)) {
            address = addressEnv;
        }
    }

    public boolean isDebugResponses() {
        return debugResponses;
    }

    public void setDebugResponses(boolean debugResponses) {
        this.debugResponses = debugResponses;
    }

    public String getNamespace() {
        if (Strings.isNullOrBlank(namespace)) {
            namespace = new DefaultKubernetesClient().getNamespace();
        }
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getSecretNamespace() {
        return secretNamespace;
    }

    public void setSecretNamespace(String secretNamespace) {
        this.secretNamespace = secretNamespace;
    }

    public String getKubeUserName() {
        return kubeUserName;
    }

    public void setKubeUserName(String kubeUserName) {
        this.kubeUserName = kubeUserName;
    }

    public ExecutionResult executeCommand(String name, ExecutionRequest executionRequest) throws Exception {
        Response response = doExecuteCommand(name, executionRequest);
        return parseResponse(response, ExecutionResult.class);
    }


    public CommandInputDTO getCommandInput(String name) throws Exception {
        Response response = doGetCommandInput(name);
        return parseResponse(response, CommandInputDTO.class);
    }

    public CommandInputDTO getCommandInput(String name, String namespace, String projectName) throws Exception {
        Response response = doGetCommandInput(name, namespace, projectName);
        return parseResponse(response, CommandInputDTO.class);
    }

    public CommandInputDTO getCommandInput(String name, String namespace, String projectName, String resourcePath) throws Exception {
        Response response = doGetCommandInput(name, namespace, projectName, resourcePath);
        return parseResponse(response, CommandInputDTO.class);
    }

    public ValidationResult validateCommand(String name, ExecutionRequest executionRequest) throws Exception {
        Response response;
        String projectName = executionRequest.getProjectName();
        String namespace = executionRequest.getNamespace();
        response = getClientAPI().validateCommand(name, executionRequest);
        response = getClientAPI().validateCommand(name, executionRequest);
        return parseResponse(response, ValidationResult.class);
    }


    @GET
    @Path("/commandNames")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getCommandNames() {
        return getClientAPI().getCommandNames();
    }

    @GET
    @Path("/commands")
    @Produces(MediaType.APPLICATION_JSON)
    public List<CommandInfoDTO> getCommands() throws Exception {
        return getClientAPI().getCommands();
    }

    @GET
    @Path("/commands/{namespace}/{projectName}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<CommandInfoDTO> getCommands(String namespace, String projectName) throws Exception {
        return getClientAPI().getCommands(namespace, projectName);
    }

    @GET
    @Path("/commands/{namespace}/{projectName}/{path: .*}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<CommandInfoDTO> getCommands(String namespace, String projectName, String resourcePath) throws Exception {
        return getClientAPI().getCommands(namespace, projectName, resourcePath);
    }

    protected Response doGetCommandInput(String name) throws Exception {
        return getClientAPI().getCommandInput(name);
    }

    protected Response doGetCommandInput(String name, String namespace, String projectName) throws Exception {
        return getClientAPI().getCommandInput(name, namespace, projectName);
    }

    protected Response doGetCommandInput(String name, String namespace, String projectName, String resourcePath) throws Exception {
        return getClientAPI().getCommandInput(name, namespace, projectName, resourcePath);
    }

    protected Response doExecuteCommand(String name, ExecutionRequest executionRequest) throws Exception {
        return getClientAPI().executeCommand(name, executionRequest);
    }

    protected Response doValidateCommand(String name, ExecutionRequest executionRequest) throws Exception {
        return getClientAPI().validateCommand(name, executionRequest);
    }

    protected CommandsAPI getClientAPI() {
        if (clientAPI == null) {
            clientAPI = createWebClient(CommandsAPI.class);
        }
        return clientAPI;
    }

    /**
     * Creates a JAXRS web client for the given JAXRS client
     */
    protected <T> T createWebClient(Class<T> clientType) {
        List<Object> providers = WebClients.createProviders();
        String queryString = "?secret=" + secret + "&secretNamespace=" + secretNamespace + "&kubeUserName=" + kubeUserName;
        String commandsAddress = URLUtils.pathJoin(this.address, "/api/forge" + queryString);
        WebClient webClient = WebClient.create(commandsAddress, providers);
        disableSslChecks(webClient);
        return JAXRSClientFactory.fromClient(webClient, clientType);
    }


    protected <T> T parseResponse(Response response, Class<T> clazz) throws IOException {
        Object entity = response.getEntity();
        ObjectMapper objectMapper = JsonHelper.createObjectMapper();
        ObjectReader reader = objectMapper.readerFor(clazz);
        if (entity instanceof Reader) {
            Reader input = (Reader) entity;
            return reader.readValue(input);
        } else if (entity instanceof InputStream) {
            InputStream input = (InputStream) entity;
            if (debugResponses) {
                String json = IOUtils.readStringFromStream(input);
                LOG.info("Received JSON: " + json);
                return reader.readValue(json);
            } else {
                return reader.readValue(input);
            }
        } else if (entity instanceof String) {
            String text = (String) entity;
            if (debugResponses) {
                LOG.info("Received JSON: " + text);
            }
            return reader.readValue(text);
        } else if (entity instanceof byte[]) {
            byte[] data = (byte[]) entity;
            if (debugResponses) {
                LOG.info("Received JSON: " + new String(data));
            }
            return reader.readValue(data);
        } else if (clazz.isInstance(entity)) {
            return clazz.cast(entity);
        } else if (entity == null) {
            return null;
        } else {
            throw new IllegalArgumentException("Could not parse the returned entity of class " + entity.getClass().getName() + " = " + entity);
        }
    }
}

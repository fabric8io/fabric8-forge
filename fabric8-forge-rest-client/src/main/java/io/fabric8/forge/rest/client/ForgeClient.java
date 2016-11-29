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
import io.fabric8.kubernetes.api.Controller;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.project.support.UserDetails;
import io.fabric8.utils.Strings;
import io.fabric8.utils.URLUtils;
import io.fabric8.utils.cxf.JsonHelper;
import io.fabric8.utils.cxf.WebClients;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.CredentialsProvider;
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

import static io.fabric8.forge.rest.client.EnvironmentVariables.getEnvironmentValue;
import static io.fabric8.utils.cxf.WebClients.disableSslChecks;

/**
 * A simple Java Facade for interacting with the Fabric8 Forge
 */
public class ForgeClient {
    private static final transient Logger LOG = LoggerFactory.getLogger(ForgeClient.class);

    private String address = getEnvironmentValue(EnvironmentVariables.FORGE_URL, "http://fabric8-forge/");
    private CommandsAPI clientAPI;
    private String namespace;
    private String secret = "default-gogs-git";
    private String secretNamespace = "user-secrets-source-admin";
    private String kubeUserName = "admin";
    private boolean debugResponses;
    private KubernetesClient kubernetesClient = new DefaultKubernetesClient();
    private long connectionTimeoutMillis = 10 * 60 * 1000L;
    private PersonIdent personIdent;
    private String gitUser = "gogsadmin";
    private String gitPassword = "RedHat$1";
    private String gitEmail = "gogsadmin@acme.com";

    public ForgeClient() {
    }

    public KubernetesClient getKubernetesClient() {
        return kubernetesClient;
    }

    public void setKubernetesClient(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }


    public OpenShiftClient getOpenShiftOrJenkinshiftClient() {
        return new Controller(kubernetesClient).getOpenShiftClientOrJenkinshift();
    }

    public long getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }

    public void setConnectionTimeoutMillis(long connectionTimeoutMillis) {
        this.connectionTimeoutMillis = connectionTimeoutMillis;
    }

    public boolean isDebugResponses() {
        return debugResponses;
    }

    public void setDebugResponses(boolean debugResponses) {
        this.debugResponses = debugResponses;
    }

    public String getNamespace() {
        if (Strings.isNullOrBlank(namespace)) {
            namespace = kubernetesClient.getNamespace();
        }
        if (Strings.isNullOrBlank(namespace)) {
            namespace = KubernetesHelper.defaultNamespace();
        }
        if (Strings.isNullOrBlank(namespace)) {
            namespace = "default";
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


    public PersonIdent getPersonIdent() {
        if (personIdent == null) {
            personIdent = new PersonIdent(gitUser, gitEmail);
        }
        return personIdent;
    }

    public void setPersonIdent(PersonIdent personIdent) {
        this.personIdent = personIdent;
    }

    public String getGitUser() {
        return gitUser;
    }

    public void setGitUser(String gitUser) {
        this.gitUser = gitUser;
    }

    public String getGitPassword() {
        return gitPassword;
    }

    public void setGitPassword(String gitPassword) {
        this.gitPassword = gitPassword;
    }

    public String getGitEmail() {
        return gitEmail;
    }

    public void setGitEmail(String gitEmail) {
        this.gitEmail = gitEmail;
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
        HTTPConduit conduit = WebClient.getConfig(webClient).getHttpConduit();
        HTTPClientPolicy httpClientPolicy = new HTTPClientPolicy();
        httpClientPolicy.setConnectionTimeout(connectionTimeoutMillis);
        httpClientPolicy.setReceiveTimeout(connectionTimeoutMillis);
        conduit.setClient(httpClientPolicy);

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

    public CredentialsProvider createCredentialsProvider() {
        String address = "gogs";
        String internalAddress = "gogs";
        return new UserDetails(address, internalAddress, gitUser, gitPassword, gitEmail).createCredentialsProvider();
    }
}

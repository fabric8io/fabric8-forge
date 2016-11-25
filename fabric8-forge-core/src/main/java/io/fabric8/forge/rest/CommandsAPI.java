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
package io.fabric8.forge.rest;

import io.fabric8.forge.rest.dto.CommandInfoDTO;
import io.fabric8.forge.rest.dto.ExecutionRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 */
public interface CommandsAPI {
    @GET
    @Path("/commandNames")
    @Produces(MediaType.APPLICATION_JSON)
    List<String> getCommandNames();

    @GET
    @Path("/commands")
    @Produces(MediaType.APPLICATION_JSON)
    List<CommandInfoDTO> getCommands() throws Exception;

    @GET
    @Path("/commands/{namespace}/{projectName}")
    @Produces(MediaType.APPLICATION_JSON)
    List<CommandInfoDTO> getCommands(@PathParam("namespace") String namespace, @PathParam("projectName") String projectName) throws Exception;

    @GET
    @Path("/commands/{namespace}/{projectName}/{path: .*}")
    @Produces(MediaType.APPLICATION_JSON)
    List<CommandInfoDTO> getCommands(@PathParam("namespace") String namespace, @PathParam("projectName") String projectName, @PathParam("path") String resourcePath) throws Exception;

    @GET
    @Path("/command/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getCommandInfo(@PathParam("name") String name) throws Exception;

    @GET
    @Path("/command/{name}/{namespace}/{projectName}/{path: .*}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getCommandInfo(@PathParam("name") String name, @PathParam("namespace") String namespace, @PathParam("projectName") String projectName,
                            @PathParam("path") String resourcePath) throws Exception;

    @GET
    @Path("/commandInput/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getCommandInput(@PathParam("name") String name) throws Exception;

    @GET
    @Path("/commandInput/{name}/{namespace}/{projectName}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getCommandInput(@PathParam("name") String name, @PathParam("namespace") String namespace, @PathParam("projectName") String projectName) throws Exception;

    @GET
    @Path("/commandInput/{name}/{namespace}/{projectName}/{path: .*}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getCommandInput(@PathParam("name") String name,
                             @PathParam("namespace") String namespace, @PathParam("projectName") String projectName,
                             @PathParam("path") String resourcePath) throws Exception;

    @GET
    @Path("/invoke/{name}/{namespace}/{projectName}")
    @Produces(MediaType.APPLICATION_JSON)
    Response executeCommandViaGetNoPath(@PathParam("name") String name,
                                        @PathParam("namespace") String namespace,
                                        @PathParam("projectName") String project) throws Exception;

    @GET
    @Path("/invoke/{name}/{namespace}/{projectName}/{path: .*}")
    @Produces(MediaType.APPLICATION_JSON)
    Response executeCommandViaGet(@PathParam("name") String name,
                                  @PathParam("namespace") String namespace,
                                  @PathParam("projectName") String project,
                                  @PathParam("path") String path) throws Exception;

    @POST
    @Path("/command/execute/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response executeCommand(@PathParam("name") String name, ExecutionRequest executionRequest) throws Exception;

    @POST
    @Path("/command/validate/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response validateCommand(@PathParam("name") String name, ExecutionRequest executionRequest) throws Exception;
}

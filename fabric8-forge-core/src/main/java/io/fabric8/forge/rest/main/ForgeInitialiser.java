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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

import io.fabric8.forge.rest.CommandsResource;
import io.fabric8.forge.rest.dto.CommandInfoDTO;
import io.fabric8.forge.rest.producer.FurnaceProducer;
import io.fabric8.forge.rest.utils.StopWatch;
import org.apache.commons.io.FileUtils;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initialises Forge add on repository
 */
@Singleton
@javax.ejb.Singleton
@javax.ejb.Startup
public class ForgeInitialiser {
    private static final transient Logger LOG = LoggerFactory.getLogger(ForgeInitialiser.class);

    /**
     * @param addOnDir the directory where Forge addons will be stored
     */
    @Inject
    public ForgeInitialiser(@ConfigProperty(name = "FORGE_ADDON_DIRECTORY", defaultValue = "./addon-repository") String addOnDir, FurnaceProducer furnaceProducer) {
        java.util.logging.Logger out = java.util.logging.Logger.getLogger(this.getClass().getName());
        out.info("Logging to JUL to test the configuration");

        // lets ensure that the addons folder is initialised
        File repoDir = new File(addOnDir);
        repoDir.mkdirs();
        LOG.info("initialising furnace with folder: " + repoDir.getAbsolutePath());
        File[] files = repoDir.listFiles();
        if (files == null || files.length == 0) {
            LOG.warn("No files found in the addon directory: " + repoDir.getAbsolutePath());
        } else {
            LOG.warn("Found " + files.length + " addon files in directory: " + repoDir.getAbsolutePath());
        }
        furnaceProducer.setup(repoDir);
    }

    public void preloadCommands(CommandsResource commandsResource)  {
        StopWatch watch = new StopWatch();

        LOG.info("Preloading commands");
        List<CommandInfoDTO> commands = Collections.EMPTY_LIST;
        try {
            commands = commandsResource.getCommands();
            LOG.info("Loaded " + commands.size() + " commands");
        } catch (Exception e) {
            LOG.error("Failed to preload commands! " + e, e);
        }

        LOG.info("preloadCommands took " + watch.taken());
    }

    public void preloadProjects(CommandsResource commandsResource, Map<String, Set<String>> catalogs)  {
        preloadProjectsMaven(catalogs);
    }

    private void preloadProjectsMaven(Map<String, Set<String>> catalogs)  {
        StopWatch watch = new StopWatch();

        LOG.info("Preloading projects");
        int i = 0;

        File tempDir = createTempDirectory();
        if (tempDir == null) {
            LOG.warn("Cannot create temporary directory");
            return;
        }

        for (Map.Entry<String, Set<String>> entry : catalogs.entrySet()) {
            for (String archetype : entry.getValue()) {
                i++;
                String folder = "dummy-" + i;

                String[] coord = archetype.split(":");
                String goal = String.format("archetype:generate -DarchetypeGroupId=%s -DarchetypeArtifactId=%s -DarchetypeVersion=%s -DgroupId=com.foo -DartifactId=%s", coord[0], coord[1], coord[2], folder);

                InvocationRequest request = new DefaultInvocationRequest();
                request.setPomFile(null);
                request.setGoals(Arrays.asList(goal));
                request.setBaseDirectory(tempDir);
                request.setInteractive(false);
                request.setShowErrors(true);

                System.out.println("Creating project from archetype: " + archetype + " in directory: " + tempDir + "/" + folder);

                Invoker invoker = new DefaultInvoker();
                try {
                    invoker.execute(request);
                } catch (MavenInvocationException e) {
                    System.err.println("Error creating project due " + e.getMessage());
                }
            }
        }

        // cleanup temporary directory
        try {
            FileUtils.deleteDirectory(tempDir);
        } catch (IOException e) {
            // ignore
        }

        LOG.info("Preloading projects took " + watch.taken());
    }

    protected File createTempDirectory() {
        try {
            File dir = new File("project-new");
            // delete directory
            FileUtils.deleteDirectory(dir);
            dir.mkdir();
            return dir;
        } catch (IOException e) {
            LOG.error("Failed to create temp directory: " + e, e);
        }

        return null;
    }

}

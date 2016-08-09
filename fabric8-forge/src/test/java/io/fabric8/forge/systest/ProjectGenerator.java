/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.forge.systest;

import io.fabric8.forge.systest.support.RestUIContext;
import io.fabric8.forge.systest.support.RestUIRuntime;
import io.fabric8.utils.Strings;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.jboss.forge.addon.resource.Resource;
import org.jboss.forge.addon.resource.ResourceFactory;
import org.jboss.forge.addon.ui.command.CommandFactory;
import org.jboss.forge.addon.ui.command.UICommand;
import org.jboss.forge.addon.ui.controller.CommandController;
import org.jboss.forge.addon.ui.controller.CommandControllerFactory;
import org.jboss.forge.addon.ui.controller.WizardCommandController;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.UICompleter;
import org.jboss.forge.addon.ui.output.UIMessage;
import org.jboss.forge.addon.ui.result.CompositeResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.util.InputComponents;
import org.jboss.forge.furnace.Furnace;
import org.jboss.forge.furnace.addons.AddonRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assert.assertEquals;

/**
 */
public class ProjectGenerator {
    public final static String FABRIC8_ARCHETYPE_VERSION = System.getProperty("fabric8ArchetypeVersion", "2.2.161");
    private static final transient Logger LOG = LoggerFactory.getLogger(ProjectGenerator.class);
    private final CommandControllerFactory commandControllerFactory;
    private final CommandFactory commandFactory;
    private final RestUIRuntime runtime = new RestUIRuntime();
    private final Furnace furnace;
    private final File projectsOutputFolder;
    private final File localMavenRepo;
    private final AddonRegistry addonRegistry;
    private final ResourceFactory resourceFactory;

    public ProjectGenerator(Furnace furnace, File projectsOutputFolder, File localMavenRepo) throws Exception {
        this.furnace = furnace;
        this.projectsOutputFolder = projectsOutputFolder;
        this.localMavenRepo = localMavenRepo;
        addonRegistry = furnace.getAddonRegistry();

        resourceFactory = addonRegistry.getServices(ResourceFactory.class).get();
        commandControllerFactory = addonRegistry.getServices(CommandControllerFactory.class).get();
        commandFactory = addonRegistry.getServices(CommandFactory.class).get();

    }

    protected static WizardCommandController assertWizardController(CommandController controller) {
        if (controller instanceof WizardCommandController) {
            return (WizardCommandController) controller;
        } else {
            fail("controller is not a wizard! " + controller.getClass());
            return null;
        }
    }

    public File getArtifactJar(String groupId, String artifactId, String version) {
        Properties properties = new Properties();
        properties.put("groupId", groupId);
        properties.put("artifactId", artifactId);
        properties.put("version", version);

        InvocationRequest request = new DefaultInvocationRequest();
        request.setLocalRepositoryDirectory(localMavenRepo);
        request.setInteractive(false);
        List<String> goalList = Arrays.asList("org.apache.maven.plugins:maven-dependency-plugin:2.10:get");
        request.setGoals(goalList);
        request.setProperties(properties);

        try {
            Invoker invoker = new DefaultInvoker();
            InvocationResult result = invoker.execute(request);
            int exitCode = result.getExitCode();
            LOG.info("maven result " + exitCode + " exception: " + result.getExecutionException());
            assertEquals("Failed to invoke maven goals: " + goalList + " with properties: " + properties + ". Exit Code: ", 0, exitCode);
        } catch (MavenInvocationException e) {
            fail("Failed to invoke maven goals: " + goalList + " with properties: " + properties + ". Exception " + e, e);
        }
        String path = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar";
        return new File(localMavenRepo, path);
    }

    public void createProject(String archetype) throws Exception {
        String archetypeUri = "io.fabric8.archetypes:" + archetype + ":" + FABRIC8_ARCHETYPE_VERSION;

        LOG.info("Creating archetype: " + archetypeUri);

        RestUIContext context = new RestUIContext();
        UICommand projectNewCommand = commandFactory.getCommandByName(context, "project-new");

        File outputDir = new File(projectsOutputFolder, archetype);
        outputDir.mkdirs();

        CommandController controller = commandControllerFactory.createController(context, runtime, projectNewCommand);
        controller.initialize();

        String name = Strings.stripSuffix("my-" + archetype, "-archetype");
        controller.setValueFor("named", name);
        controller.setValueFor("topLevelPackage", "org.example");
        controller.setValueFor("version", "1.0.0-SNAPSHOT");
        controller.setValueFor("targetLocation", outputDir.getAbsolutePath());
        controller.setValueFor("buildSystem", "Maven");
        controller.setValueFor("type", "From Archetype Catalog");

        WizardCommandController wizardCommandController = assertWizardController(controller);
        validate(wizardCommandController);
        wizardCommandController = wizardCommandController.next();
        LOG.info("Next result: " + wizardCommandController);

/*
        InputComponent<?, ?> archetypeInput = wizardCommandController.getInputs().get("archetype");
        archetypeInput.get
*/

        wizardCommandController.setValueFor("catalog", "fabric8");
        wizardCommandController.setValueFor("archetype", archetypeUri);

        validate(wizardCommandController);
        try {

            Result result = wizardCommandController.execute();
            printResult(result);

            useProject(archetype, outputDir, name);
        } catch (Exception e) {
            LOG.error("Failed to create project " + archetypeUri + " " + e, e);
        }
    }

    protected void useProject(String archetype, File outputDir, String projectName) throws MavenInvocationException {
        File projectDir = new File(outputDir, projectName);
        File pom = new File(projectDir, "pom.xml");
        boolean hasPom = pom.exists() && pom.isFile();

        LOG.info("Now using project: " + archetype + " at folder: " + outputDir + " has pom.xml: " + hasPom);

        RestUIContext context = createUIContextForFolder(outputDir);
        Set<String> names = commandFactory.getCommandNames(context);

        LOG.info("Got command names: " + names);

        // turn off checking commands as that takes too long
        /*
        for (String name : names) {
            try {
                UICommand command = commandFactory.getCommandByName(context, name);
                boolean enabled = command.isEnabled(context);
                LOG.info("Command " + name + " enabled: " + enabled);
            } catch (Throwable e) {
                LOG.warn("Failed to check command " + name + ". " + e, e);
            }
        }*/

        // now lets try validate the devops-edit command
        //UICommand devOpsEdit = commandFactory.getCommandByName(context, "devops-edit");

        // the projects are already setup
        /*if (hasPom) {
            useCommand(context, "fabric8-setup", true);
        }
        useCommand(context, "fabric8-pipeline", false);
        */

        if (hasPom) {
            runMavenGoals(archetype, projectDir, "package");
        }
    }

    protected void runMavenGoals(String archetype, File outputDir, String... goals) throws MavenInvocationException {
        List<String> goalList = Arrays.asList(goals);
        LOG.info("Invoking maven with goals: " + goalList + " in folder: " + outputDir);
        File pomFile = new File(outputDir, "pom.xml");


        InvocationRequest request = new DefaultInvocationRequest();
        request.setLocalRepositoryDirectory(localMavenRepo);
        request.setInteractive(false);
        request.setPomFile(pomFile);
        request.setGoals(goalList);

        // lets use a dummy service name to avoid it being too long
        request.setMavenOpts("-Dfabric8.service.name=dummy-name");

        Invoker invoker = new DefaultInvoker();
        InvocationResult result = invoker.execute(request);
        int exitCode = result.getExitCode();
        LOG.info("maven result " + exitCode + " exception: " + result.getExecutionException());
        assertEquals("Failed to invoke maven goals: " + goalList + " in folder: " + outputDir + ". Exit Code: ", 0, exitCode);
    }

    protected void useCommand(RestUIContext context, String commandName, boolean shouldExecute) {
        try {
            UICommand command = commandFactory.getCommandByName(context, commandName);
            if (command == null) {
                LOG.warn("No such command! '" + commandName + "'");
                return;
            }
            CommandController controller = commandControllerFactory.createController(context, runtime, command);
            if (controller == null) {
                LOG.warn("No such controller! '" + commandName + "'");
                return;
            }
            controller.initialize();
            WizardCommandController wizardCommandController = assertWizardController(controller);

            Map<String, InputComponent<?, ?>> inputs = controller.getInputs();
            Set<Map.Entry<String, InputComponent<?, ?>>> entries = inputs.entrySet();
            for (Map.Entry<String, InputComponent<?, ?>> entry : entries) {
                String key = entry.getKey();
                InputComponent component = entry.getValue();
                Object value = InputComponents.getValueFor(component);
                Object completions = null;
                UICompleter<?> completer = InputComponents.getCompleterFor(component);
                if (completer != null) {
                    completions = completer.getCompletionProposals(context, component, "");
                }
                LOG.info(key + " = " + component + " value: " + value + " completions: " + completions);
            }
            validate(controller);
            wizardCommandController = wizardCommandController.next();

            if (shouldExecute) {
                Result result = controller.execute();
                printResult(result);
            }
        } catch (Exception e) {
            LOG.error("Failed to create the " + commandName + " controller! " + e, e);
        }
    }

    private RestUIContext createUIContextForFolder(File outputDir) {
        Resource<File> selection = resourceFactory.create(outputDir);
        return new RestUIContext(selection);
    }


    protected void printResult(Result result) {
        if (result instanceof CompositeResult) {
            CompositeResult compositeResult = (CompositeResult) result;
            List<Result> results = compositeResult.getResults();
            for (Result child : results) {
                printResult(child);
            }
        } else {
            LOG.info("Result: " + result.getMessage());
        }
    }

    private void validate(CommandController controller) {
        List<UIMessage> validate = controller.validate();
        if (!validate.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (UIMessage message : validate) {
                String description = message.getDescription();
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(description);
                LOG.error("Wizard is invalid: " + description);
            }
            Map<String, InputComponent<?, ?>> inputs = controller.getInputs();
            LOG.warn("Available inputs are: " + new TreeSet<>(inputs.keySet()));
            throw new IllegalStateException("Wizard invalid: " + builder.toString());
        }
    }
}

/**
 * Copyright 2005-2015 Red Hat, Inc.
 * <p/>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.forge.camel.commands;

import io.fabric8.forge.camel.commands.project.CamelGetRoutesXmlCommand;
import io.fabric8.forge.camel.commands.project.dto.ContextDto;
import io.fabric8.forge.camel.commands.project.dto.NodeDtoSupport;
import io.fabric8.forge.camel.commands.project.dto.NodeDtos;
import io.fabric8.utils.Files;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.resource.Resource;
import org.jboss.forge.addon.resource.ResourceFactory;
import org.jboss.forge.addon.ui.controller.CommandController;
import org.jboss.forge.addon.ui.result.Failed;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.test.UITestHarness;
import org.jboss.forge.arquillian.AddonDependencies;
import org.jboss.forge.arquillian.AddonDependency;
import org.jboss.forge.arquillian.archive.AddonArchive;
import org.jboss.forge.furnace.repositories.AddonDependencyEntry;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

import static org.junit.Assert.assertFalse;


@RunWith(Arquillian.class)
public class NewNodeXmlTest {

    @Inject
    private UITestHarness testHarness;

    @Inject
    private ProjectFactory projectFactory;

    @Inject
    private ResourceFactory resourceFactory;

    @Deployment
    @AddonDependencies({
            @AddonDependency(name = "org.jboss.forge.addon:maven"),
            @AddonDependency(name = "org.jboss.forge.addon:projects"),
            @AddonDependency(name = "org.jboss.forge.addon:ui"),
            @AddonDependency(name = "org.jboss.forge.addon:ui-test-harness"),
            @AddonDependency(name = "org.jboss.forge.addon:shell-test-harness"),
            @AddonDependency(name = "io.fabric8.forge:camel")
    })
    public static AddonArchive getDeployment() {
        AddonArchive archive = ShrinkWrap
                .create(AddonArchive.class)
                .addBeansXML()
                .addAsAddonDependencies(
                        AddonDependencyEntry.create("org.jboss.forge.furnace.container:cdi"),
                        AddonDependencyEntry.create("org.jboss.forge.addon:ui"),
                        AddonDependencyEntry.create("org.jboss.forge.addon:ui-test-harness"),
                        AddonDependencyEntry.create("org.jboss.forge.addon:shell-test-harness"),
                        AddonDependencyEntry.create("org.jboss.forge.addon:projects"),
                        AddonDependencyEntry.create("org.jboss.forge.addon:maven"),
                        AddonDependencyEntry.create("io.fabric8.forge:camel")
                );
        return archive;
    }

    @Test
    public void testCreateXmlNode() throws Exception {
        File basedir = getBaseDir();
        String projectName = "example-camel-spring";
        File projectDir = new File(basedir, "target/test-projects/" + projectName);
        File projectSourceDir = new File(basedir, "src/itests/" + projectName);
        Files.recursiveDelete(projectDir);
        io.fabric8.forge.addon.utils.Files.copy(projectSourceDir, projectDir);

        System.out.println("Copied project to " + projectDir);

        Resource<?> resource = resourceFactory.create(projectDir);
        Assert.assertNotNull("Should have found a resource", resource);

        Project project = projectFactory.findProject(resource);
        Assert.assertNotNull("Should have found a project", project);

        List<ContextDto> contexts = getRoutesXml(project);
        assertFalse("Should have loaded a camelContext", contexts.isEmpty());
    }

    protected List<ContextDto> getRoutesXml(Project project) throws Exception {
        CommandController command = testHarness.createCommandController(CamelGetRoutesXmlCommand.class, project.getRoot());
        command.initialize();
        command.setValueFor("format", "JSON");

        Result result = command.execute();
        assertFalse("Should not fail", result instanceof Failed);

        String message = result.getMessage();

        System.out.println();
        System.out.println();
        System.out.println("JSON: " + message);
        System.out.println();

        List<ContextDto> answer = NodeDtos.parseContexts(message);
        System.out.println();
        System.out.println();
        List<NodeDtoSupport> nodeList = NodeDtos.toNodeList(answer);
        for (NodeDtoSupport node : nodeList) {
            System.out.println(node.getLabel());
        }
        System.out.println();
        return answer;
    }

    public static File getBaseDir() {
        String dirName = System.getProperty("basedir", ".");
        return new File(dirName);
    }


}

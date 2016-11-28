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

import io.fabric8.forge.systest.support.FurnaceCallback;
import io.fabric8.forge.systest.support.Furnaces;
import io.fabric8.utils.Files;
import io.fabric8.utils.Strings;
import org.jboss.forge.furnace.Furnace;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 */
@Ignore("We have a maven plugin now to download the repository")
public class PopulateMavenRepositoryTest {
    private static final transient Logger LOG = LoggerFactory.getLogger(PopulateMavenRepositoryTest.class);
    public static final String TEST_ARCHETYPE_SYSTEM_PROPERTY = "test.archetype";

    protected String baseDir = System.getProperty("basedir", ".");
    protected File localMavenRepo = new File(baseDir, "localMavenRepo");

    @Test
    public void testPopulateMavenRepo() throws Exception {
        // lets point to a local maven repo
        localMavenRepo.mkdirs();
        System.setProperty("maven.repo.local", localMavenRepo.getAbsolutePath());

        Furnaces.withFurnace(new FurnaceCallback<String>() {

            @Override
            public String invoke(Furnace furnace) throws Exception {
                createProjects(furnace);
                return null;
            }
        });
    }

    protected void createProjects(Furnace furnace) throws Exception {
        File projectsOutputFolder = new File(baseDir, "target/createdProjects");
        Files.recursiveDelete(projectsOutputFolder);

        ProjectGenerator generator = new ProjectGenerator(furnace, projectsOutputFolder, localMavenRepo);
        File archetypeJar = generator.getArtifactJar("io.fabric8.archetypes", "archetypes-catalog", ProjectGenerator.FABRIC8_ARCHETYPE_VERSION);

        List<String> archetypes = getArchetypesFromJar(archetypeJar);
        assertThat(archetypes).describedAs("Archetypes to create").isNotEmpty();

        String testArchetype = System.getProperty(TEST_ARCHETYPE_SYSTEM_PROPERTY, "");
        if (Strings.isNotBlank(testArchetype)) {
            LOG.info("Due to system property: '" + TEST_ARCHETYPE_SYSTEM_PROPERTY + "' we will just run the test on archetype: " + testArchetype);
            generator.createProjectFromArchetype(testArchetype);
        } else {
            for (String archetype : archetypes) {
                // TODO fix failing archetypes...
                if (!archetype.startsWith("jboss-fuse-") && !archetype.startsWith("swarm")) {
                    generator.createProjectFromArchetype(archetype);
                }
            }
        }

        removeSnapshotFabric8Artifacts();


        List<File> failedFolders = generator.getFailedFolders();
        if (failedFolders.size() > 0) {
            LOG.error("Failed to build all the projects!!!");
            for (File failedFolder : failedFolders) {
                LOG.error("Failed to build project: " + failedFolder.getName());
            }
        }
    }

    protected List<String> getArchetypesFromJar(File archetypeJar) throws IOException {
        assertThat(archetypeJar).exists();
        JarFile jar = new JarFile(archetypeJar);
        String entryName = "archetype-catalog.xml";
        ZipEntry entry = jar.getEntry(entryName);
        assertThat(entry).describedAs("Missing entry " + entryName + " in jar " + archetypeJar).isNotNull();

        SortedSet<String> artifactIds = new TreeSet<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource is = new InputSource(jar.getInputStream(entry));
            Document doc = builder.parse(is);
            NodeList artifactTags = doc.getElementsByTagName("artifactId");
            for (int i = 0, size = artifactTags.getLength(); i < size; i++) {
                Node element = artifactTags.item(i);
                String artifactId = element.getTextContent();
                if (Strings.isNotBlank(artifactId)) {
                    artifactIds.add(artifactId);
                }
            }
        } catch (Exception e) {
            fail("Failed to parse " + entryName + " in jar " + archetypeJar + ". Exception " + e, e);
        }
        return new ArrayList<>(artifactIds);
    }

    protected void removeSnapshotFabric8Artifacts() {
        File fabric8Folder = new File(localMavenRepo, "io/fabric8");
        if (Files.isDirectory(fabric8Folder)) {
            File[] artifactFolders = fabric8Folder.listFiles();
            if (artifactFolders != null) {
                for (File artifactFolder : artifactFolders) {
                    File[] versionFolders = artifactFolder.listFiles();
                    if (versionFolders != null) {
                        for (File versionFolder : versionFolders) {
                            if (versionFolder.getName().toUpperCase().endsWith("-SNAPSHOT")) {
                                LOG.info("Removing snapshot version from local maven repo: " + versionFolder);
                                Files.recursiveDelete(versionFolder);
                            }
                        }
                    }
                }
            }
        }
    }
}

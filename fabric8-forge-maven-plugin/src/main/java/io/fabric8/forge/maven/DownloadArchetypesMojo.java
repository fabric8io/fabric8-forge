/**
 * Copyright 2005-2015 Red Hat, Inc.
 * <p>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.forge.maven;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.archetype.catalog.ArchetypeCatalog;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.codehaus.plexus.util.FileUtils;

/**
 * Maven plugin to download all the fabric8 artifacts from the fabric8 archetype catalog
 * into a local maven repository which can be embedded in the docker image, so the
 * artifacts are pre-downloaded in fabric8-forge.
 *
 * @goal download
 */
public class DownloadArchetypesMojo extends AbstractMojo {

    /**
     * The maven project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * Maven ProjectHelper.
     *
     * @component
     * @readonly
     */
    private MavenProjectHelper projectHelper;

    /**
     * Local maven repository directory
     *
     * @parameter default-value="localMavenRepo"
     */
    private String localRepositoryDirectory;

    /**
     * Execute goal.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException execution of the main class or one of the
     *                                                        threads it generated failed.
     * @throws org.apache.maven.plugin.MojoFailureException   something bad happened...
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Downloading fabric8 maven artifacts to local repository: " + localRepositoryDirectory);

        File repo = init();

        // find the fabric8 artifact
        Artifact artifact = findFabric8Archetype(project);

        if (artifact != null) {
            ArchetypeCatalog catalog;
            try {
                catalog = FabricArchetypeCatalogFactory.getArchetypeCatalog(artifact);
            } catch (Exception e) {
                getLog().warn("Error downloading archetype catalog due " + e.getMessage(), e);
                return;
            }

            if (catalog != null) {
                getLog().info("Catalog contains " + catalog.getArchetypes().size() + " archetypes");

                for (Archetype a : catalog.getArchetypes()) {
                    try {
                        download(repo, a);
                    } catch (Exception e) {
                        getLog().warn("Error downloading " + a + " due " + e.getMessage(), e);
                    }
                }
            }
        }

        getLog().info("Download complete");
    }

    private Artifact findFabric8Archetype(MavenProject project) {
        Iterator it = project.getDependencyArtifacts().iterator();
        while (it.hasNext()) {
            Artifact a = (Artifact) it.next();
            if ("io.fabric8.archetypes".equals(a.getGroupId()) && "archetypes-catalog".equals(a.getArtifactId())) {
                return a;
            }
        }
        return null;
    }

    private File init() throws MojoFailureException {
        File m2 = new File(localRepositoryDirectory);
        if (!m2.exists()) {
            m2.mkdir();
        }
        return m2;
    }

    private void download(File repo, Archetype archetype) throws Exception {
        getLog().info("Downloading... " + archetype);

        // skip redhat only as they are not in maven central
        if (archetype.getArtifactId().startsWith("karaf2-")) {
            getLog().warn("Skipping Red Hat JBoss Fuse archetype: " + archetype);
            return;
        }

        // delete dummy directory
        FileUtils.deleteDirectory(new File("target/dummy"));

        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(null);

        String goal = String.format("archetype:generate -DarchetypeGroupId=%s -DarchetypeArtifactId=%s -DarchetypeVersion=%s -DgroupId=com.foo -DartifactId=dummy", archetype.getGroupId(), archetype.getArtifactId(), archetype.getVersion());

        request.setGoals(Arrays.asList(goal));
        request.setBaseDirectory(new File("target"));
        request.setInteractive(false);
        request.setShowErrors(true);
        request.setLocalRepositoryDirectory(repo);

        Invoker invoker = new DefaultInvoker();
        invoker.execute(request);

        // these are non java projects and therefore do not have a pom.xml file
        if (archetype.getArtifactId().equals("django-example-archetype")
                || archetype.getArtifactId().equals("dotnet-example-archetype")
                || archetype.getArtifactId().equals("golang-example-archetype")
                || archetype.getArtifactId().equals("node-example-archetype")
                || archetype.getArtifactId().equals("php-example-archetype")
                || archetype.getArtifactId().equals("rails-example-archetype")
                || archetype.getArtifactId().equals("swift-example-archetype")) {
            return;
        }

        // assert the Java project is created with a maven pom.xml file
        File dummy = new File("target/dummy/pom.xml");
        if (!dummy.exists()) {
            getLog().warn("Created project in target/dummy does not have a pom.xml file");
            return;
        }

        request = new DefaultInvocationRequest();
        request.setPomFile(dummy);

        // this goal will download the JARs and not build/test the project which is slower and can potential fail
        goal = String.format("dependency:tree");

        request.setGoals(Arrays.asList(goal));
        request.setBaseDirectory(new File("target/dummy"));
        request.setInteractive(false);
        request.setShowErrors(true);
        request.setLocalRepositoryDirectory(repo);

        invoker = new DefaultInvoker();
        invoker.execute(request);
    }

}

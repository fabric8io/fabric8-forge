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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.codehaus.plexus.util.FileUtils;

/**
 * @goal download
 */
public class DownloadArchetypesMojo extends AbstractMojo {

    private static final String M2_DIR = "target/local-m2";

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
     * Execute goal.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException execution of the main class or one of the
     *                                                        threads it generated failed.
     * @throws org.apache.maven.plugin.MojoFailureException   something bad happened...
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Download Maven Artifacts from Camel and fabric8 catalogs to local m2 repository ...");

        File repo = init();
        List<CatalogDTO> catalogs = findCatalogs();
        for (CatalogDTO dto : catalogs) {
            try {
                download(repo, dto);
            } catch (Exception e) {
                throw new MojoFailureException("Error downloading " + dto + " due " + e.getMessage(), e);
            }
        }

        getLog().info("Done");
    }

    private File init() throws MojoFailureException {
        File m2 = new File(M2_DIR);

        // delete directory
        try {
            FileUtils.deleteDirectory(m2);
        } catch (IOException e) {
            throw new MojoFailureException("Error deleting directory: " + M2_DIR);
        }

        // create empty dir
        m2.mkdir();

        return m2;
    }

    /**
     * Find the fabric8 and camel catalogs to use
     */
    private List<CatalogDTO> findCatalogs() {
        List<CatalogDTO> answer = new ArrayList<>();
        answer.add(new CatalogDTO("org.apache.camel.archetypes", "camel-archetype-blueprint", "2.18.0"));
        return answer;
    }

    private void download(File repo, CatalogDTO dto) throws Exception {
        getLog().info("Downloading... " + dto);

        // delete dummy directory
        FileUtils.deleteDirectory(new File("target/dummy"));

        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(null);

        String goal = String.format("archetype:generate -DarchetypeGroupId=%s -DarchetypeArtifactId=%s -DarchetypeVersion=%s -DgroupId=com.foo -DartifactId=dummy", dto.getGroupId(), dto.getArtifactId(), dto.getVersion());

        request.setGoals(Arrays.asList(goal));
        request.setBaseDirectory(new File("target"));
        request.setInteractive(false);
        request.setShowErrors(true);
        request.setLocalRepositoryDirectory(repo);

        Invoker invoker = new DefaultInvoker();
        invoker.execute(request);

        // assert the project is created

        File dummy = new File("target/dummy/pom.xml");
        if (!dummy.exists()) {
            throw new MavenInvocationException("Created project in target/dummy does not have a pom.xml file");
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

        // after creating the project then need to do a maven build to force download
    }

}

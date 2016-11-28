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
package io.fabric8.forge.rest.downloader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;

/**
 * Main class to pre-download all the Maven archetypes for the fabric8 and camel catalog we use in fabric8-forge
 * new-project wizard so we can pre-populate the local m2/repository in the docker image.
 * The project-new wizard works faster as it does not need internet connection and to download the JARs
 * on the first command.
 */
public class DownloadArchetypesMain {

    // TODO: move to separate maven module / maven plugin
    // TODO: add logging
    // TODO: add the two catalogs and download their XML and parse it for elements
    // TODO: .. make it dynamic versioned so we grab the version as arg line

    private static final String M2_DIR = "target/local-m2";

    public static void main(String[] args) throws Exception {
        DownloadArchetypesMain me = new DownloadArchetypesMain();
        File repo = me.init();
        List<CatalogDTO> catalogs = me.findCatalogs();
        for (CatalogDTO dto : catalogs) {
            me.download(repo, dto);
        }
    }

    private File init() throws IOException {
        File m2 = new File(M2_DIR);

        // delete directory
        FileUtils.deleteDirectory(m2);

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

    private class CatalogDTO {
        private String groupId;
        private String artifactId;
        private String version;

        public CatalogDTO(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }


}

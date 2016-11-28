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
package io.fabric8.forge.rest.main;

import java.io.File;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class MavenEmbeddedTest {

    @Test
    public void testMavenEmbedded() throws Exception {

        File m2 = new File("target/local-m2");

        // delete directory
        FileUtils.deleteDirectory(m2);
        FileUtils.deleteDirectory(new File("target/dummy"));

        // create empty dir
        m2.mkdir();

        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(null);
        request.setGoals(Arrays.asList("archetype:generate -DarchetypeGroupId=org.apache.camel.archetypes -DarchetypeArtifactId=camel-archetype-blueprint -DarchetypeVersion=2.18.0 -DgroupId=com.foo -DartifactId=dummy"));
        request.setBaseDirectory(new File("target"));
        request.setInteractive(false);
        request.setShowErrors(true);
        request.setLocalRepositoryDirectory(m2);

        Invoker invoker = new DefaultInvoker();
        invoker.execute(request);

        // should create project
        File pom = new File("target/dummy/pom.xml");
        Assert.assertTrue("Should create the project", pom.exists());
    }
}

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
package io.fabric8.forge.camel.dto;

import io.fabric8.forge.camel.commands.project.dto.ContextDto;
import io.fabric8.forge.camel.commands.project.dto.NodeDto;
import io.fabric8.forge.camel.commands.project.dto.NodeDtos;
import io.fabric8.utils.Files;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NodeTest {

    @Test
    public void testNodes() throws Exception {
        File jsonFile = new File(getBaseDir(), "src/test/resources/io/fabric8/forge/camel/dto/nodes.json");
        assertTrue("Could not find file " +jsonFile, Files.isFile(jsonFile));

        List<ContextDto> contexts = NodeDtos.parseContexts(jsonFile);
        assertFalse("Should have loaded a camelContext", contexts.isEmpty());

        List<NodeDto> nodeList = NodeDtos.toNodeList(contexts);
        assertFalse("Should have created a not empty node list", nodeList.isEmpty());

        for (NodeDto node : nodeList) {
            System.out.println(node.getLabel());
        }
    }

    public static File getBaseDir() {
        String dirName = System.getProperty("basedir", ".");
        return new File(dirName);
    }

}

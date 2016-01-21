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
package io.fabric8.forge.camel.xml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.fabric8.forge.camel.commands.project.helper.XmlRouteParser;
import io.fabric8.forge.camel.commands.project.model.CamelEndpointDetails;
import org.junit.Assert;
import org.junit.Test;

public class XmlRouteTest {

    @Test
    public void testXml() throws Exception {
        List<CamelEndpointDetails> endpoints = new ArrayList<>();

        InputStream is = new FileInputStream("src/test/resources/io/fabric8/forge/camel/xml/mycamel.xml");
        String fqn = "src/test/resources/io/fabric8/forge/camel/xml/mycamel.xml";
        String baseDir = "src/test/resources";
        XmlRouteParser.parseXmlRouteEndpoints(is, baseDir, fqn, endpoints);

        for (CamelEndpointDetails detail : endpoints) {
            System.out.println(detail.getEndpointUri());
        }
        Assert.assertEquals("stream:in?promptMessage=Enter something:", endpoints.get(0).getEndpointUri());
        Assert.assertEquals("stream:out", endpoints.get(1).getEndpointUri());
    }

}

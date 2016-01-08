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
package io.fabric8.camel.tooling.util;

import java.io.File;
import java.io.FileReader;
import java.util.List;

import org.apache.camel.model.RouteDefinition;
import org.junit.Test;
import org.springframework.util.FileCopyUtils;

import static org.junit.Assert.assertTrue;

public class NamespacePreserveTest extends RouteXmlTestSupport {

    @Test
    public void testUpdateAnXmlAndKeepNamespaces() throws Exception {
        File file = new File(getBaseDir(), "src/test/resources/namespaceRoute.xml");
        XmlModel xm = assertRoutes(file, 1, null);

        // now lets modify the xml
        List<RouteDefinition> definitionList = xm.getRouteDefinitionList();
        RouteDefinition route = new RouteDefinition().from("file:foo").to("file:bar");
        definitionList.add(route);

        System.out.println("Routes now: " + xm.getRouteDefinitionList());

        String text = FileCopyUtils.copyToString(new FileReader(file));

        RouteXml helper = new RouteXml();
        String newText = helper.marshalToText(text, definitionList);

        System.out.println("newText: " + newText);

        assertTrue("Generated XML has missing XML namespace declaration " + "http://acme.com/foo", newText.contains("http://acme.com/foo"));
        assertTrue("Generated XML has missing XML namespace declaration " + "urn:barNamespace", newText.contains("urn:barNamespace"));
    }

}

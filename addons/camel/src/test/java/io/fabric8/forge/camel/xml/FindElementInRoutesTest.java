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

import io.fabric8.forge.camel.commands.project.helper.CamelXmlHelper;
import io.fabric8.forge.camel.commands.project.helper.XmlRouteParser;
import io.fabric8.forge.camel.commands.project.model.CamelEndpointDetails;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Element;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;

public class FindElementInRoutesTest {

    @Test
    public void testXml() throws Exception {
        InputStream is = new FileInputStream("src/test/resources/io/fabric8/forge/camel/xml/myroutes.xml");
        String key = "_camelContext1/cbr-route/_from1";
        Element element = CamelXmlHelper.getSelectedCamelElementNode(key, is);
        assertNotNull("Could not find Element for key " + key, element);

        System.out.println("Found element " + element.getTagName());
    }

}

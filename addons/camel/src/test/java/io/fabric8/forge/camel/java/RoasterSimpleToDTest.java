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
package io.fabric8.forge.camel.java;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.fabric8.forge.camel.commands.project.helper.CamelJavaParserHelper;
import io.fabric8.forge.camel.commands.project.helper.ParserResult;
import io.fabric8.forge.camel.commands.project.helper.RouteBuilderParser;
import io.fabric8.forge.camel.commands.project.model.CamelEndpointDetails;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.junit.Assert;
import org.junit.Test;

public class RoasterSimpleToDTest {

    @Test
    public void parse() throws Exception {
        JavaClassSource clazz = (JavaClassSource) Roaster.parse(new File("src/test/java/io/fabric8/forge/camel/java/MySimpleToDRoute.java"));
        MethodSource<JavaClassSource> method = CamelJavaParserHelper.findConfigureMethod(clazz);

        List<CamelEndpointDetails> details = new ArrayList<CamelEndpointDetails>();
        RouteBuilderParser.parseRouteBuilderEndpoints(clazz, ".", "src/test/java/io/fabric8/forge/camel/java/MySimpleToDRoute.java", details);
        System.out.println(details);

        List<ParserResult> list = CamelJavaParserHelper.parseCamelConsumerUris(method, true, true);
        for (ParserResult result : list) {
            System.out.println("Consumer: " + result.getElement());
        }
        Assert.assertEquals("direct:start", list.get(0).getElement());

        list = CamelJavaParserHelper.parseCamelProducerUris(method, true, true);
        for (ParserResult result : list) {
            System.out.println("Producer: " + result.getElement());
        }
        Assert.assertEquals("log:a", list.get(0).getElement());
        Assert.assertEquals("log:b", list.get(1).getElement());
        Assert.assertEquals("log:c", list.get(2).getElement());
        Assert.assertEquals(3, list.size());

        Assert.assertEquals(4, details.size());
        Assert.assertEquals("direct:start", details.get(0).getEndpointUri());
        Assert.assertEquals("log:a", details.get(1).getEndpointUri());
        Assert.assertEquals("log:b", details.get(2).getEndpointUri());
        Assert.assertEquals("log:c", details.get(3).getEndpointUri());
    }

}

/**
 * Copyright 2005-2015 Red Hat, Inc.
 * <p/>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.forge.camel.commands.project.completer;

import java.io.InputStream;
import java.util.List;
import java.util.function.Function;

import io.fabric8.forge.camel.commands.project.AbstractCamelProjectCommand;
import io.fabric8.forge.camel.commands.project.helper.PoorMansLogger;
import io.fabric8.forge.camel.commands.project.helper.XmlRouteParser;
import io.fabric8.forge.camel.commands.project.model.CamelEndpointDetails;
import org.jboss.forge.addon.projects.facets.ResourcesFacet;
import org.jboss.forge.addon.resource.FileResource;
import org.jboss.forge.addon.resource.Resource;
import org.jboss.forge.addon.resource.visit.ResourceVisitor;
import org.jboss.forge.addon.resource.visit.VisitContext;

public class XmlResourcesCamelEndpointsVisitor implements ResourceVisitor {

    private static final PoorMansLogger LOG = new PoorMansLogger(false);

    private final ResourcesFacet facet;
    private final List<CamelEndpointDetails> endpoints;
    private final Function<String, Boolean> filter;

    public XmlResourcesCamelEndpointsVisitor(ResourcesFacet facet, List<CamelEndpointDetails> endpoints, Function<String, Boolean> filter) {
        this.facet = facet;
        this.endpoints = endpoints;
        this.filter = filter;
    }

    @Override
    public void visit(VisitContext visitContext, Resource<?> resource) {
        // skip directories
        if (resource instanceof FileResource) {
            if (((FileResource) resource).isDirectory()) {
                return;
            }
        }

        String name = resource.getFullyQualifiedName();
        name = AbstractCamelProjectCommand.asRelativeFile(name, null, facet, null);
        LOG.info("Resource name " + name);

        if (name.endsWith(".xml")) {
            boolean include = true;
            if (filter != null) {
                Boolean out = filter.apply(name);
                LOG.info("Filter " + name + " -> " + out);
                include = out == null || out;
            }

            if (include) {
                boolean camel = containsCamelRoutes(resource);
                if (camel) {
                    // find all the endpoints (currently only <endpoint> and within <route>)
                    try {
                        InputStream is = resource.getResourceInputStream();
                        String fqn = resource.getFullyQualifiedName();
                        String baseDir = facet.getResourceDirectory().getFullyQualifiedName();
                        XmlRouteParser.parseXmlRouteEndpoints(is, baseDir, fqn, endpoints);
                    } catch (Throwable e) {
                        // ignore
                    }
                }
            }
        }
    }

    public static boolean containsCamelRoutes(Resource<?> resource) {
        // must contain <camelContext... or <routes...
        String contents = resource.getContents();
        return contents.contains("<camelContext") || contents.contains("<routes");
    }
}

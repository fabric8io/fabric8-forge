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

import java.util.List;
import java.util.function.Function;

import io.fabric8.forge.camel.commands.project.helper.PoorMansLogger;
import io.fabric8.forge.camel.commands.project.helper.RouteBuilderParser;
import io.fabric8.forge.camel.commands.project.model.CamelEndpointDetails;
import org.jboss.forge.addon.parser.java.facets.JavaSourceFacet;
import org.jboss.forge.addon.parser.java.resources.JavaResource;
import org.jboss.forge.addon.parser.java.resources.JavaResourceVisitor;
import org.jboss.forge.addon.resource.visit.VisitContext;
import org.jboss.forge.roaster.model.source.JavaClassSource;

public class RouteBuilderCamelEndpointsVisitor extends JavaResourceVisitor {

    private static final PoorMansLogger LOG = new PoorMansLogger(false);

    private final JavaSourceFacet facet;
    private final List<CamelEndpointDetails> endpoints;
    private final Function<String, Boolean> filter;

    public RouteBuilderCamelEndpointsVisitor(JavaSourceFacet facet, List<CamelEndpointDetails> endpoints, Function<String, Boolean> filter) {
        this.facet = facet;
        this.endpoints = endpoints;
        this.filter = filter;
    }

    @Override
    public void visit(VisitContext visitContext, JavaResource resource) {
        try {
            JavaClassSource clazz = resource.getJavaType();
            String fqn = resource.getFullyQualifiedName();
            String name = clazz.getQualifiedName();
            String baseDir = facet.getSourceDirectory().getFullyQualifiedName();

            boolean include = true;
            if (filter != null) {
                Boolean out = filter.apply(name);
                LOG.info("Filter " + name + " -> " + out);
                include = out == null || out;
            }

            if (include) {
                RouteBuilderParser.parseRouteBuilderEndpoints(clazz, baseDir, fqn, endpoints);
            }
        } catch (Throwable e) {
            // ignore
        }
    }


}

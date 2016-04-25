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
package io.fabric8.forge.devops.springboot;

import java.util.ArrayList;
import java.util.List;

import org.jboss.forge.addon.parser.java.facets.JavaCompilerFacet;
import org.jboss.forge.addon.parser.java.facets.JavaSourceFacet;
import org.jboss.forge.addon.projects.AbstractProjectType;
import org.jboss.forge.addon.projects.ProjectFacet;
import org.jboss.forge.addon.projects.facets.DependencyFacet;
import org.jboss.forge.addon.projects.facets.MetadataFacet;
import org.jboss.forge.addon.projects.facets.PackagingFacet;
import org.jboss.forge.addon.projects.facets.ResourcesFacet;
import org.jboss.forge.addon.projects.facets.WebResourcesFacet;
import org.jboss.forge.addon.projects.stacks.Stack;
import org.jboss.forge.addon.ui.wizard.UIWizardStep;

public class SpringBootProjectType extends AbstractProjectType {

    @Override
    public boolean supports(Stack stack) {
        return false;
    }

    @Override
    public String getType() {
        return "Spring Boot";
    }

    @Override
    public Iterable<Class<? extends ProjectFacet>> getRequiredFacets() {
        List<Class<? extends ProjectFacet>> result = new ArrayList<Class<? extends ProjectFacet>>(7);
        result.add(MetadataFacet.class);
        result.add(PackagingFacet.class);
        result.add(DependencyFacet.class);
        result.add(ResourcesFacet.class);
        result.add(WebResourcesFacet.class);
        result.add(JavaSourceFacet.class);
        result.add(JavaCompilerFacet.class);
        return result;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public Class<? extends UIWizardStep> getSetupFlow() {
        return SpringBootSetupFlow.class;
    }

    @Override
    public String toString() {
        return "spring-boot";
    }
}

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
package io.fabric8.forge.camel.commands.project.completer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import io.fabric8.forge.camel.commands.project.helper.CamelXmlHelper;
import org.jboss.forge.addon.projects.facets.ResourcesFacet;
import org.jboss.forge.addon.resource.visit.ResourceVisitor;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIValidationContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.UICompleter;
import org.jboss.forge.addon.ui.input.UIInput;

/**
 * Spring Boot application properties/yaml file completer
 */
public class SpringBootConfigurationFileCompleter implements UICompleter<String> {

    private final Set<String> files = new TreeSet<String>();
    private final Set<String> directories = new TreeSet<String>();

    public SpringBootConfigurationFileCompleter(final ResourcesFacet facet, Function<String, Boolean> filter) {
        if (facet != null) {
            ResourceVisitor visitor = new SpringBootConfigurationResourcesFilesVisitor(facet, files, directories, filter);
            facet.visitResources(visitor);
        }
    }

    public Set<String> getDirectories() {
        return directories;
    }

    public Set<String> getFiles() {
        return files;
    }

    @Override
    public Iterable<String> getCompletionProposals(UIContext context, InputComponent input, String value) {
        List<String> answer = new ArrayList<String>();

        for (String name : files) {
            if (value == null || name.startsWith(value)) {
                answer.add(name);
            }
        }

        return answer;
    }

    /**
     * Validates that the given selected directory and fileName are valid and that the file doesn't already exist
     */
    public void validateFileDoesNotExist(UIInput<String> directory, UIInput<String> fileName, UIValidationContext validator) {
        String resourcePath = CamelXmlHelper.createFileName(directory, fileName);
        if (files.contains(resourcePath)) {
            validator.addValidationError(fileName, "A file with that name already exists!");
        }
    }
}

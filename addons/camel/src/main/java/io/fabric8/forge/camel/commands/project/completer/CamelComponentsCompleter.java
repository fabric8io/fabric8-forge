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
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import io.fabric8.forge.addon.utils.CamelProjectHelper;
import io.fabric8.forge.camel.commands.project.dto.ComponentDto;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.JSonSchemaHelper;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.UICompleter;
import org.jboss.forge.addon.ui.input.UIInput;

import static io.fabric8.forge.addon.utils.CamelProjectHelper.findCamelArtifacts;
import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.componentsFromArtifact;
import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.createComponentDto;

public class CamelComponentsCompleter implements UICompleter<ComponentDto> {

    private final Project project;
    private final CamelCatalog camelCatalog;
    private final UIInput<String> filter;
    private final boolean excludeComponentsOnClasspath;
    private final boolean includeCatalogComponents;
    private final boolean consumerOnly;
    private final boolean producerOnly;
    private final Dependency core;

    public CamelComponentsCompleter(Project project, CamelCatalog camelCatalog, UIInput<String> filter,
                                    boolean excludeComponentsOnClasspath, boolean includeCatalogComponents,
                                    boolean consumerOnly, boolean producerOnly) {
        this.project = project;
        this.camelCatalog = camelCatalog;
        this.filter = filter;
        this.excludeComponentsOnClasspath = excludeComponentsOnClasspath;
        this.includeCatalogComponents = includeCatalogComponents;
        this.consumerOnly = consumerOnly;
        this.producerOnly = producerOnly;

        // need to find camel-core so we known the camel version
        core = CamelProjectHelper.findCamelCoreDependency(project);
    }

    @Override
    public Iterable<ComponentDto> getCompletionProposals(UIContext context, InputComponent input, String value) {
        if (core == null) {
            return null;
        }

        List<String> names = getComponentNames();

        // filter non matching names first
        List<String> filtered = new ArrayList<String>();
        for (String name : names) {
            if (value == null || name.startsWith(value)) {
                filtered.add(name);
            }
        }

        if (consumerOnly) {
            filtered = filterByConsumerOnly(filtered);
        }
        if (producerOnly) {
            filtered = filterByProducerOnly(filtered);
        }

        filtered = filterByName(filtered);
        filtered = filterByLabel(filtered, filter.getValue());

        List<ComponentDto> answer = new ArrayList<>();
        for (String filter : filtered) {
            ComponentDto dto = createComponentDto(camelCatalog, filter);
            answer.add(dto);
        }
        return answer;
    }

    public Iterable<ComponentDto> getValueChoices(String label) {
        // need to find camel-core so we known the camel version
        Dependency core = CamelProjectHelper.findCamelCoreDependency(project);
        if (core == null) {
            return null;
        }

        List<String> names = getComponentNames();

        if (label != null && !"<all>".equals(label)) {
            names = filterByLabel(names, label);
        }

        List<ComponentDto> answer = new ArrayList<>();
        for (String filter : names) {
            ComponentDto dto = createComponentDto(camelCatalog, filter);
            answer.add(dto);
        }
        return answer;
    }

    protected List<String> getComponentNames() {
        List<String> names;
        if (includeCatalogComponents) {
            // find all available component names
            names = camelCatalog.findComponentNames();

            // filter out existing components we already have
            if (excludeComponentsOnClasspath) {
                Set<Dependency> artifacts = findCamelArtifacts(project);
                for (Dependency dep : artifacts) {
                    Set<String> components = componentsFromArtifact(camelCatalog, dep.getCoordinate().getArtifactId());
                    names.removeAll(components);
                }
            }
        } else {
            SortedSet<String> set = new TreeSet<>();
            Set<Dependency> artifacts = findCamelArtifacts(project);
            for (Dependency dep : artifacts) {
                Set<String> components = componentsFromArtifact(camelCatalog, dep.getCoordinate().getArtifactId());
                set.addAll(components);
            }
            names = new ArrayList<>(set);
        } return names;
    }

    private List<String> filterByConsumerOnly(List<String> choices) {
        List<String> answer = new ArrayList<String>();

        for (String name : choices) {
            String json = camelCatalog.componentJSonSchema(name);
            // yes its correct we grab the producer value
            String producerOnly = findProducerOnly(json);
            if (producerOnly != null && "true".equals(producerOnly)) {
                // its not able to consume so skip it
                continue;
            }
            answer.add(name);
        }

        return answer;
    }

    private List<String> filterByProducerOnly(List<String> choices) {
        List<String> answer = new ArrayList<String>();

        for (String name : choices) {
            String json = camelCatalog.componentJSonSchema(name);
            // yes its correct we grab the consumer value
            String consumerOnly = findConsumerOnly(json);
            if (consumerOnly != null && "true".equals(consumerOnly)) {
                // its not able to produce so skip it
                continue;
            }
            answer.add(name);
        }

        return answer;
    }

    private List<String> filterByName(List<String> choices) {
        List<String> answer = new ArrayList<String>();

        // filter names which are already on the classpath, or do not match the optional filter by label input
        for (String name : choices) {
            // skip if we already have the dependency
            boolean already = false;
            if (excludeComponentsOnClasspath) {
                String json = camelCatalog.componentJSonSchema(name);
                String artifactId = findArtifactId(json);
                if (artifactId != null) {
                    already = CamelProjectHelper.hasDependency(project, "org.apache.camel", artifactId);
                }
            }

            if (!already) {
                answer.add(name);
            }
        }

        return answer;
    }

    private List<String> filterByLabel(List<String> choices, String label) {
        if (label == null || label.isEmpty()) {
            return choices;
        }

        List<String> answer = new ArrayList<String>();

        // filter names which are already on the classpath, or do not match the optional filter by label input
        for (String name : choices) {
            String json = camelCatalog.componentJSonSchema(name);
            String labels = findLabel(json);
            if (labels != null) {
                for (String target : labels.split(",")) {
                    if (target.startsWith(label)) {
                        answer.add(name);
                        break;
                    }
                }
            } else {
                // no label so they all match
                answer.addAll(choices);
            }
        }

        return answer;
    }

    private static String findArtifactId(String json) {
        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("component", json, false);
        for (Map<String, String> row : data) {
            if (row.get("artifactId") != null) {
                return row.get("artifactId");
            }
        }
        return null;
    }

    private static String findConsumerOnly(String json) {
        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("component", json, false);
        for (Map<String, String> row : data) {
            if (row.get("consumerOnly") != null) {
                return row.get("consumerOnly");
            }
        }
        return null;
    }

    private static String findProducerOnly(String json) {
        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("component", json, false);
        for (Map<String, String> row : data) {
            if (row.get("producerOnly") != null) {
                return row.get("producerOnly");
            }
        }
        return null;
    }

    private static String findLabel(String json) {
        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("component", json, false);
        for (Map<String, String> row : data) {
            if (row.get("label") != null) {
                return row.get("label");
            }
        }
        return null;
    }

}

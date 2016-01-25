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

import io.fabric8.forge.addon.utils.CamelProjectHelper;
import io.fabric8.forge.camel.commands.project.dto.EipDto;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.JSonSchemaHelper;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.UICompleter;

import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.createEipDto;

public class CamelEipsCompleter implements UICompleter<EipDto> {

    private final Project project;
    private final CamelCatalog camelCatalog;
    private final Dependency core;

    public CamelEipsCompleter(Project project, CamelCatalog camelCatalog) {
        this.project = project;
        this.camelCatalog = camelCatalog;
        // need to find camel-core so we known the camel version
        core = CamelProjectHelper.findCamelCoreDependency(project);
    }

    @Override
    public Iterable<EipDto> getCompletionProposals(UIContext context, InputComponent input, String value) {
        if (core == null) {
            return null;
        }

        List<EipDto> answer = new ArrayList<>();

        // find all available model names
        List<String> names = camelCatalog.findModelNames();

        // filter non matching names first
        List<String> filtered = new ArrayList<String>();
        for (String name : names) {
            if (value == null || name.startsWith(value)) {
                filtered.add(name);
            }
        }

        for (String name : filtered) {
            String json = camelCatalog.modelJSonSchema(name);
            EipDto dto = createEipDto(camelCatalog, json);
            answer.add(dto);
        }

        return answer;
    }

    public Iterable<EipDto> getValueChoices(String label) {
        if (core == null) {
            return null;
        }

        List<String> names = camelCatalog.findModelNames();

        if (label != null && !"<all>".equals(label)) {
            names = filterByLabel(names, label);
        }

        List<EipDto> answer = new ArrayList<>();
        for (String name : names) {
            EipDto dto = createEipDto(camelCatalog, name);
            answer.add(dto);
        }

        return answer;
    }

    private List<String> filterByLabel(List<String> choices, String label) {
        if (label == null || label.isEmpty()) {
            return choices;
        }

        List<String> answer = new ArrayList<String>();

        for (String name : choices) {
            String json = camelCatalog.modelJSonSchema(name);
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

    private static String findLabel(String json) {
        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("model", json, false);
        for (Map<String, String> row : data) {
            if (row.get("label") != null) {
                return row.get("label");
            }
        }
        return null;
    }

}

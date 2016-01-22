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
package io.fabric8.forge.camel.commands.project;

import java.util.List;

import io.fabric8.forge.addon.utils.LineNumberHelper;
import org.apache.camel.catalog.CamelCatalog;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.resource.FileResource;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;

/**
 * A wizard step to edit a XML node
 */
public class EditNodeXmlStep extends ConfigureEipPropertiesStep {

    public EditNodeXmlStep(ProjectFactory projectFactory, CamelCatalog camelCatalog, String eipName, String group, List<InputComponent> allInputs, List<InputComponent> inputs,
                           boolean last, int index, int total) {
        super(projectFactory, camelCatalog, eipName, group, allInputs, inputs, last, index, total);
    }

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(ConfigureEndpointPropertiesStep.class).name(
                "Camel: Edit EIP").category(Categories.create(CATEGORY))
                .description(String.format("Configure %s options (%s of %s)", getGroup(), getIndex(), getTotal()));
    }

    @Override
    protected Result editModelXml(List<String> lines, String lineNumber, String lineNumberEnd, String modelXml, FileResource file, String xml) throws Exception {
        // the list is 0-based, and line number is 1-based
        int idx = Integer.valueOf(lineNumber) - 1;
        int idx2 = Integer.valueOf(lineNumberEnd) - 1;
        int delta = (idx2 - idx) + 1;

        // use the same indent from the eip we are replacing
        int spaces = LineNumberHelper.leadingSpaces(lines, idx);
        String line = LineNumberHelper.padString(modelXml, spaces);

        // remove the lines
        while (delta > 0) {
            delta--;
            lines.remove(idx);
        }

        // add the new line at the old starting position
        lines.add(idx, line);

        // and save the file back
        String content = LineNumberHelper.linesToString(lines);
        file.setContents(content);
        return Results.success("Edited: " + modelXml);
    }

    @Override
    protected Result addModelXml(List<String> lines, String lineNumber, String lineNumberEnd, String modelXml, FileResource file, String xml) throws Exception {
        // noop
        return null;
    }

}

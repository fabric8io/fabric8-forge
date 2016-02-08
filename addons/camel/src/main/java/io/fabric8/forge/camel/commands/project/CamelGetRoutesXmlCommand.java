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

import io.fabric8.forge.addon.utils.Indenter;
import io.fabric8.forge.camel.commands.project.completer.XmlFileCompleter;
import io.fabric8.forge.camel.commands.project.dto.ContextDto;
import io.fabric8.forge.camel.commands.project.dto.NodeDtos;
import io.fabric8.forge.addon.utils.dto.OutputFormat;
import io.fabric8.forge.camel.commands.project.helper.CamelXmlHelper;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;

import static io.fabric8.forge.camel.commands.project.helper.CollectionHelper.first;
import static io.fabric8.forge.addon.utils.OutputFormatHelper.toJson;

public class CamelGetRoutesXmlCommand extends AbstractCamelProjectCommand {

    @Inject
    @WithAttributes(label = "XML File", required = true, description = "The XML file to use (either Spring or Blueprint)")
    private UISelectOne<String> xml;

    @Inject
    @WithAttributes(label = "Format", defaultValue = "Text", description = "Format output as text or json")
    private UISelectOne<OutputFormat> format;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelGetRoutesXmlCommand.class).name(
                "Camel: Get Routes XML").category(Categories.create(CATEGORY))
                .description("Gets the overview of the routes in a project for a given XML file");
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        Project project = getSelectedProject(builder.getUIContext());

        XmlFileCompleter xmlFileCompleter = createXmlFileCompleter(project);
        Set<String> files = xmlFileCompleter.getFiles();

        // use value choices instead of completer as that works better in web console
        xml.setValueChoices(files);
        if (files.size() == 1) {
            // lets default the value if there's only one choice
            xml.setDefaultValue(first(files));
        }
        builder.add(xml).add(format);
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        Project project = getSelectedProject(context);

        String xmlResourceName = xml.getValue();
        List<ContextDto> camelContexts = CamelXmlHelper.loadCamelContext(getCamelCatalog(), context.getUIContext(), project, xmlResourceName);
        if (camelContexts == null) {
            return Results.fail("No file found for: " + xmlResourceName);
        }
        String result = formatResult(camelContexts);
        return Results.success(result);
    }

    protected String formatResult(List<ContextDto> result) throws Exception {
        OutputFormat outputFormat = format.getValue();
        switch (outputFormat) {
            case JSON:
                return toJson(result);
            default:
                return textResult(result);
        }
    }

    protected String textResult(List<ContextDto> camelContexts) throws Exception {
        StringBuilder buffer = new StringBuilder("\n\n");

        final Indenter out = new Indenter(buffer);
        for (final ContextDto camelContext : camelContexts) {
            NodeDtos.printNode(out, camelContext);
        }
        return buffer.toString();
    }

}

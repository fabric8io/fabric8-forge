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
package io.fabric8.forge.devops;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.forge.addon.utils.dto.OutputFormat;
import io.fabric8.forge.devops.dto.ProjectOverviewDTO;
import io.fabric8.utils.TablePrinter;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
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
import java.util.Set;

import static io.fabric8.forge.addon.utils.OutputFormatHelper.addTableTextOutput;
import static io.fabric8.forge.addon.utils.OutputFormatHelper.toJson;

public class GetOverviewCommand extends AbstractDevOpsCommand {

    @Inject
    @WithAttributes(label = "Format", defaultValue = "Text", description = "Format output as text or json")
    private UISelectOne<OutputFormat> format;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(GetOverviewCommand.class).name(
                "DevOps: Get Overview").category(Categories.create(CATEGORY))
                .description("Gets the overview of the builders and perspectives for this project");
    }

    @Override
    public boolean isEnabled(UIContext context) {
        boolean answer = super.isEnabled(context);
        if (answer) {
            // we should only be enabled in non gui
            boolean gui = isRunningInGui(context);
            answer = !gui;
        }
        return answer;
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        builder.add(format);
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        UIContext uiContext = context.getUIContext();
        ProjectOverviewDTO projectOverview = getProjectOverview(uiContext);
        String result = formatResult(projectOverview);
        return Results.success(result);
    }

    protected String formatResult(ProjectOverviewDTO result) throws JsonProcessingException {
        OutputFormat outputFormat = format.getValue();
        switch (outputFormat) {
            case JSON:
                return toJson(result);
            default:
                return textResult(result);
        }
    }

    protected String textResult(ProjectOverviewDTO project) {
        StringBuilder buffer = new StringBuilder("\n\n");

        Set<String> perspectives = project.getPerspectives();
        TablePrinter table = new TablePrinter();
        table.columns("perspective");
        for (String perspective : perspectives) {
            table.row(perspective);
        }
        addTableTextOutput(buffer, null, table);

        Set<String> builders = project.getBuilders();
        table = new TablePrinter();
        table.columns("builder");
        for (String builder : builders) {
            table.row(builder);
        }
        addTableTextOutput(buffer, null, table);
        return buffer.toString();
    }

}

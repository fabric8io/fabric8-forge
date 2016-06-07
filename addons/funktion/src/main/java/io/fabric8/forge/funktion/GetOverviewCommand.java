/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.forge.funktion;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.forge.addon.utils.CommandHelpers;
import io.fabric8.forge.addon.utils.dto.OutputFormat;
import io.fabric8.forge.funktion.dto.ProjectDto;
import io.fabric8.funktion.model.FunktionConfig;
import io.fabric8.funktion.model.FunktionConfigs;
import io.fabric8.funktion.model.FunktionRule;
import io.fabric8.utils.TablePrinter;
import org.jboss.forge.addon.projects.Project;
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
import java.io.File;
import java.io.IOException;
import java.util.List;

import static io.fabric8.forge.addon.utils.OutputFormatHelper.addTableTextOutput;
import static io.fabric8.forge.addon.utils.OutputFormatHelper.toJson;

/**
 */
public class GetOverviewCommand extends AbstractFunktionCommand {

    public static String CATEGORY = "Funktion";

    @Inject
    @WithAttributes(label = "Format", defaultValue = "Text", description = "Format output as text or json")
    private UISelectOne<OutputFormat> format;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(GetOverviewCommand.class).name(
                "Funktion: Get Overview").category(Categories.create(CATEGORY))
                .description("Gets the overview of the funktion project");
    }

    @Override
    public boolean isEnabled(UIContext context) {
        FunktionConfig config = getFunktionConfig(context);
        return config != null && config.getRules().size() > 0;
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        builder.add(format);
    }


    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        ProjectDto projectDto = new ProjectDto();
        FunktionConfig config = getFunktionConfig(context.getUIContext());
        if (config != null) {
            projectDto.setConfig(config);
        }
        String result = formatResult(projectDto);
        return Results.success(result);
    }


    public FunktionConfig getFunktionConfig(UIContext context) {
        FunktionConfig config = null;
        Project project = getSelectedProject(context);
        File baseDir = CommandHelpers.getBaseDir(project);
        if (baseDir != null) {
            try {
                config = FunktionConfigs.findFromFolder(baseDir);
            } catch (IOException e) {
                // ignore
            }
        }
        return config;
    }

    protected String formatResult(ProjectDto result) throws JsonProcessingException {
        OutputFormat outputFormat = format.getValue();
        switch (outputFormat) {
            case JSON:
                return toJson(result);
            default:
                return textResult(result);
        }
    }

    protected String textResult(ProjectDto projectDto) {
        StringBuilder buffer = new StringBuilder("\n\n");

        FunktionConfig config = projectDto.getConfig();
        if (config != null) {
            List<FunktionRule> rules = config.getRules();
            if (rules != null && !rules.isEmpty()) {
                TablePrinter table = new TablePrinter();
                table.columns("name", "trigger", "action", "chain");
                for (FunktionRule rule : rules) {
                    table.row(rule.getName(), rule.getTrigger(), rule.getAction(), rule.getChain());
                }
                addTableTextOutput(buffer, "Rules", table);
            }
        }
        return buffer.toString();
    }
}

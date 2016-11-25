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
package io.fabric8.forge.rest.client;

import com.offbytwo.jenkins.JenkinsServer;
import io.fabric8.forge.rest.dto.CommandInputDTO;
import io.fabric8.forge.rest.dto.ExecutionRequest;
import io.fabric8.forge.rest.dto.ExecutionResult;
import io.fabric8.forge.rest.dto.PropertyDTO;
import io.fabric8.forge.rest.dto.ValidationResult;
import io.fabric8.forge.rest.dto.WizardResultsDTO;
import io.fabric8.utils.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.fabric8.forge.rest.client.EnvironmentVariables.getEnvironmentValue;

/**
 */
public class ForgeClientHelpers {
    private static final transient Logger LOG = LoggerFactory.getLogger(ForgeClientHelpers.class);

    public static Map<String, Object> getLastPage(ExecutionRequest executionRequest) {
        Map<String, Object> page = null;
        List<Map<String, Object>> inputList = executionRequest.getInputList();
        if (inputList != null) {
            int size = inputList.size();
            page = inputList.get(size - 1);
        }
        if (page == null) {
            page = Collections.EMPTY_MAP;
        }
        return page;
    }

    public static Map<String, PropertyDTO> getCommandProperties(CommandInputDTO commandInput) {
        if (commandInput != null) {
            Map<String, PropertyDTO> properties = commandInput.getProperties();
            if (properties != null) {
                return properties;
            }
        }
        return Collections.EMPTY_MAP;
    }

    public static Map<String, PropertyDTO> getCommandProperties(ExecutionResult result) {
        if (result != null) {
            return getCommandProperties(result.getWizardResults());
        }
        return Collections.EMPTY_MAP;
    }

    public static Map<String, PropertyDTO> getCommandProperties(ValidationResult result) {
        if (result != null) {
            return getCommandProperties(result.getWizardResults());
        }
        return Collections.EMPTY_MAP;
    }

    protected static Map<String, PropertyDTO> getCommandProperties(WizardResultsDTO wizardResults) {
        if (wizardResults != null) {
            List<CommandInputDTO> stepInputs = wizardResults.getStepInputs();
            if (stepInputs != null) {
                int size = stepInputs.size();
                if (size > 0) {
                    CommandInputDTO commandInput = stepInputs.get(size - 1);
                    return getCommandProperties(commandInput);
                }
            }
        }
        return Collections.EMPTY_MAP;
    }

    public static Map<String, Object> addPage(List<Map<String, Object>> inputList, Map<String, PropertyDTO> properties, ValueProvider valueProvider) {
        Map<String, Object> page = createPage(inputList, properties, valueProvider);
        inputList.add(page);
        return page;
    }

    protected static Map<String, Object> createPage(List<Map<String, Object>> inputList, Map<String, PropertyDTO> properties, ValueProvider valueProvider) {
        Map<String, Object> page = new HashMap<>();
        updatePageValues(inputList, properties, valueProvider, page);
        return page;
    }

    public static void updatePageValues(List<Map<String, Object>> inputList, Map<String, PropertyDTO> properties, ValueProvider valueProvider, Map<String, Object> page) {
        if (properties != null) {
            for (Map.Entry<String, PropertyDTO> entry : properties.entrySet()) {
                String key = entry.getKey();
                PropertyDTO property = entry.getValue();
                int pageNumber = inputList.size();
                Object value = valueProvider.getValue(key, property, pageNumber);
                if (value != null) {
                    page.put(key, value);
                }
            }
        }
    }

    public static JenkinsServer createJenkinsServer() throws URISyntaxException {
        String url = getJenkinsURL();
        return new JenkinsServer(new URI(url));
    }

    public static String getJenkinsURL() {
        return getEnvironmentValue(EnvironmentVariables.JENKINS_URL, "http://jenkins/");
    }


    /**
     * Tails the log of the given URL such as a build log, processing all new lines since the last results
     */
    public static TailResults tailLog(String uri, TailResults previousResults, Function<String, Void> lineProcessor) throws IOException {
        URL logURL = new URL(uri);
        try (InputStream inputStream = logURL.openStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            int count = 0;
            String lastLine = null;
            while (true) {
                String line = reader.readLine();
                if (line == null) break;
                lastLine = line;
                if (previousResults.isNewLine(line, count)) {
                    lineProcessor.apply(line);
                }
                count++;
            }
            return new TailResults(count, lastLine);
        }
    }
}

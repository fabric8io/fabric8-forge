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
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.BuildResult;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.JobWithDetails;
import io.fabric8.forge.rest.dto.ExecutionResult;
import io.fabric8.forge.rest.dto.PropertyDTO;
import io.fabric8.forge.rest.dto.ValidationResult;
import io.fabric8.utils.Closeables;
import io.fabric8.utils.Function;
import io.fabric8.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static io.fabric8.forge.rest.client.ForgeClientHelpers.createJenkinsServer;
import static io.fabric8.forge.rest.client.ForgeClientHelpers.getJenkinsURL;
import static io.fabric8.forge.rest.client.ForgeClientHelpers.tailLog;
import static org.assertj.core.api.Assertions.assertThat;

/**
 */
public class ForgeClientAsserts {
    private static final transient Logger LOG = LoggerFactory.getLogger(ForgeClientAsserts.class);

    private static final long DEFAULT_TIMEOUT_MILLIS = 60 * 60 * 1000;

    protected static boolean doAssert = true;

    public static void assertValidAndExecutable(ValidationResult result) {
        assertThat(result).isNotNull();

        if (doAssert) {
            assertThat(result.isValid()).describedAs("isValid").isTrue();
            assertThat(result.isCanExecute()).describedAs("isCanExecute").isTrue();
        } else {
            LOG.info("isValid: " + result.isValid());
            LOG.info("isCanExecute: " + result.isCanExecute());
        }
    }

    public static void assertCanMoveToNextStep(ExecutionResult result) {
        assertThat(result).isNotNull();
        if (doAssert) {
            assertThat(result.isCanMoveToNextStep()).describedAs("isCanMoveToNextStep").isTrue();
            assertThat(result.isCommandCompleted()).describedAs("isCommandCompleted").isFalse();
        } else {
            LOG.info("isCanMoveToNextStep: " + result.isCanMoveToNextStep());
            LOG.info("isCommandCompleted: " + result.isCommandCompleted());
        }
    }

    public static void assertExecutionWorked(ExecutionResult result) {
        assertThat(result).isNotNull();
        assertThat(result.isCommandCompleted()).describedAs("isCommandCompleted").isTrue();
        assertThat(result.isCanMoveToNextStep()).describedAs("isCanMoveToNextStep").isFalse();
    }

    public static Object assertChooseValue(String propertyName, PropertyDTO property, int pageNumber, String value) {
        List<Object> valueChoices = property.getValueChoices();
        if (valueChoices == null || valueChoices.isEmpty()) {
            valueChoices = property.getTypeaheadData();
        }
        if (valueChoices == null || valueChoices.isEmpty()) {
            // lets assume that we are in the initial request - and that validate will populate this!
            return null;
        }

        boolean contained = valueChoices.contains(value);
        if (!contained) {
            // we may have maps containing a value property
            for (Object choice : valueChoices) {
                if (choice instanceof Map) {
                    Map map = (Map) choice;
                    Object text = map.get("value");
                    if (text != null && value.equals(text)) {
                        contained = true;
                        break;
                    }

                }
            }
        }
        assertThat(contained).describedAs(("Choices for property " + propertyName + " on page " + pageNumber + " with choices: " + valueChoices) + " does not contain " + value).isTrue();
        return value;
    }


    /**
     * Asserts that a Build is created and that it completes successfully within the default time period
     */
    public static void assertBuildCompletes(ForgeClient forgeClient, String projectName) throws IOException, URISyntaxException {
        assertBuildCompletes(forgeClient, projectName, DEFAULT_TIMEOUT_MILLIS);
    }


    /**
     * Asserts that a Build is created and that it completes successfully within the given time period
     */
    public static void assertBuildCompletes(ForgeClient forgeClient, String projectName, long timeoutMillis) throws URISyntaxException, IOException {
/*
        OpenShiftClient openShiftClient = forgeClient.getOpenShiftOrJenkinshiftClient();
        String namespace = forgeClient.getNamespace();
        BuildConfig buildConfig = openShiftClient.buildConfigs().inNamespace(namespace).withName(projectName).get();
        assertThat(buildConfig).describedAs("No BuildConfig found in " + namespace + " called " + projectName).isNotNull();
*/

        JenkinsServer jenkins = createJenkinsServer();
        JobWithDetails job = jenkins.getJob(projectName);
        assertThat(job).describedAs("No Jenkins Job found for name: " + projectName).isNotNull();

        Build lastBuild = job.getLastBuild();
        assertThat(lastBuild).describedAs("No Jenkins Build for Job: " + projectName).isNotNull();
        BuildWithDetails details = null;
        String description = "Job " + projectName + " build " + lastBuild.getNumber();

        LOG.info("Waiting for build " + description + " to complete...");

        String logUri = getBuildConsoleTextUrl(lastBuild, description);

        long end = System.currentTimeMillis() + timeoutMillis;
        TailResults tailResults = TailResults.START;
        while (true) {
            int sleepMillis = 5000;
            long start = System.currentTimeMillis();
            tailResults = tailLog(logUri, tailResults, new Function<String, Void>() {
                @Override
                public Void apply(String line) {
                    System.out.println("Build:" + lastBuild.getNumber() + ": " + line);
                    return null;
                }
            });

            details = lastBuild.details();
            if (!details.isBuilding()) {
                break;
            }
            if (end < System.currentTimeMillis()) {
                break;
            }
            long duration = System.currentTimeMillis() - start;
            long sleepTime = sleepMillis - duration;
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
        details = lastBuild.details();
        dumpBuildLog(lastBuild, description);
        LOG.info("");

        BuildResult result = details.getResult();
        assertThat(result).describedAs("Status of " + description).isEqualTo(BuildResult.SUCCESS);
        assertThat(details.isBuilding()).describedAs("Build not finshed for " + description).isFalse();
    }

    public static void dumpBuildLog(Build lastBuild, String description) throws IOException {
        String logUri = getBuildConsoleTextUrl(lastBuild, description);

        URL logURL = new URL(logUri);
        InputStream inputStream = logURL.openStream();

        printBuildLog(inputStream, description);
    }

    public static String getBuildConsoleTextUrl(Build lastBuild, String description) {
        String url = lastBuild.getUrl();

        LOG.info("Build URL: " + url);
        String logUri = url + "/consoleText";
        if (logUri.indexOf("://") < 0) {
            logUri = URLUtils.pathJoin(getJenkinsURL(), logUri);
        }
        LOG.info("Tailing " + description + " at URL:" + logUri);
        return logUri;
    }

    protected static void printBuildLog(InputStream inputStream, String name) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        try {
            while (true) {
                String line = reader.readLine();
                if (line == null) break;
                LOG.info(line);
            }

        } catch (Exception e) {
            LOG.error("Failed to read build log for: " + name + ": " + e, e);
            throw e;
        } finally {
            Closeables.closeQuietly(reader);
        }
    }
}

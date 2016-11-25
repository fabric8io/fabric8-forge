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

import io.fabric8.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class EnvironmentVariables {
    public static final String FORGE_URL = "FABRIC8_FORGE_URL";
    public static final String JENKINS_URL = "JENKINS_URL";
    private static final transient Logger LOG = LoggerFactory.getLogger(EnvironmentVariables.class);

    public static String getEnvironmentValue(String envVarName, String defaultValue) {
        String answer = System.getenv(envVarName);
        if (Strings.isNullOrBlank(answer)) {
            answer = defaultValue;
        }
        LOG.info("Using $" + envVarName + " value " + answer);
        return answer;
    }
}

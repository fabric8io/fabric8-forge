/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.forge.rest;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 */
public class Constants {
    public static final String PROJECT_NEW_COMMAND = "project-new";
    public static final String TARGET_LOCATION_PROPERTY = "targetLocation";

    public static class RequestParameters {

        public static final String SECRET = "secret";
        public static final String SECRET_NAMESPACE = "secretNamespace";
        public static final String GOGS_AUTH = "_gogsAuth";
        public static final String GOGS_EMAIL = "_gogsEmail";

        public static final Set<String> REQUEST_PARAMETERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                SECRET, SECRET_NAMESPACE, GOGS_AUTH, GOGS_EMAIL
        )));
    }
}

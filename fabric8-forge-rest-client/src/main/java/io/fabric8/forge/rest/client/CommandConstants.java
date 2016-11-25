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

import io.fabric8.forge.rest.Constants;

/**
 */
public class CommandConstants {
    public static final String ProjectNew = Constants.PROJECT_NEW_COMMAND;
    public static final String DevopsEdit = "devops-edit";

    public static class ProjectNewProperties {
        public static class Type {
            public static final String FromArchetypeCatalog = "From Archetype Catalog";
            public static final String Funktion = "Funktion";
            public static final String JAR = "Java Library (JAR)";
            public static final String Integration = "Integration";
            public static final String Microservice = "Microservice";
            public static final String NodeJS = "NodeJS";
            public static final String SpringBoot = "Spring Boot";
            public static final String VertX = "Vert.x";
            public static final String WAR = "Java Web Application (WAR)";
        }

        public static class Catalog {
            public static final String Fabric8 = "fabric8";
            public static final String Camel = "fabric8";
            public static final String Django = "django";
        }
    }

    public static class DevopsEditProperties {
        public static class Pipeline {
            public static final String CanaryRelease = "maven/CanaryRelease/Jenkinsfile";
            public static final String CanaryReleaseAndStage = "maven/CanaryReleaseAndStage/Jenkinsfile";
            public static final String CanaryReleaseStageAndApprovePromote = "maven/CanaryReleaseStageAndApprovePromote/Jenkinsfile";
        }
    }
}

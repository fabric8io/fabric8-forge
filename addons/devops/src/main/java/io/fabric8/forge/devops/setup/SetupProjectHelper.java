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
package io.fabric8.forge.devops.setup;

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.jboss.forge.addon.maven.projects.MavenFacet;
import org.jboss.forge.addon.projects.Project;

public class SetupProjectHelper {

    @Deprecated
    public static boolean isFunktionParentPom(Project project) {
        MavenFacet mavenFacet = project.getFacet(MavenFacet.class);
        if (mavenFacet != null) {
            Model model = mavenFacet.getModel();
            if (model != null) {
                Parent parent = model.getParent();
                if (parent != null) {
                    String groupId = parent.getGroupId();
                    if (groupId != null && groupId.startsWith("io.fabric8.funktion")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}

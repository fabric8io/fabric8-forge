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
package io.fabric8.forge.devops.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProjectOverviewDTO {
    private Set<String> builders = new TreeSet<>();
    private Set<String> perspectives = new TreeSet<>();

    public ProjectOverviewDTO() {
    }

    public void addBuilder(String builder) {
        builders.add(builder);
    }

    public void addPerspective(String perspective) {
        perspectives.add(perspective);
    }

    @Override
    public String toString() {
        return "ProjectOverviewDTO{" +
                "builders=" + builders +
                ", perspectives=" + perspectives +
                '}';
    }

    public Set<String> getBuilders() {
        return builders;
    }

    public void setBuilders(Set<String> builders) {
        this.builders = builders;
    }

    public Set<String> getPerspectives() {
        return perspectives;
    }

    public void setPerspectives(Set<String> perspectives) {
        this.perspectives = perspectives;
    }
}

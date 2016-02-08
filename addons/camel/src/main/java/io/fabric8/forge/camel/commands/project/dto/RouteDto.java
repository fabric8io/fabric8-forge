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
package io.fabric8.forge.camel.commands.project.dto;

import java.util.ArrayList;
import java.util.List;

public class RouteDto extends NodeDto {
    public static final String PATTERN = "route";

    public RouteDto() {
        super.setPattern(PATTERN);
    }

    public RouteDto(String key, String id, String label, String description, List<NodeDto> children) {
        super(key, id, label, PATTERN, description, true, children);
    }

    @Override
    protected RouteDto copy() {
        return new RouteDto(getKey(), getId(), getLabel(), getDescription(), new ArrayList<>(getChildren()));
    }

    @Override
    public void setPattern(String pattern) {
    }
}

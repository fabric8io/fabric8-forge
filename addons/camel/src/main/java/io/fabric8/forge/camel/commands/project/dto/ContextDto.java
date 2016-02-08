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

import org.apache.camel.RouteNode;

public class ContextDto extends NodeDto {
    public static final String PATTERN = "camelContext";

    public ContextDto() {
        this(null);
    }

    public ContextDto(String key, String id, String label, String description, List<NodeDto> children) {
        super(key, id, label, PATTERN, description, true, children);
    }

    public ContextDto(String name) {
        super.setPattern(PATTERN);
        setId(name);
    }

    @Override
    protected ContextDto copy() {
        return new ContextDto(getKey(), getId(), getLabel(), getDescription(), new ArrayList<>(getChildren()));
    }

    @Override
    public void setPattern(String pattern) {
    }

    public void addRoute(RouteDto node) {
        super.addChild(node);
    }

    @Override
    public void addChild(NodeDto node) {
        if (node instanceof RouteNode) {
            addRoute((RouteDto) node);
        } else {
            throw new IllegalArgumentException("Child node is not a route: " + node);
        }
    }

}

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
package io.fabric8.forge.camel.commands.project.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.camel.RouteNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 */
public class ContextDto extends NodeDto {
    public static final String PATTERN = "camelContext";

    public ContextDto() {
        this(null, Collections.EMPTY_LIST);
    }

    public ContextDto(String key, String id, String label, String description, List<NodeDto> children) {
        super(key, id, label, PATTERN, description, children);
    }

    public ContextDto(String name, List<NodeDto> routes) {
        super.setPattern(PATTERN);
        setId(name);
        setChildren(routes);
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

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

import org.apache.camel.RouteNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 */
public class ContextDto extends NodeDtoSupport {
    private final List<RouteDto> routes;

    public ContextDto() {
        this(null, Collections.EMPTY_LIST);
    }

    public ContextDto(String name, List<RouteDto> routes) {
        this.routes = routes;
        super.setPattern("camelContext");
        setId(name);
    }

    @Override
    public void setPattern(String pattern) {
    }

    public List<RouteDto> getRoutes() {
        return routes;
    }

    public void addRoute(RouteDto node) {
        routes.add(node);
    }
    @Override
    public void addChild(NodeDto node) {
        if (node instanceof RouteNode) {
            addRoute((RouteDto) node);
        } else {
            throw new IllegalArgumentException("Child node is not a route: " + node);
        }
    }

    @Override
    public List<NodeDto> getChildren() {
        return new ArrayList<NodeDto>(routes);
    }
}

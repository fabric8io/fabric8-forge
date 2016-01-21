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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodeDto extends NodeDtoSupport {
    private List<NodeDto> children;
    private Map<String,String> properties;

    public NodeDto() {
        children = new ArrayList<>();
    }

    public NodeDto(String key, String id, String label, String pattern, String description, List<NodeDto> children) {
        super(key, id, label, pattern, description);
        this.children = children;
    }

    @Override
    protected NodeDto copy() {
        return new NodeDto(getKey(), getId(), getLabel(), getPattern(), getDescription(), new ArrayList<>(children));
    }

    @Override
    public void addChild(NodeDto node) {
        children.add(node);
    }

    @Override
    public List<NodeDto> getChildren() {
        return children;
    }

    public void setChildren(List<NodeDto> children) {
        this.children = children;
    }

    public String getProperty(String name) {
        if (properties != null) {
            return properties.get(name);
        }
        return null;
    }

    public void setProperty(String name, String value) {
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.put(name, value);
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

}

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

import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.utils.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.fabric8.forge.camel.commands.project.dto.NodeDtos.getNodeText;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class NodeDtoSupport {
    private String key;
    private String id;
    private String pattern;
    private String label;
    private String description;
    private boolean supportOutput;

    public NodeDtoSupport() {
    }

    public NodeDtoSupport(String key, String id, String label, String pattern, String description, boolean supportOutput) {
        this.key = key;
        this.id = id;
        this.label = label;
        this.pattern = pattern;
        this.description = description;
        this.supportOutput = supportOutput;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NodeDtoSupport that = (NodeDtoSupport) o;

        if (!key.equals(that.key)) return false;
        return pattern != null ? pattern.equals(that.pattern) : that.pattern == null;

    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + (pattern != null ? pattern.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return pattern + "{" +
                "id='" + id + '\'' +
                ", label='" + label + '\'' +
                '}';
    }

    public abstract List<NodeDto> getChildren();

    public abstract void addChild(NodeDto node);

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public boolean isSupportOutput() {
        return supportOutput;
    }

    public void setSupportOutput(boolean supportOutput) {
        this.supportOutput = supportOutput;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void defaultKey(NodeDto owner, Map<String, Integer> nodeCounts) {
        String elementName = getPattern();
        Integer countObject = nodeCounts.get(elementName);
        int count = countObject != null ? countObject : 0;
        nodeCounts.put(elementName, ++count);
        if (Strings.isNullOrBlank(key)) {
            key = owner.getKey();
            if (Strings.isNullOrBlank(key)) {
                key = "";
            } else {
                key += "/";
            }
            if (Strings.isNotBlank(id)) {
                key += id;
            } else {
                key += "_" + elementName + count;
            }
        }
    }

    public List<NodeDto> toNodeList(String indentation) {
        List<NodeDto> answer = new ArrayList<>();
        addToNodeList(answer, "", indentation);
        return answer;
    }

    protected void addToNodeList(List<NodeDto> answer, String indent, String indentation) {
        NodeDto copy = this.copy();
        copy.setLabel(indent + getNodeText(copy));

        answer.add(copy);

        indent += indentation;
        for (NodeDto child  : getChildren()) {
            child.addToNodeList(answer, indent, indentation);
        }
    }

    protected abstract NodeDto copy();

}

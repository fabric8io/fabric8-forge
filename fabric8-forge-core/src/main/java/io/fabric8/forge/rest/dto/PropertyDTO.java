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
package io.fabric8.forge.rest.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PropertyDTO {
    private String name;
    private String description;
    private String title;
    private String requiredMessage;
    private Object value;
    private String javaType;
    private String type;
    private boolean enabled;
    private boolean required;
    @JsonProperty("enum")
    private List<Object> valueChoices;
    private List<Object> typeaheadData;

    public PropertyDTO() {
    }

    public PropertyDTO(String name, String description, String title, String requiredMessage, Object value, String javaType, String type, boolean enabled, boolean required, List<Object> valueChoices, List<Object> typeaheadData) {
        this.name = name;
        this.description = description;
        this.title = title;
        this.requiredMessage = requiredMessage;
        this.value = value;
        this.javaType = javaType;
        this.type = type;
        this.enabled = enabled;
        this.required = required;
        this.valueChoices = valueChoices;
        this.typeaheadData = typeaheadData;
    }

    @Override
    public String toString() {
        return "PropertyDTO{" +
                "name='" + name + '\'' +
                ", javaType='" + javaType + '\'' +
                ", description='" + description + '\'' +
                '}';
    }

    public String getDescription() {
        return description;
    }

    public String getJavaType() {
        return javaType;
    }

    public String getTitle() {
        return title;
    }

    public String getName() {
        return name;
    }

    public String getRequiredMessage() {
        return requiredMessage;
    }

    public Object getValue() {
        return value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isRequired() {
        return required;
    }

    public String getType() {
        return type;
    }

    public List<Object> getValueChoices() {
        return valueChoices;
    }

    public List<Object> getTypeaheadData() {
        return typeaheadData;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setRequiredMessage(String requiredMessage) {
        this.requiredMessage = requiredMessage;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public void setValueChoices(List<Object> valueChoices) {
        this.valueChoices = valueChoices;
    }

    public void setTypeaheadData(List<Object> typeaheadData) {
        this.typeaheadData = typeaheadData;
    }
}

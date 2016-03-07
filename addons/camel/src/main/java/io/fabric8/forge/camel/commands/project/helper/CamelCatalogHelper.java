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
package io.fabric8.forge.camel.commands.project.helper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import io.fabric8.forge.camel.commands.project.dto.ComponentDto;
import io.fabric8.forge.camel.commands.project.dto.DataFormatDto;
import io.fabric8.forge.camel.commands.project.dto.EipDto;
import io.fabric8.forge.camel.commands.project.dto.LanguageDto;
import io.fabric8.utils.Strings;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.JSonSchemaHelper;

public final class CamelCatalogHelper {

    /**
     * Returns the text in title case
     */
    public static String asTitleCase(String text) {
        StringBuilder sb = new StringBuilder();
        boolean next = true;

        for (char c : text.toCharArray()) {
            if (Character.isSpaceChar(c)) {
                next = true;
            } else if (next) {
                c = Character.toTitleCase(c);
                next = false;
            }
            sb.append(c);
        }

        return sb.toString();
    }

    public static String endpointComponentName(String uri) {
        if (uri != null) {
            int idx = uri.indexOf(":");
            if (idx > 0) {
                return uri.substring(0, idx);
            }
        }
        return null;
    }

    public static Set<String> componentsFromArtifact(CamelCatalog camelCatalog, String artifactId) {
        Set<String> answer = new TreeSet<String>();

        // use the camel catalog to find what components the artifact has
        for (String name : camelCatalog.findComponentNames()) {
            String json = camelCatalog.componentJSonSchema(name);
            if (json != null) {
                List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("component", json, false);
                String scheme = null;
                String artifact = null;
                for (Map<String, String> row : data) {
                    if (row.get("artifactId") != null) {
                        artifact = row.get("artifactId");
                    }
                    if (row.get("scheme") != null) {
                        scheme = row.get("scheme");
                    }
                }
                if (artifactId.equals(artifact) && scheme != null) {
                    answer.add(scheme);
                }
            }
        }

        return answer;
    }

    public static Set<String> dataFormatsFromArtifact(CamelCatalog camelCatalog, String artifactId) {
        Set<String> answer = new TreeSet<String>();

        // use the camel catalog to find what components the artifact has
        for (String name : camelCatalog.findDataFormatNames()) {
            String json = camelCatalog.dataFormatJSonSchema(name);
            if (json != null) {
                List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("dataformat", json, false);
                String df = null;
                String artifact = null;
                for (Map<String, String> row : data) {
                    if (row.get("artifactId") != null) {
                        artifact = row.get("artifactId");
                    }
                    if (row.get("name") != null) {
                        df = row.get("name");
                    }
                }
                if (artifactId.equals(artifact) && df != null) {
                    answer.add(df);
                }
            }
        }

        return answer;
    }

    public static Set<String> languagesFromArtifact(CamelCatalog camelCatalog, String artifactId) {
        Set<String> answer = new TreeSet<String>();

        // use the camel catalog to find what components the artifact has
        for (String name : camelCatalog.findLanguageNames()) {
            String json = camelCatalog.languageJSonSchema(name);
            if (json != null) {
                List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("language", json, false);
                String lan = null;
                String artifact = null;
                for (Map<String, String> row : data) {
                    if (row.get("artifactId") != null) {
                        artifact = row.get("artifactId");
                    }
                    if (row.get("name") != null) {
                        lan = row.get("name");
                    }
                }
                if (artifactId.equals(artifact) && lan != null) {
                    answer.add(lan);
                }
            }
        }

        return answer;
    }

    /**
     * Checks whether the given value is matching the default value from the given component.
     *
     * @param scheme the component name
     * @param key    the option key
     * @param value  the option value
     * @return <tt>true</tt> if matching the default value, <tt>false</tt> otherwise
     */
    public static boolean isDefaultValue(CamelCatalog camelCatalog, String scheme, String key, String value) {
        // use the camel catalog
        String json = camelCatalog.componentJSonSchema(scheme);
        if (json == null) {
            throw new IllegalArgumentException("Could not find catalog entry for component name: " + scheme);
        }

        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        if (data != null) {
            for (Map<String, String> propertyMap : data) {
                String name = propertyMap.get("name");
                String defaultValue = propertyMap.get("defaultValue");
                if (key.equals(name)) {
                    return value.equalsIgnoreCase(defaultValue);
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the given value is matching the default value from the given component.
     *
     * @param scheme the component name
     * @param key    the option key
     * @param value  the option value
     * @return <tt>true</tt> if matching the default value, <tt>false</tt> otherwise
     */
    public static boolean isDefaultValueComponent(CamelCatalog camelCatalog, String scheme, String key, String value) {
        // use the camel catalog
        String json = camelCatalog.componentJSonSchema(scheme);
        if (json == null) {
            throw new IllegalArgumentException("Could not find catalog entry for component name: " + scheme);
        }

        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("componentProperties", json, true);
        if (data != null) {
            for (Map<String, String> propertyMap : data) {
                String name = propertyMap.get("name");
                String defaultValue = propertyMap.get("defaultValue");
                if (key.equals(name)) {
                    return value.equalsIgnoreCase(defaultValue);
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the given key is a multi valued option
     *
     * @param scheme the component name
     * @param key    the option key
     * @return <tt>true</tt> if the key is multi valued, <tt>false</tt> otherwise
     */
    public static boolean isMultiValue(CamelCatalog camelCatalog, String scheme, String key) {
        // use the camel catalog
        String json = camelCatalog.componentJSonSchema(scheme);
        if (json == null) {
            throw new IllegalArgumentException("Could not find catalog entry for component name: " + scheme);
        }

        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        if (data != null) {
            for (Map<String, String> propertyMap : data) {
                String name = propertyMap.get("name");
                String multiValue = propertyMap.get("multiValue");
                if (key.equals(name)) {
                    return "true".equals(multiValue);
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the given key is a multi valued option
     *
     * @param scheme the component name
     * @param key    the option key
     * @return <tt>true</tt> if the key is multi valued, <tt>false</tt> otherwise
     */
    public static String getPrefix(CamelCatalog camelCatalog, String scheme, String key) {
        // use the camel catalog
        String json = camelCatalog.componentJSonSchema(scheme);
        if (json == null) {
            throw new IllegalArgumentException("Could not find catalog entry for component name: " + scheme);
        }

        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        if (data != null) {
            for (Map<String, String> propertyMap : data) {
                String name = propertyMap.get("name");
                String prefix = propertyMap.get("prefix");
                if (key.equals(name)) {
                    return prefix;
                }
            }
        }
        return null;
    }

    /**
     * Checks whether the given value corresponds to a dummy none placeholder for an enum type
     *
     * @param scheme the component name
     * @param key    the option key
     * @return <tt>true</tt> if matching the default value, <tt>false</tt> otherwise
     */
    public static boolean isNonePlaceholderEnumValue(CamelCatalog camelCatalog, String scheme, String key) {
        // use the camel catalog
        String json = camelCatalog.componentJSonSchema(scheme);
        if (json == null) {
            throw new IllegalArgumentException("Could not find catalog entry for component name: " + scheme);
        }

        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        if (data != null) {
            for (Map<String, String> propertyMap : data) {
                String name = propertyMap.get("name");
                String enums = propertyMap.get("enum");
                if (key.equals(name) && enums != null) {
                    if (!enums.contains("none")) {
                        return true;
                    } else {
                        return false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the given value corresponds to a dummy none placeholder for an enum type
     *
     * @param scheme the component name
     * @param key    the option key
     * @return <tt>true</tt> if matching the default value, <tt>false</tt> otherwise
     */
    public static boolean isNonePlaceholderEnumValueComponent(CamelCatalog camelCatalog, String scheme, String key) {
        // use the camel catalog
        String json = camelCatalog.componentJSonSchema(scheme);
        if (json == null) {
            throw new IllegalArgumentException("Could not find catalog entry for component name: " + scheme);
        }

        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("componentProperties", json, true);
        if (data != null) {
            for (Map<String, String> propertyMap : data) {
                String name = propertyMap.get("name");
                String enums = propertyMap.get("enum");
                if (key.equals(name) && enums != null) {
                    if (!enums.contains("none")) {
                        return true;
                    } else {
                        return false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Get's the java type for the given option if its enum based, otherwise it returns null
     *
     * @param scheme the component name
     * @param key    the option key
     * @return <tt>true</tt> if matching the default value, <tt>false</tt> otherwise
     */
    public static String getEnumJavaTypeComponent(CamelCatalog camelCatalog, String scheme, String key) {
        // use the camel catalog
        String json = camelCatalog.componentJSonSchema(scheme);
        if (json == null) {
            throw new IllegalArgumentException("Could not find catalog entry for component name: " + scheme);
        }

        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("componentProperties", json, true);
        if (data != null) {
            for (Map<String, String> propertyMap : data) {
                String javaType = propertyMap.get("javaType");
                String name = propertyMap.get("name");
                String enums = propertyMap.get("enum");
                if (key.equals(name) && enums != null) {
                    return javaType;
                }
            }
        }
        return null;
    }

    /**
     * Checks whether the given value is matching the default value from the given model.
     *
     * @param modelName the model name
     * @param key    the option key
     * @param value  the option value
     * @return <tt>true</tt> if matching the default value, <tt>false</tt> otherwise
     */
    public static boolean isModelDefaultValue(CamelCatalog camelCatalog, String modelName, String key, String value) {
        // use the camel catalog
        String json = camelCatalog.modelJSonSchema(modelName);
        if (json == null) {
            throw new IllegalArgumentException("Could not find catalog entry for model name: " + modelName);
        }

        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        if (data != null) {
            for (Map<String, String> propertyMap : data) {
                String name = propertyMap.get("name");
                String defaultValue = propertyMap.get("defaultValue");
                if (key.equals(name)) {
                    return value.equalsIgnoreCase(defaultValue);
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the given key is an expression kind
     *
     * @param modelName the model name
     * @param key    the option key
     * @return <tt>true</tt> if the key is an expression type, <tt>false</tt> otherwise
     */
    public static boolean isModelExpressionKind(CamelCatalog camelCatalog, String modelName, String key) {
        // use the camel catalog
        String json = camelCatalog.modelJSonSchema(modelName);
        if (json == null) {
            throw new IllegalArgumentException("Could not find catalog entry for model name: " + modelName);
        }

        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        if (data != null) {
            for (Map<String, String> propertyMap : data) {
                String name = propertyMap.get("name");
                if (key.equals(name)) {
                    return "expression".equals(propertyMap.get("kind"));
                }
            }
        }
        return false;
    }

    /**
     * Gets the java type of the given model
     *
     * @param modelName the model name
     * @return the java type
     */
    public static String getModelJavaType(CamelCatalog camelCatalog, String modelName) {
        // use the camel catalog
        String json = camelCatalog.modelJSonSchema(modelName);
        if (json == null) {
            throw new IllegalArgumentException("Could not find catalog entry for model name: " + modelName);
        }

        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("model", json, false);
        if (data != null) {
            for (Map<String, String> propertyMap : data) {
                String javaType = propertyMap.get("javaType");
                if (javaType != null) {
                    return javaType;
                }
            }
        }
        return null;
    }

    /**
     * Whether the EIP supports outputs
     *
     * @param modelName the model name
     * @return <tt>true</tt> if output supported, <tt>false</tt> otherwise
     */
    public static boolean isModelSupportOutput(CamelCatalog camelCatalog, String modelName) {
        // use the camel catalog
        String json = camelCatalog.modelJSonSchema(modelName);
        if (json == null) {
            throw new IllegalArgumentException("Could not find catalog entry for model name: " + modelName);
        }

        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("model", json, false);
        if (data != null) {
            for (Map<String, String> propertyMap : data) {
                String output = propertyMap.get("output");
                if (output != null) {
                    return "true".equals(output);
                }
            }
        }
        return false;
    }

    /**
     * Whether the component is consumer only
     */
    public static boolean isComponentConsumerOnly(CamelCatalog camelCatalog, String scheme) {
        // use the camel catalog
        String json = camelCatalog.componentJSonSchema(scheme);
        if (json == null) {
            return false;
        }

        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("component", json, false);
        if (data != null) {
            for (Map<String, String> propertyMap : data) {
                String consumerOnly = propertyMap.get("consumerOnly");
                if (consumerOnly != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the component is consumer only
     */
    public static boolean isComponentProducerOnly(CamelCatalog camelCatalog, String scheme) {
        // use the camel catalog
        String json = camelCatalog.componentJSonSchema(scheme);
        if (json == null) {
            return false;
        }

        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("component", json, false);
        if (data != null) {
            for (Map<String, String> propertyMap : data) {
                String consumerOnly = propertyMap.get("producerOnly");
                if (consumerOnly != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public static ComponentDto createComponentDto(CamelCatalog camelCatalog, String scheme) {
        // use the camel catalog
        String json = camelCatalog.componentJSonSchema(scheme);
        if (json == null) {
            return null;
        }

        ComponentDto dto = new ComponentDto();
        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("component", json, false);
        for (Map<String, String> row : data) {
            if (row.get("scheme") != null) {
                dto.setScheme(row.get("scheme"));
            } else if (row.get("syntax") != null) {
                dto.setSyntax(row.get("syntax"));
            } else if (row.get("title") != null) {
                dto.setTitle(row.get("title"));
            } else if (row.get("description") != null) {
                dto.setDescription(row.get("description"));
            } else if (row.get("label") != null) {
                String labelText = row.get("label");
                if (Strings.isNotBlank(labelText)) {
                    dto.setTags(labelText.split(","));
                }
            } else if (row.get("consumerOnly") != null) {
                dto.setConsumerOnly("true".equals(row.get("consumerOnly")));
            } else if (row.get("producerOnly") != null) {
                dto.setProducerOnly("true".equals(row.get("producerOnly")));
            } else if (row.get("javaType") != null) {
                dto.setJavaType(row.get("javaType"));
            } else if (row.get("groupId") != null) {
                dto.setGroupId(row.get("groupId"));
            } else if (row.get("artifactId") != null) {
                dto.setArtifactId(row.get("artifactId"));
            } else if (row.get("version") != null) {
                dto.setVersion(row.get("version"));
            }
        }
        return dto;
    }

    public static EipDto createEipDto(CamelCatalog camelCatalog, String modelName) {
        // use the camel catalog
        String json = camelCatalog.modelJSonSchema(modelName);
        if (json == null) {
            return null;
        }

        EipDto dto = new EipDto();
        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("model", json, false);
        for (Map<String, String> row : data) {
            if (row.get("name") != null) {
                dto.setName(row.get("name"));
            } else if (row.get("title") != null) {
                dto.setTitle(row.get("title"));
            } else if (row.get("description") != null) {
                dto.setDescription(row.get("description"));
            } else if (row.get("label") != null) {
                String labelText = row.get("label");
                if (Strings.isNotBlank(labelText)) {
                    dto.setTags(labelText.split(","));
                }
            } else if (row.get("javaType") != null) {
                dto.setJavaType(row.get("javaType"));
            }
        }
        return dto;
    }

    public static DataFormatDto createDataFormatDto(CamelCatalog camelCatalog, String name) {
        // use the camel catalog
        String json = camelCatalog.dataFormatJSonSchema(name);
        if (json == null) {
            return null;
        }

        DataFormatDto dto = new DataFormatDto();
        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("dataformat", json, false);
        for (Map<String, String> row : data) {
            if (row.get("name") != null) {
                dto.setName(row.get("name"));
            } else if (row.get("modelName") != null) {
                dto.setModelName(row.get("modelName"));
            } else if (row.get("title") != null) {
                dto.setTitle(row.get("title"));
            } else if (row.get("description") != null) {
                dto.setDescription(row.get("description"));
            } else if (row.get("label") != null) {
                dto.setLabel(row.get("label"));
            } else if (row.get("javaType") != null) {
                dto.setJavaType(row.get("javaType"));
            } else if (row.get("modelJavaType") != null) {
                dto.setModelJavaType(row.get("modelJavaType"));
            } else if (row.get("groupId") != null) {
                dto.setGroupId(row.get("groupId"));
            } else if (row.get("artifactId") != null) {
                dto.setArtifactId(row.get("artifactId"));
            } else if (row.get("version") != null) {
                dto.setVersion(row.get("version"));
            }
        }
        return dto;
    }

    public static LanguageDto createLanguageDto(CamelCatalog camelCatalog, String name) {
        // use the camel catalog
        String json = camelCatalog.languageJSonSchema(name);
        if (json == null) {
            return null;
        }

        LanguageDto dto = new LanguageDto();
        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("language", json, false);
        for (Map<String, String> row : data) {
            if (row.get("name") != null) {
                dto.setName(row.get("name"));
            } else if (row.get("modelName") != null) {
                dto.setModelName(row.get("modelName"));
            } else if (row.get("title") != null) {
                dto.setTitle(row.get("title"));
            } else if (row.get("description") != null) {
                dto.setDescription(row.get("description"));
            } else if (row.get("label") != null) {
                dto.setLabel(row.get("label"));
            } else if (row.get("javaType") != null) {
                dto.setJavaType(row.get("javaType"));
            } else if (row.get("modelJavaType") != null) {
                dto.setModelJavaType(row.get("modelJavaType"));
            } else if (row.get("groupId") != null) {
                dto.setGroupId(row.get("groupId"));
            } else if (row.get("artifactId") != null) {
                dto.setArtifactId(row.get("artifactId"));
            } else if (row.get("version") != null) {
                dto.setVersion(row.get("version"));
            }
        }
        return dto;
    }

}

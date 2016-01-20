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

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.camel.tooling.util.Strings2;
import io.fabric8.forge.addon.utils.Indenter;
import io.fabric8.utils.Block;
import io.fabric8.utils.Objects;
import io.fabric8.utils.Strings;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NodeDtos {
    private static Set<String> patternsToPrefix = new HashSet<>(Arrays.asList("camelContext", "route", "from", "to"));

    public static List<NodeDto> toNodeList(Iterable<? extends NodeDto> nodes) {
        return toNodeList(nodes, "  ");
    }

    public static List<NodeDto> toNodeList(Iterable<? extends NodeDto> nodes, String indentation) {
        List<NodeDto> answer = new ArrayList<>();
        if (nodes != null) {
            for (NodeDto node : nodes) {
                List<NodeDto> list = node.toNodeList(indentation);
                answer.addAll(list);
            }
        }
        return answer;
    }

    public static List<ContextDto> parseContexts(File file) throws java.io.IOException {
        ObjectMapper mapper = new ObjectMapper();
        MappingIterator<ContextDto> iter = mapper.readerFor(ContextDto.class).readValues(file);
        return toList(iter);
    }

    public static List<ContextDto> parseContexts(String message) throws java.io.IOException {
        ObjectMapper mapper = new ObjectMapper();
        MappingIterator<ContextDto> iter = mapper.readerFor(ContextDto.class).readValues(message);
        return toList(iter);
    }

    protected static <T> List<T> toList(MappingIterator<T> iter) throws java.io.IOException {
        List<T> answer = new ArrayList<>();
        while (iter != null && iter.hasNextValue()) {
            T value = iter.nextValue();
            answer.add(value);
        }
        return answer;
    }

    public static void printNode(final Indenter out, final NodeDtoSupport node) throws Exception {
        if (node != null) {
            out.println(getNodeText(node));
            out.withIndent(new Block() {

                @Override
                public void invoke() throws Exception {
                    for (NodeDto child : node.getChildren()) {
                        printNode(out, child);
                    }
                }
            });
        }
    }

    public static String getNodeText(NodeDtoSupport node) {
        String pattern = Strings2.getOrElse(node.getPattern());
        String label = Strings2.getOrElse(node.getLabel());

        // lets output the pattern for a few kinds of nodes....
        if (patternsToPrefix.contains(pattern)) {
            return Strings.join(" ", pattern, label);
        }
        return label;
    }

    public static NodeDto findNodeByKey(Iterable<? extends NodeDto> nodes, String key) {
        if (nodes != null) {
            for (NodeDto node : nodes) {
                if (Objects.equal(node.getKey(), key)) {
                    return node;
                }
                NodeDto answer = findNodeByKey(node.getChildren(), key);
                if (answer != null) {
                    return answer;
                }
            }
        }
        return null;
    }
}

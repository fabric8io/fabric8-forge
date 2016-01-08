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
package io.fabric8.forge.addon.utils;

import io.fabric8.utils.Files;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.resource.Resource;
import org.jboss.forge.addon.resource.util.ResourceUtil;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UISelection;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.UIInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.fabric8.forge.addon.utils.Files.joinPaths;

/**
 */
public class CommandHelpers {
    private static final transient Logger LOG = LoggerFactory.getLogger(CommandHelpers.class);
    
    /**
     * A helper function to add the components to the builder and return a list of all the components
     */
    public static List<InputComponent> addInputComponents(UIBuilder builder, InputComponent... components) {
        List<InputComponent> inputComponents = new ArrayList<>();
        for (InputComponent component : components) {
            builder.add(component);
            inputComponents.add(component);
        }
        return inputComponents;
    }

    public static void putComponentValuesInAttributeMap(UIExecutionContext context, List<InputComponent> inputComponents) {
        if (inputComponents != null) {
            Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();
            for (InputComponent inputComponent : inputComponents) {
                String name = inputComponent.getName();
                Object value = inputComponent.getValue();
                if (value != null) {
                    attributeMap.put(name, value);
                }
            }
        }
    }

    /**
     * If the initial value is not blank lets set the value on the underlying component
     */
    public static <T> void setInitialComponentValue(UIInput<T> inputComponent, T value) {
        if (value != null) {
            inputComponent.setValue(value);
        }
    }

    public static File getBaseDir(Project project) {
        if (project == null) {
            return null;
        }
        Resource<?> root = project.getRoot();
        if (root == null) {
            return null;
        }
        return ResourceUtil.getContextFile(root);
    }

    public static File getProjectContextFile(UIContext context, Project project, String fileName) {
        if (project != null) {
            Resource<?> root = project.getRoot();
            if (root != null) {
                Resource<?> fileResource = root.getChild(fileName);
                if (fileResource != null) {
                    File answer = ResourceUtil.getContextFile(fileResource);
                    if (answer != null) {
                        return answer;
                    } else {
                        LOG.info("No file found for resource " + fileResource + " for " + fileName);
                    }
                }
                File folder = ResourceUtil.getContextFile(root);
                if (folder != null && Files.isDirectory(folder)) {
                    return new File(folder, fileName);
                } else {
                    LOG.info("No context root selection - found: " + folder);
                }
            } else {
                LOG.info("No root for project!");
            }
        }
        UISelection<Object> selection = context.getSelection();
        if (selection != null) {
            Object object = selection.get();
            if (object instanceof Resource) {
                File folder = ResourceUtil.getContextFile((Resource<?>) object);
                if (folder != null && Files.isDirectory(folder)) {
                    return new File(folder, fileName);
                } else {
                    LOG.info("No context root selection - found: " + folder);
                }
            }
        } else {
            LOG.info("Context has no selection!");
        }
        return null;
    }

    public static File getProjectResourceFile(UIContext context, Project project, String fileName) {
        return getProjectContextFile(context, project, joinPaths("src/main/resources", fileName));
    }
}

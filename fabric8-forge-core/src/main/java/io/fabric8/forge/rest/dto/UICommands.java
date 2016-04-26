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

import io.fabric8.forge.rest.ui.RestUIContext;
import io.fabric8.forge.rest.ui.RestUIProvider;
import io.fabric8.utils.Strings;
import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.convert.ConverterFactory;
import org.jboss.forge.addon.projects.ProjectProvider;
import org.jboss.forge.addon.projects.ProjectType;
import org.jboss.forge.addon.projects.stacks.Stack;
import org.jboss.forge.addon.projects.stacks.StackFacet;
import org.jboss.forge.addon.ui.command.UICommand;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.controller.CommandController;
import org.jboss.forge.addon.ui.input.HasCompleter;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.SelectComponent;
import org.jboss.forge.addon.ui.input.UICompleter;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.output.UIMessage;
import org.jboss.forge.addon.ui.result.CompositeResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.util.InputComponents;
import org.jboss.forge.furnace.proxy.Proxies;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static io.fabric8.forge.rest.dto.UIMessageDTO.toDtoList;
import static org.jboss.forge.furnace.util.Strings.capitalize;

/**
 */
public class UICommands {
    public static CommandInfoDTO createCommandInfoDTO(RestUIContext context, UICommand command) {
        CommandInfoDTO answer;
        UICommandMetadata metadata = command.getMetadata(context);
        String metadataName = unshellifyName(metadata.getName());
        String id = shellifyName(metadataName);
        String description = metadata.getDescription();
        String category = toStringOrNull(metadata.getCategory());
        String docLocation = toStringOrNull(metadata.getDocLocation());
        boolean enabled = command.isEnabled(context);
        answer = new CommandInfoDTO(id, metadataName, description, category, docLocation, enabled);
        return answer;
    }


    public static CommandInputDTO createCommandInputDTO(RestUIContext context, UICommand command, CommandController controller) throws Exception {
        CommandInfoDTO info = createCommandInfoDTO(context, command);
        CommandInputDTO inputInfo = new CommandInputDTO(info);
        Map<String, InputComponent<?, ?>> inputs = controller.getInputs();
        if (inputs != null) {
            Set<Map.Entry<String, InputComponent<?, ?>>> entries = inputs.entrySet();
            for (Map.Entry<String, InputComponent<?, ?>> entry : entries) {
                String key = entry.getKey();
                InputComponent<?, ?> input = entry.getValue();
                PropertyDTO dto = UICommands.createInputDTO(context, input);
                inputInfo.addProperty(key, dto);
            }
        }
        return inputInfo;
    }

    protected static String toStringOrNull(Object value) {
        return value != null ? value.toString() : null;
    }

    public static PropertyDTO createInputDTO(UIContext context, InputComponent<?, ?> input) {
        String name = input.getName();
        String description = input.getDescription();
        String label = input.getLabel();
        String requiredMessage = input.getRequiredMessage();
        char shortNameChar = input.getShortName();
        String shortName = Character.toString(shortNameChar);
        Object inputValue = InputComponents.getValueFor(input);

/*
        if (input instanceof ManyValued) {
            ManyValued manyValued = (ManyValued) input;
            inputValue = manyValued.getValue();
        }
*/
        Object value = convertValueToSafeJson(input.getValueConverter(), inputValue);

        Class<?> valueType = input.getValueType();
        String javaType = null;
        if (valueType != null) {
            javaType = valueType.getCanonicalName();
        }
        String type = JsonSchemaTypes.getJsonSchemaTypeName(valueType);
        boolean enabled = input.isEnabled();
        boolean required = input.isRequired();
        List<Object> enumValues = new ArrayList<>();
        List<Object> typeaheadData = new ArrayList<>();
        if (input instanceof SelectComponent) {
            SelectComponent selectComponent = (SelectComponent) input;
            Iterable valueChoices = selectComponent.getValueChoices();
            Converter converter = selectComponent.getItemLabelConverter();

            boolean isJson = isJsonDTO(javaType);
            for (Object valueChoice : valueChoices) {
                Object jsonValue;
                if (isJson) {
                    jsonValue = Proxies.unwrap(valueChoice);
                } else {
                    jsonValue = convertValueToSafeJson(converter, valueChoice);
                }
                enumValues.add(jsonValue);
            }
        }
        if (input instanceof HasCompleter) {
            HasCompleter hasCompleter = (HasCompleter) input;
            UICompleter completer = hasCompleter.getCompleter();
            if (completer != null) {
                String textValue = inputValue != null ? inputValue.toString() : "";
                Iterable valueChoices = completer.getCompletionProposals(context, input, textValue);
                // TODO is there a way to find a converter?
                Converter converter = null;
                for (Object valueChoice : valueChoices) {
                    Object jsonValue = convertValueToSafeJson(converter, valueChoice);
                    typeaheadData.add(jsonValue);
                }
            }
        }
        if (enumValues.isEmpty()) {
            enumValues = null;
        }
        if (typeaheadData.isEmpty()) {
            typeaheadData = null;
        }
        return new PropertyDTO(name, description, label, requiredMessage, value, javaType, type, enabled, required, enumValues, typeaheadData);
    }

    /**
     * Returns true if the given <code>javaType</code> class name should be considered a JSON DTO to return to the UI
     */
    protected static boolean isJsonDTO(String javaType) {
        Set<String> jsonJavatypes = new HashSet<>(Arrays.asList("org.apache.maven.archetype.catalog.Archetype"));
        return javaType != null && jsonJavatypes.contains(javaType);
    }

    /**
     * Uses the given converter to convert to a nicer UI value and return the JSON safe version
     */
    public static Object convertValueToSafeJson(Converter converter, Object value) {
        value = Proxies.unwrap(value);
        if (isJsonObject(value)) {
            return value;
        }
        if (converter != null) {
            // TODO converters ususally go from String -> CustomType?
            try {
                Object converted = converter.convert(value);
                if (converted != null) {
                    value = converted;
                }
            } catch (Exception e) {
                // ignore - invalid converter
            }
        }
        if (value != null) {
            return toSafeJsonValue(value);
        } else {
            return null;
        }
    }

    /**
     * Lets return a safe JSON value
     */
    protected static Object toSafeJsonValue(Object value) {
        if (value == null) {
            return null;
        } else {
            if (value instanceof Boolean || value instanceof String || value instanceof Number) {
                return value;
            }
            if (value instanceof Iterable) {
                Iterable iterable = (Iterable) value;
                List answer = new ArrayList<>();
                for (Object item : iterable) {
                    Object itemJson = toSafeJsonValue(item);
                    if (itemJson != null) {
                        answer.add(itemJson);
                    }
                }
                return answer;
            }
            if (value instanceof ProjectProvider) {
                ProjectProvider projectProvider = (ProjectProvider) value;
                return projectProvider.getType();
            }
            if (value instanceof ProjectType) {
                ProjectType projectType = (ProjectType) value;
                return projectType.getType();
            }
            if (value instanceof StackFacet) {
                StackFacet stackFacet = (StackFacet) value;
                Stack stack = stackFacet.getStack();
                if (stack != null) {
                    return stack.getName();
                } else {
                    return null;
                }
            }
            value = Proxies.unwrap(value);
            if (isJsonObject(value)) {
                return value;
            }
            return value.toString();
        }
    }

    protected static boolean isJsonObject(Object value) {
        if (value != null) {
            Class<?> aClass = value.getClass();
            while (aClass != null && !aClass.equals(Object.class)) {
                Annotation[] annotations = aClass.getAnnotations();
                if (annotations != null) {
                    for (Annotation annotation : annotations) {
                        String annotationClassName = annotation.getClass().getName();
                        if (annotationClassName != null && annotationClassName.startsWith("com.fasterxml.jackson.")) {
                            // lets assume its a JSON DTO!
                            return true;
                        } else {
                            String text = annotation.toString();
                            // because of the Forge proxying we can't just use the actual class here...
                            if (text.indexOf("com.fasterxml.jackson.") >= 0) {
                                return true;
                            }
                        }
                    }
                }
                aClass = aClass.getSuperclass();
            }
        }
        return false;
    }

    private static final Pattern WHITESPACES = Pattern.compile("\\W+");
    private static final Pattern COLONS = Pattern.compile("\\:");

    /**
     * "Shellifies" a name (that is, makes the name shell-friendly) by replacing spaces with "-" and removing colons
     *
     * @param name
     * @return
     */
    public static String shellifyName(String name) {
       return COLONS.matcher(WHITESPACES.matcher(name.trim()).replaceAll("-")).replaceAll("").toLowerCase();
    }


    /**
     * A name of the form "foo-bar-whatnot" is turned into "Foo: Bar Whatnot"
     */
    public static String unshellifyName(String name) {
        if (Strings.isNotBlank(name)) {
            if (name.indexOf('-') >= 0 && name.toLowerCase().equals(name)) {
                String[] split = name.split("-");
                StringBuffer buffer = new StringBuffer();
                int idx = 0;
                for (String part : split) {
                    if (idx == 1) {
                        buffer.append(": ");
                    } else if (idx > 1) {
                        buffer.append(" ");
                    }
                    buffer.append(capitalize(part));
                    idx++;
                }
                return buffer.toString();
            }
        }
        return name;
    }

    public static void populateController(Map<String, Object> requestedInputs, CommandController controller, ConverterFactory converterFactory) {
        Map<String, InputComponent<?, ?>> inputs = controller.getInputs();
        Set<String> inputKeys = new HashSet<>(inputs.keySet());
        if (requestedInputs != null) {
            inputKeys.retainAll(requestedInputs.keySet());
            Set<Map.Entry<String, InputComponent<?, ?>>> entries = inputs.entrySet();
            for (Map.Entry<String, InputComponent<?, ?>> entry : entries) {
                String key = entry.getKey();
                InputComponent<?, ?> component = entry.getValue();
                Object value = requestedInputs.get(key);
                String textValue = null;
                if (value instanceof String || value instanceof Number || value instanceof Date) {
                    textValue = value.toString();
                }
/*
                String textValue = requestedInputs.get(key);
                Object value = textValue;
*/
                if (component != null && textValue != null) {
                    Converter<String, ?> valueConverter = component.getValueConverter();
                    if (valueConverter != null) {
                        value = valueConverter.convert(textValue);
                    } else {
                        Class<?> valueType = component.getValueType();
                        if (valueType.isEnum()) {
                            Class<? extends Enum> enumType = (Class<? extends Enum>) valueType;
                            value = Enum.valueOf(enumType, textValue);
                        }
                    }
                    InputComponents.setValueFor(converterFactory, component, value);
                } else {
                    controller.setValueFor(key, value);
                }

                Object actual = controller.getValueFor(key);
            }
        }
    }

    public static ExecutionResult createExecutionResult(RestUIContext context, Result result, boolean canMoveToNextStep) {
        RestUIProvider provider = context.getProvider();
        String out = provider.getOut();
        String err = provider.getErr();
        String message = result != null ? getResultMessage(result) : null;
        String detail = null;
        ExecutionStatus status = ExecutionStatus.SUCCESS;
        return new ExecutionResult(status, message, out, err, detail, canMoveToNextStep);
    }


    public static ValidationResult createValidationResult(RestUIContext context, CommandController controller, List<UIMessage> messages) {
        boolean valid = controller.isValid();
        boolean canExecute = controller.canExecute();

        RestUIProvider provider = context.getProvider();
        String out = provider.getOut();
        String err = provider.getErr();
        return new ValidationResult(toDtoList(messages), valid, canExecute, out, err);
    }

    protected static String getResultMessage(Result result) {
        if (result instanceof CompositeResult) {
            CompositeResult compositeResult = (CompositeResult) result;
            List<Result> results = compositeResult.getResults();
            StringBuilder buffer = new StringBuilder();
            for (Result childResult : results) {
                String childResultMessage = getResultMessage(childResult);
                if (Strings.isNotBlank(childResultMessage)) {
                    if (buffer.length() > 0) {
                        buffer.append("\n");
                    }
                    buffer.append(childResultMessage);
                }
            }
            return buffer.toString();
        } else if (result != null) {
            return result.getMessage();
        } else {
            return null;
        }
    }

}

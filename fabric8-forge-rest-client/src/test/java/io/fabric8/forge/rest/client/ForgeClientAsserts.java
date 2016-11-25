/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.forge.rest.client;

import io.fabric8.forge.rest.dto.ExecutionResult;
import io.fabric8.forge.rest.dto.PropertyDTO;
import io.fabric8.forge.rest.dto.ValidationResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 */
public class ForgeClientAsserts {
    protected static boolean doAssert = true;

    public static void assertValidAndExecutable(ValidationResult result) {
        assertThat(result).isNotNull();

        if (doAssert) {
            assertThat(result.isValid()).describedAs("isValid").isTrue();
            assertThat(result.isCanExecute()).describedAs("isCanExecute").isTrue();
        } else {
            System.out.println("isValid: " + result.isValid());
            System.out.println("isCanExecute: " + result.isCanExecute());
        }
    }

    public static void assertCanMoveToNextStep(ExecutionResult result) {
        assertThat(result).isNotNull();
        if (doAssert) {
            assertThat(result.isCanMoveToNextStep()).describedAs("isCanMoveToNextStep").isTrue();
            assertThat(result.isCommandCompleted()).describedAs("isCommandCompleted").isFalse();
        } else {
            System.out.println("isCanMoveToNextStep: " + result.isCanMoveToNextStep());
            System.out.println("isCommandCompleted: " + result.isCommandCompleted());
        }
    }

    public static void assertExecutionWorked(ExecutionResult result) {
        assertThat(result).isNotNull();
        assertThat(result.isCommandCompleted()).describedAs("isCommandCompleted").isTrue();
        assertThat(result.isCanMoveToNextStep()).describedAs("isCanMoveToNextStep").isFalse();
    }

    public static Object assertChooseValue(String propertyName, PropertyDTO property, int pageNumber, String value) {
        List<Object> valueChoices = property.getValueChoices();
        if (valueChoices == null || valueChoices.isEmpty()) {
            valueChoices = property.getTypeaheadData();
        }
        if (valueChoices == null || valueChoices.isEmpty()) {
            // lets assume that we are in the initial request - and that validate will populate this!
            return null;
        }

        boolean contained = valueChoices.contains(value);
        if (!contained) {
            // we may have maps containing a value property
            for (Object choice : valueChoices) {
                if (choice instanceof Map) {
                    Map map = (Map) choice;
                    Object text = map.get("value");
                    if (text != null && value.equals(text)) {
                        contained = true;
                        break;
                    }

                }
            }
        }
        assertThat(contained).describedAs(("Choices for property " + propertyName + " on page " + pageNumber + " with choices: " + valueChoices) + " does not contain " + value).isTrue();
        return value;
    }
}

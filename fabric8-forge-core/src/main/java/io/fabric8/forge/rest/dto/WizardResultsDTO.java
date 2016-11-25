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

import java.util.List;

/**
 */
public class WizardResultsDTO {
    private List<CommandInputDTO> stepInputs;
    private List<ValidationResult> stepValidations;
    private List<ExecutionResult> stepResults;

    public WizardResultsDTO() {
    }

    public WizardResultsDTO(List<CommandInputDTO> stepInputs, List<ValidationResult> stepValidations, List<ExecutionResult> stepResults) {
        this.stepInputs = stepInputs;
        this.stepValidations = stepValidations;
        this.stepResults = stepResults;
    }

    @Override
    public String toString() {
        return "WizardResultsDTO{" +
                "stepInputs=" + stepInputs +
                ", stepResults=" + stepResults +
                '}';
    }

    public List<CommandInputDTO> getStepInputs() {
        return stepInputs;
    }

    public List<ValidationResult> getStepValidations() {
        return stepValidations;
    }

    public List<ExecutionResult> getStepResults() {
        return stepResults;
    }

    public void setStepInputs(List<CommandInputDTO> stepInputs) {
        this.stepInputs = stepInputs;
    }

    public void setStepValidations(List<ValidationResult> stepValidations) {
        this.stepValidations = stepValidations;
    }

    public void setStepResults(List<ExecutionResult> stepResults) {
        this.stepResults = stepResults;
    }
}

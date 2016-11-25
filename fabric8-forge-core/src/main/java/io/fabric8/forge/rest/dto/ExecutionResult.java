/**
 * Copyright 2005-2015 Red Hat, Inc.
 * <p>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.forge.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fabric8.utils.Strings;

import java.util.HashMap;
import java.util.Map;

public class ExecutionResult {
    private String projectName;
    private ExecutionStatus status;
    private String message;
    private String output;
    private String err;
    private String detail;
    private WizardResultsDTO wizardResults;
    private boolean canMoveToNextStep;
    private Map<String, String> outputProperties = new HashMap<>();

    public ExecutionResult() {
    }

    public ExecutionResult(ExecutionStatus status, String message, String output, String err, String detail, boolean canMoveToNextStep) {
        this.status = status;
        this.message = message;
        this.output = output;
        this.err = err;
        this.detail = detail;
        this.canMoveToNextStep = canMoveToNextStep;
    }

    @Override
    public String toString() {
        return "ExecutionResult{" +
                "status=" + status +
                ", message='" + message + '\'' +
                ", output='" + output + '\'' +
                ", err='" + err + '\'' +
                ", detail='" + detail + '\'' +
                '}';
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getErr() {
        return err;
    }

    public void setErr(String err) {
        this.err = err;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public WizardResultsDTO getWizardResults() {
        return wizardResults;
    }

    public void setWizardResults(WizardResultsDTO wizardResults) {
        this.wizardResults = wizardResults;
    }

    public boolean isCanMoveToNextStep() {
        return canMoveToNextStep;
    }

    public void setCanMoveToNextStep(boolean canMoveToNextStep) {
        this.canMoveToNextStep = canMoveToNextStep;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    /**
     * Returns true if the command completed successfully and its either not a wizard command or it is a wizard and the last page was completed
     */
    @JsonIgnore
    public boolean isCommandCompleted() {
        return status.equals(ExecutionStatus.SUCCESS) && (wizardResults == null || !isCanMoveToNextStep());
    }

    public void appendOut(String text) {
        if (Strings.isNullOrBlank(this.output)) {
            this.output = text;
        } else {
            this.output += "\n" + text;
        }
    }

    public Map<String, String> getOutputProperties() {
        return outputProperties;
    }

    public void setOutputProperties(Map<String, String> outputProperties) {
        this.outputProperties = outputProperties;
    }

    public void setOutputProperty(String name, String value) {
        if (outputProperties == null) {
            outputProperties = new HashMap<>();
        }
        outputProperties.put(name, value);
    }
}

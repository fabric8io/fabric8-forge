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

import org.jboss.forge.addon.ui.output.UIMessage;

import java.util.List;

/**
 */
public class ValidationResult {
    private List<UIMessageDTO> messages;
    private boolean valid;
    private boolean canExecute;
    private String out;
    private String err;
    private WizardResultsDTO wizardResults;

    public ValidationResult() {
    }

    public ValidationResult(List<UIMessageDTO> messages, boolean valid, boolean canExecute, String out, String err) {
        this.messages = messages;
        this.valid = valid;
        this.canExecute = canExecute;
        this.out = out;
        this.err = err;
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
                "messages=" + messages +
                ", valid=" + valid +
                ", canExecute=" + canExecute +
                ", out='" + out + '\'' +
                ", err='" + err + '\'' +
                '}';
    }

    public boolean isCanExecute() {
        return canExecute;
    }

    public String getErr() {
        return err;
    }

    public List<UIMessageDTO> getMessages() {
        return messages;
    }

    public String getOut() {
        return out;
    }

    public boolean isValid() {
        return valid;
    }

    public WizardResultsDTO getWizardResults() {
        return wizardResults;
    }

    public void setMessages(List<UIMessageDTO> messages) {
        this.messages = messages;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public void setCanExecute(boolean canExecute) {
        this.canExecute = canExecute;
    }

    public void setOut(String out) {
        this.out = out;
    }

    public void setErr(String err) {
        this.err = err;
    }

    public void setWizardResults(WizardResultsDTO wizardResults) {
        this.wizardResults = wizardResults;
    }

    /**
     * Adds an extra validation error
     */
    public void addValidationError(String message) {
        messages.add(new UIMessageDTO(message, null, UIMessage.Severity.ERROR));
        valid = false;
        canExecute = false;
    }
}

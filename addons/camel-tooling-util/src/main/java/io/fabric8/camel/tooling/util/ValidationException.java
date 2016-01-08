/**
 * Copyright 2005-2015 Red Hat, Inc.
 * <p/>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.camel.tooling.util;

import org.xml.sax.SAXParseException;

import java.util.LinkedList;
import java.util.List;

public class ValidationException extends Exception {

    private final String userMessage;
    List<SAXParseException> errors = new LinkedList<SAXParseException>();
    List<SAXParseException> fatalErrors = new LinkedList<SAXParseException>();
    List<SAXParseException> warnings = new LinkedList<SAXParseException>();

    public ValidationException(String message, String userMessage, List<SAXParseException> errors, List<SAXParseException> fatalErrors, List<SAXParseException> warnings) {
        super(message);
        this.errors = errors;
        this.fatalErrors = fatalErrors;
        this.warnings = warnings;
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }

}

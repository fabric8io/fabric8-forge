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
package io.fabric8.forge.camel.commands.project.completer;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.forge.addon.utils.LineNumberHelper;
import io.fabric8.forge.camel.commands.project.helper.PoorMansLogger;
import io.fabric8.forge.camel.commands.project.model.CamelEndpointDetails;
import org.jboss.forge.addon.projects.facets.ResourcesFacet;
import org.jboss.forge.addon.projects.facets.WebResourcesFacet;
import org.jboss.forge.addon.resource.FileResource;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.UICompleter;

public class CurrentLineCompleter implements UICompleter<String> {

    private static final PoorMansLogger LOG = new PoorMansLogger(false);

    private final int lineNumber;
    private final String relativeFile;
    private final ResourcesFacet facet;
    private final WebResourcesFacet webFacet;
    private final String line;

    public CurrentLineCompleter(int lineNumber, String relativeFile, final ResourcesFacet facet, final WebResourcesFacet webFacet) throws Exception {
        this.lineNumber = lineNumber;
        this.relativeFile = relativeFile;
        this.facet = facet;
        this.webFacet = webFacet;
        this.line = getCurrentCursorLineText();
        LOG.info("Created CurrentLineCompleter[lineNumber=" + lineNumber + ",relativeFile=" + relativeFile + ",line=" + line + "]");
    }

    public CamelEndpointDetails getEndpoint() {
        if (line != null) {
            String uri = getCamelEndpointUri(line);
            String component = getEndpointComponentName(uri);
            if (uri != null && component != null) {
                CamelEndpointDetails details = new CamelEndpointDetails();
                details.setLineNumber("" + lineNumber);
                details.setLineNumberEnd("" + lineNumber);
                details.setEndpointUri(uri);
                details.setEndpointComponentName(component);
                details.setFileName(relativeFile);
                LOG.info("CurrentLineCompleter uri: " + details.getEndpointUri());
                return details;
            }
        }
        return null;
    }

    @Override
    public Iterable<String> getCompletionProposals(UIContext uiContext, InputComponent<?, String> inputComponent, String value) {
        List<String> answer = new ArrayList<String>();

        if (line != null && (value == null || line.startsWith(value))) {
            answer.add(line);
        }

        return answer;

    }

    protected String getCurrentCursorLineText() throws Exception {
        FileResource file = facet != null ? facet.getResource(relativeFile) : null;
        if (file == null || !file.exists()) {
            file = webFacet != null ? webFacet.getWebResource(relativeFile) : null;
        }
        if (file != null) {
            // read all the lines
            List<String> lines = LineNumberHelper.readLines(file.getResourceInputStream());

            // the list is 0-based, and line number is 1-based
            int idx = lineNumber - 1;
            String line = lines.get(idx);

            return line;
        }
        return null;
    }

    protected String getCamelEndpointUri(String line) {
        String uri = line;
        boolean properties = relativeFile.endsWith(".properties") || relativeFile.endsWith(".cfg");
        if (properties) {
            if (line.contains("=")) {
                uri = line.substring(line.indexOf("=") + 1);
            }
        }
        return uri != null ? uri.trim() : null;
    }

    protected String getEndpointComponentName(String uri) {
        if (uri.contains(":")) {
            return uri.substring(0, uri.indexOf(":"));
        }
        return null;
    }

}

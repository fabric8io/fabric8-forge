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
package io.fabric8.forge.devops.setup;

import java.util.Arrays;
import java.util.Properties;
import javax.inject.Inject;

import io.fabric8.utils.Strings;
import org.apache.maven.model.Model;
import org.jboss.forge.addon.facets.constraints.FacetConstraint;
import org.jboss.forge.addon.maven.projects.MavenFacet;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.facets.HintsFacet;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;

@FacetConstraint({MavenFacet.class})
public class ReadinessProbeSetupCommand extends AbstractFabricProjectCommand {

    private static final String[] TYPES = new String[]{"EXEC", "HTTP", "TCP"};

    @Inject
    @WithAttributes(label = "Type", required = true, description = "Whether to use EXEC, HTTP, or TCP readiness probe")
    private UISelectOne<String> type;

    @Inject
    @WithAttributes(label = "Exec", description = "Creates a exec action readiness probe with this command")
    private UIInput<String> exec;

    @Inject
    @WithAttributes(label = "HTTP Host", description = "Creates a HTTP GET action readiness probe on this host. To use readiness probe with HTTP you must configure at least the port and path options.")
    private UIInput<String> httpHost;

    @Inject
    @WithAttributes(label = "HTTP Port", description = "Creates a HTTP GET action readiness probe on this port. The default value is 80.")
    private UIInput<Integer> httpPort;

    @Inject
    @WithAttributes(label = "HTTP Path", description = "Creates a HTTP GET action readiness probe on with this path.")
    private UIInput<String> httpPath;

    @Inject
    @WithAttributes(label = "Port", description = "Creates a TCP socket action readiness probe on specified port")
    private UIInput<Integer> port;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(ReadinessProbeSetupCommand.class).name(
                "Fabric8: Readiness Probe").category(Categories.create(AbstractFabricProjectCommand.CATEGORY))
                .description("Add/Update Kubernetes Readiness Probe");
    }

    @Override
    public boolean isEnabled(UIContext context) {
        Project project = getSelectedProjectOrNull(context);
        // only enable if we do not have Camel yet
        if (project == null) {
            // must have a project
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void initializeUI(final UIBuilder builder) throws Exception {
        Project project = getSelectedProject(builder.getUIContext());

        MavenFacet maven = project.getFacet(MavenFacet.class);
        Model pom = maven.getModel();
        final Properties properties = pom.getProperties();

        type.setValueChoices(Arrays.asList(TYPES));
        // use HTTP as default
        selectType(TYPES[1]);
        type.setDefaultValue(TYPES[1]);

        if (properties != null) {
            exec.setDefaultValue(properties.getProperty("fabric8.readinessProbe.exec", ""));
            httpHost.setDefaultValue(properties.getProperty("fabric8.readinessProbe.httpGet.host", ""));
            String val = properties.getProperty("fabric8.readinessProbe.httpGet.port", "80");
            if (Strings.isNotBlank(val)) {
                httpPort.setDefaultValue(Integer.valueOf(val));
            }
            httpPath.setDefaultValue(properties.getProperty("fabric8.readinessProbe.httpGet.path", ""));
            val = properties.getProperty("fabric8.readinessProbe.port", "");
            if (Strings.isNotBlank(val)) {
                port.setDefaultValue(Integer.valueOf(val));
            }

            if (Strings.isNotBlank(exec.getValue())) {
                selectType(TYPES[0]);
                type.setDefaultValue(TYPES[0]);
            } else if (port.getValue() != null) {
                selectType(TYPES[2]);
                type.setDefaultValue(TYPES[2]);
            } else {
                selectType(TYPES[1]);
                type.setDefaultValue(TYPES[1]);
            }
        }

        type.addValueChangeListener(event -> {
            selectType(event.getNewValue());
        });

        builder.add(type).add(exec).add(httpHost).add(httpPort).add(httpPath).add(port);
    }

    private void selectType(Object type) {
        if (TYPES[0].equals(type)) {
            exec.setRequired(true);
            exec.setEnabled(true);
            exec.getFacet(HintsFacet.class).setPromptInInteractiveMode(true);
            httpHost.setRequired(false);
            httpHost.setEnabled(false);
            httpHost.setValue(null);
            httpPort.setRequired(false);
            httpPort.setEnabled(false);
            httpPort.setValue(null);
            httpPath.setRequired(false);
            httpPath.setEnabled(false);
            httpPath.setValue(null);
            port.setRequired(false);
            port.setEnabled(false);
            port.setValue(null);
        } else if (TYPES[1].equals(type)) {
            exec.setRequired(false);
            exec.setEnabled(false);
            exec.setValue(null);
            // host is optional
            httpHost.getFacet(HintsFacet.class).setPromptInInteractiveMode(true);
            httpHost.setRequired(false);
            httpHost.setEnabled(true);
            httpPort.setRequired(true);
            httpPort.setEnabled(true);
            httpPath.setRequired(true);
            httpPath.setEnabled(true);
            port.setRequired(false);
            port.setEnabled(false);
            port.setValue(null);
        } else if (TYPES[2].equals(type)) {
            exec.setRequired(false);
            exec.setEnabled(false);
            exec.setValue(null);
            httpHost.setRequired(false);
            httpHost.setEnabled(false);
            httpHost.setValue(null);
            httpPort.setRequired(false);
            httpPort.setEnabled(false);
            httpPort.setValue(null);
            httpPath.setRequired(false);
            httpPath.setEnabled(false);
            httpPath.setValue(null);
            port.getFacet(HintsFacet.class).setPromptInInteractiveMode(true);
            port.setRequired(true);
            port.setEnabled(true);
        }
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        Project project = getSelectedProject(context);

        // update properties section in pom.xml
        MavenFacet maven = project.getFacet(MavenFacet.class);
        Model pom = maven.getModel();
        Properties properties = pom.getProperties();
        boolean updated = false;

        if (TYPES[0].equals(type.getValue())) {
            if (Strings.isNotBlank(exec.getValue())) {
                properties.put("fabric8.readinessProbe.exec", exec.getValue());
                updated = true;
                // remove the http/port as we use exec
                properties.remove("fabric8.readinessProbe.httpGet.host");
                properties.remove("fabric8.readinessProbe.httpGet.port");
                properties.remove("fabric8.readinessProbe.httpGet.path");
                properties.remove("fabric8.readinessProbe.port");
            }
        } else if (TYPES[1].equals(type.getValue())) {
            if (Strings.isNotBlank(httpHost.getValue())) {
                properties.put("fabric8.readinessProbe.httpGet.host", httpHost.getValue());
                updated = true;
                // remove the exec/port as we use http
                properties.remove("fabric8.readinessProbe.exec");
                properties.remove("fabric8.readinessProbe.port");
            }
            if (httpPort.getValue() != null) {
                properties.put("fabric8.readinessProbe.httpGet.port", "" + httpPort.getValue());
                updated = true;
                // remove the exec/port as we use http
                properties.remove("fabric8.readinessProbe.exec");
                properties.remove("fabric8.readinessProbe.port");
            }
            if (Strings.isNotBlank(httpPath.getValue())) {
                properties.put("fabric8.readinessProbe.httpGet.path", httpPath.getValue());
                updated = true;
                // remove the exec/port as we use http
                properties.remove("fabric8.readinessProbe.exec");
                properties.remove("fabric8.readinessProbe.port");
            }
        } else if (TYPES[2].equals(type.getValue())) {
            if (port.getValue() != null) {
                properties.put("fabric8.readinessProbe.port", "" + port.getValue());
                updated = true;
                // remove the exec/http as we use port
                properties.remove("fabric8.readinessProbe.exec");
                properties.remove("fabric8.readinessProbe.httpGet.host");
                properties.remove("fabric8.readinessProbe.httpGet.port");
                properties.remove("fabric8.readinessProbe.httpGet.path");
            }
        }

        // to save then set the model
        if (updated) {
            maven.setModel(pom);
        }

        return Results.success("Kubernetes readiness probe updated");
    }

}

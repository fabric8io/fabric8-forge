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

import io.fabric8.forge.addon.utils.MavenHelpers;
import io.fabric8.utils.Strings;
import org.apache.maven.model.Model;
import org.hibernate.validator.constraints.Range;
import org.hibernate.validator.valuehandling.UnwrapValidatedValue;
import org.jboss.forge.addon.facets.constraints.FacetConstraint;
import org.jboss.forge.addon.maven.plugins.MavenPlugin;
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
public class LivenessProbeSetupCommand extends AbstractFabricProjectCommand {

    private static final String[] TYPES = new String[]{"EXEC", "HTTP", "TCP"};

    @Inject
    @WithAttributes(label = "Type", required = true, description = "Whether to use EXEC, HTTP, or TCP liveness probe")
    private UISelectOne<String> type;

    @Inject
    @WithAttributes(label = "Exec", description = "Creates a exec action liveness probe with this command")
    private UIInput<String> exec;

    @Inject
    @WithAttributes(label = "HTTP Host", description = "Creates a HTTP GET action liveness probe on this host. To use liveness probe with HTTP you must configure at least the port and path options.")
    private UIInput<String> httpHost;

    @Inject
    @WithAttributes(label = "HTTP Port", description = "Creates a HTTP GET action liveness probe on this port. The default value is 80.")
    @Range(min = 0, max = 65535)
    @UnwrapValidatedValue
    private UIInput<Integer> httpPort;

    @Inject
    @WithAttributes(label = "HTTP Path", description = "Creates a HTTP GET action liveness probe on with this path.")
    private UIInput<String> httpPath;

    @Inject
    @WithAttributes(label = "Port", description = "Creates a TCP socket action liveness probe on specified port")
    @Range(min = 0, max = 65535)
    @UnwrapValidatedValue
    private UIInput<Integer> port;

    @Inject
    @WithAttributes(label = "Initial Delay", defaultValue = "5", description = "Configures an initial delay in seconds before the probe is started.")
    @Range(min = 0, max = 60)
    @UnwrapValidatedValue
    private UIInput<Integer> initialDelaySeconds;

    @Inject
    @WithAttributes(label = "Timeout", defaultValue = "30", description = "Configures a timeout in seconds which the probe will use and is expected to complete within to be successful.")
    @Range(min = 0, max = 600)
    @UnwrapValidatedValue
    private UIInput<Integer> timeoutSeconds;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(LivenessProbeSetupCommand.class).name(
                "Fabric8: Liveness Probe").category(Categories.create(AbstractFabricProjectCommand.CATEGORY))
                .description("Add/Update Kubernetes Liveness Probe");
    }

    @Override
    public boolean isEnabled(UIContext context) {
        // must be fabric8 project
        return isFabric8Project(getSelectedProjectOrNull(context));
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
            exec.setDefaultValue(properties.getProperty("fabric8.livenessProbe.exec", ""));
            httpHost.setDefaultValue(properties.getProperty("fabric8.livenessProbe.httpGet.host", ""));
            String val = properties.getProperty("fabric8.livenessProbe.httpGet.port", "80");
            if (Strings.isNotBlank(val)) {
                httpPort.setDefaultValue(Integer.valueOf(val));
            }
            httpPath.setDefaultValue(properties.getProperty("fabric8.livenessProbe.httpGet.path", ""));
            val = properties.getProperty("fabric8.livenessProbe.port", "");
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

        // we want to edit those in CLI even if they have default values
        initialDelaySeconds.getFacet(HintsFacet.class).setPromptInInteractiveMode(true);
        timeoutSeconds.getFacet(HintsFacet.class).setPromptInInteractiveMode(true);

        builder.add(type).add(exec).add(httpHost).add(httpPort).add(httpPath).add(port).add(initialDelaySeconds).add(timeoutSeconds);
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
                properties.put("fabric8.livenessProbe.exec", exec.getValue());
                updated = true;
                // remove the http/port as we use exec
                properties.remove("fabric8.livenessProbe.httpGet.host");
                properties.remove("fabric8.livenessProbe.httpGet.port");
                properties.remove("fabric8.livenessProbe.httpGet.path");
                properties.remove("fabric8.livenessProbe.port");
            }
        } else if (TYPES[1].equals(type.getValue())) {
            if (Strings.isNotBlank(httpHost.getValue())) {
                properties.put("fabric8.livenessProbe.httpGet.host", httpHost.getValue());
                updated = true;
                // remove the exec/port as we use http
                properties.remove("fabric8.livenessProbe.exec");
                properties.remove("fabric8.livenessProbe.port");
            }
            if (httpPort.getValue() != null) {
                properties.put("fabric8.livenessProbe.httpGet.port", "" + httpPort.getValue());
                updated = true;
                // remove the exec/port as we use http
                properties.remove("fabric8.livenessProbe.exec");
                properties.remove("fabric8.livenessProbe.port");
            }
            if (Strings.isNotBlank(httpPath.getValue())) {
                properties.put("fabric8.livenessProbe.httpGet.path", httpPath.getValue());
                updated = true;
                // remove the exec/port as we use http
                properties.remove("fabric8.livenessProbe.exec");
                properties.remove("fabric8.livenessProbe.port");
            }
        } else if (TYPES[2].equals(type.getValue())) {
            if (port.getValue() != null) {
                properties.put("fabric8.livenessProbe.port", "" + port.getValue());
                updated = true;
                // remove the exec/http as we use port
                properties.remove("fabric8.livenessProbe.exec");
                properties.remove("fabric8.livenessProbe.httpGet.host");
                properties.remove("fabric8.livenessProbe.httpGet.port");
                properties.remove("fabric8.livenessProbe.httpGet.path");
            }
        }

        if (initialDelaySeconds.getValue() != null) {
            properties.put("fabric8.livenessProbe.initialDelaySeconds", "" + initialDelaySeconds.getValue());
            updated = true;
        }

        if (timeoutSeconds.getValue() != null) {
            properties.put("fabric8.livenessProbe.timeoutSeconds", "" + timeoutSeconds.getValue());
            updated = true;
        }

        // to save then set the model
        if (updated) {
            maven.setModel(pom);
        }

        return Results.success("Kubernetes liveness probe updated");
    }

}

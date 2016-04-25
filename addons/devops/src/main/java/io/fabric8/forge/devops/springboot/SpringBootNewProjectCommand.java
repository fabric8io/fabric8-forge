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
package io.fabric8.forge.devops.springboot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

import io.fabric8.forge.devops.AbstractDevOpsCommand;
import io.fabric8.forge.devops.dto.SpringBootDependencyDTO;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.input.UISelectMany;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.wizard.UIWizard;

import static io.fabric8.forge.devops.springboot.IOHelper.close;
import static io.fabric8.forge.devops.springboot.IOHelper.copyAndCloseInput;
import static io.fabric8.forge.devops.springboot.OkHttpClientHelper.createOkHttpClient;
import static io.fabric8.forge.devops.springboot.UnzipHelper.unzip;

public class SpringBootNewProjectCommand extends AbstractDevOpsCommand implements UIWizard {

    private static final String STARTER_URL = "https://start.spring.io/starter.zip";

    private List<SpringBootDependencyDTO> choices;

    @Inject
    @WithAttributes(label = "Dependencies", required = true, description = "Add Spring Boot Starters and dependencies to your application")
    private UISelectMany<SpringBootDependencyDTO> dependencies;

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        choices = initDependencies();

        dependencies.setValueChoices(choices);
        dependencies.setItemLabelConverter(SpringBootDependencyDTO::getName);
        dependencies.setItemLabelConverter(SpringBootDependencyDTO::getNameAndDescription);

        builder.add(dependencies);
    }

    private List<SpringBootDependencyDTO> initDependencies() {
        List<SpringBootDependencyDTO> list = new ArrayList<>();
        list.add(new SpringBootDependencyDTO("web", "Web", "Full-stack web development with Tomcat and Spring MVC"));
        list.add(new SpringBootDependencyDTO("camel", "Apache Camel", "Integration using Apache Camel"));
        return list;
    }

    @Override
    protected boolean isProjectRequired() {
        return false;
    }

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.from(super.getMetadata(context), getClass()).name("Spring-Boot: New Project")
                .description("Create a new Spring Boot project");
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        int[] selected = dependencies.getSelectedIndexes();

        CollectionStringBuffer csb = new CollectionStringBuffer(",");
        for (int i = 0; i < selected.length; i++) {
            int val = selected[i];
            SpringBootDependencyDTO dto = choices.get(val);
            csb.append(dto.getId());
        }
        String deps = csb.toString();

        String url = STARTER_URL + "?dependencies=" + deps;

        // use http client to call start.spring.io that creates the project
        OkHttpClient client = createOkHttpClient();

        Request request = new Request.Builder()
                .url(url).build();

        Response response = client.newCall(request).execute();
        InputStream is = response.body().byteStream();

        // TODO: what was the project dir
        // TODO: whats the project name etc

        File name = new File("mydownload.zip");
        if (name.exists()) {
            name.delete();
        }

        FileOutputStream fos = new FileOutputStream(name, false);
        copyAndCloseInput(is, fos);
        close(fos);

        // unzip the file in the project dir
        String destination = "unzip";

        unzip(name.getName(), destination);

        // delete the zip file
        name.delete();

        return Results.success("Created new Spring Boot project with dependencies: " + deps + " in directory: " + destination);
    }
}

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
import java.util.Map;
import javax.inject.Inject;

import io.fabric8.forge.devops.AbstractDevOpsCommand;
import io.fabric8.forge.devops.dto.SpringBootDependencyDTO;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.facets.MetadataFacet;
import org.jboss.forge.addon.resource.DirectoryResource;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.input.UISelectMany;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.wizard.UIWizard;
import org.yaml.snakeyaml.Yaml;

import static io.fabric8.forge.devops.springboot.IOHelper.close;
import static io.fabric8.forge.devops.springboot.IOHelper.copyAndCloseInput;
import static io.fabric8.forge.devops.springboot.OkHttpClientHelper.createOkHttpClient;
import static io.fabric8.forge.devops.springboot.UnzipHelper.unzip;
import static io.fabric8.utils.Files.recursiveDelete;

public class SpringBootNewProjectCommand extends AbstractDevOpsCommand implements UIWizard {

    private static final String STARTER_URL = "https://start.spring.io/starter.zip";

    private List<SpringBootDependencyDTO> choices;

    @Inject
    @WithAttributes(label = "Dependencies", required = true, description = "Add Spring Boot Starters and dependencies to your application")
    private UISelectMany<SpringBootDependencyDTO> dependencies;

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        return null;
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        try {
            choices = initDependencies();
        } catch (Exception e) {
            throw new IllegalStateException("Error loading dependencies from spring-boot-application.yaml due: " + e.getMessage(), e);
        }

        dependencies.setValueChoices(choices);
        dependencies.setItemLabelConverter(SpringBootDependencyDTO::getName);
        dependencies.setItemLabelConverter(SpringBootDependencyDTO::getGroupAndName);

        builder.add(dependencies);
    }

    private List<SpringBootDependencyDTO> initDependencies() {
        List<SpringBootDependencyDTO> list = new ArrayList<>();

        Yaml yaml = new Yaml();
        // load the application.yaml file from the spring-boot initializr project and parse it
        // and grab all the dependencies it has so we have those as choices
        InputStream input = SpringBootNewProjectCommand.class.getResourceAsStream("/spring-boot-application.yaml");
        Map data = (Map) yaml.load(input);

        Map initializer = (Map) data.get("initializr");
        List deps = (List) initializer.get("dependencies");
        for (Object dep : deps) {
            Map group = (Map) dep;
            String groupName = (String) group.get("name");
            List content = (List) group.get("content");
            for (Object row : content) {
                Map item = (Map) row;
                String id = (String) item.get("id");
                String name = (String) item.get("name");
                String description = (String) item.get("description");
                list.add(new SpringBootDependencyDTO(groupName, id, name, description));
            }
        }

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
        UIContext uiContext = context.getUIContext();
        Project project = (Project) uiContext.getAttributeMap().get(Project.class);
        MetadataFacet metadataFacet = project.getFacet(MetadataFacet.class);

        String projectName = metadataFacet.getProjectName();
        String groupId = metadataFacet.getProjectGroupName();
        String version = metadataFacet.getProjectVersion();
        File folder = project.getRoot().reify(DirectoryResource.class).getUnderlyingResourceObject();

        int[] selected = dependencies.getSelectedIndexes();
        CollectionStringBuffer csb = new CollectionStringBuffer(",");
        for (int val : selected) {
            SpringBootDependencyDTO dto = choices.get(val);
            csb.append(dto.getId());
        }
        String deps = csb.toString();

        String url = String.format("%s?groupId=%s&artifactId=%s&version=%s&packageName=%s&dependencies=%s", STARTER_URL, groupId, projectName, version, groupId, deps);

        // use http client to call start.spring.io that creates the project
        OkHttpClient client = createOkHttpClient();

        Request request = new Request.Builder()
                .url(url).build();

        Response response = client.newCall(request).execute();
        InputStream is = response.body().byteStream();


        // some archetypes might not use maven or use the maven source layout so lets remove
        // the pom.xml and src folder if its already been pre-created
        // as these will be created if necessary via the archetype jar's contents
        File pom = new File(folder, "pom.xml");
        if (pom.isFile() && pom.exists()) {
            pom.delete();
        }
        File src = new File(folder, "src");
        if (src.isDirectory() && src.exists()) {
            recursiveDelete(src);
        }

        File name = new File(folder, projectName + ".zip");
        if (name.exists()) {
            name.delete();
        }

        FileOutputStream fos = new FileOutputStream(name, false);
        copyAndCloseInput(is, fos);
        close(fos);

        // unzip the download from spring starter
        unzip(name, folder.getName());

        // and delete the zip file
        name.delete();

        return Results.success("Created new Spring Boot project with dependencies: " + deps + " in directory: " + folder.getName());
    }
}

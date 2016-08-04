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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import io.fabric8.forge.devops.AbstractDevOpsCommand;
import io.fabric8.forge.devops.dto.SpringBootDependencyDTO;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jboss.forge.addon.dependencies.builder.DependencyBuilder;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.projects.facets.MetadataFacet;
import org.jboss.forge.addon.resource.DirectoryResource;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.input.UISelectMany;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.wizard.UIWizard;
import org.jboss.forge.furnace.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import static io.fabric8.forge.devops.springboot.IOHelper.close;
import static io.fabric8.forge.devops.springboot.IOHelper.copyAndCloseInput;
import static io.fabric8.forge.devops.springboot.OkHttpClientHelper.createOkHttpClient;
import static io.fabric8.forge.devops.springboot.UnzipHelper.unzip;
import static io.fabric8.utils.Files.recursiveDelete;

public class SpringBootNewProjectCommand extends AbstractDevOpsCommand implements UIWizard {

    private static final transient Logger LOG = LoggerFactory.getLogger(SpringBootNewProjectCommand.class);

    // lets use 1.3.x which currently fabric8 works best with
    private static final String SPRING_BOOT_DEFAULT_VERSION = "1.3.7";
    private static final String[] SPRING_BOOT_VERSIONS = new String[]{"1.3.7", "1.4.0"};

    private static final String STARTER_URL = "https://start.spring.io/starter.zip";

    // fabric8 only dependencies which we should not pass on to start.spring.io
    private static final String[] fabric8Deps = new String[]{"spring-cloud-kubernetes", "kubeflix-ribbon-discovery",
            "kubeflix-turbine-discovery", "kubeflix-turbine-server", "camel-zipkin-starter"};

    @Inject
    @WithAttributes(label = "Spring Boot Version", required = true, description = "Spring Boot Version to use")
    private UISelectOne<String> springBootVersion;

    private List<SpringBootDependencyDTO> choices;

    @Inject
    @WithAttributes(label = "Dependencies", required = true, description = "Add Spring Boot Starters and dependencies to your application")
    private UISelectMany<SpringBootDependencyDTO> dependencies;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        return null;
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        springBootVersion.setValueChoices(Arrays.asList(SPRING_BOOT_VERSIONS));
        springBootVersion.setDefaultValue(SPRING_BOOT_DEFAULT_VERSION);

        try {
            choices = initDependencies();
        } catch (Exception e) {
            throw new IllegalStateException("Error loading dependencies from spring-boot-application.yaml due: " + e.getMessage(), e);
        }

        dependencies.setValueChoices(choices);
        dependencies.setItemLabelConverter(SpringBootDependencyDTO::getGroupAndName);

        builder.add(springBootVersion).add(dependencies);
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

                // are we at apache camel, then inject other Camel modules that are not in the spring-boot-application yet
                if ("camel".equals(id)) {
                    SpringBootDependencyDTO dto = new SpringBootDependencyDTO(groupName, "camel-zipkin-starter", "Apache Camel Zipkin", "Distributed tracing with an existing Zipkin installation with Apache Camel.");
                    String version = SpringBootVersionHelper.getVersion("camel.version");
                    dto.setMavenCoord("org.apache.camel", "camel-zipkin", version);
                    list.add(dto);
                }
            }
        }

        // and then add the fabric8 group
        String version = SpringBootVersionHelper.getVersion("fabric8.spring.cloud.kubernetes.version");
        SpringBootDependencyDTO dto = new SpringBootDependencyDTO("Fabric8", "spring-cloud-kubernetes", "Spring Cloud Kubernetes", "Kubernetes integration with Spring Cloud");
        dto.setMavenCoord("io.fabric8", "spring-cloud-starter-kubernetes-all", version);
        list.add(dto);

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

        Map<String, SpringBootDependencyDTO> selectedDTOs = new HashMap<>();
        int[] selected = dependencies.getSelectedIndexes();
        CollectionStringBuffer csbSpringBoot = new CollectionStringBuffer(",");
        CollectionStringBuffer csbFabric8 = new CollectionStringBuffer(",");
        for (int val : selected) {
            SpringBootDependencyDTO dto = choices.get(val);
            if (isFabric8Dependency(dto.getId())) {
                csbFabric8.append(dto.getId());
            } else {
                csbSpringBoot.append(dto.getId());
            }
            selectedDTOs.put(dto.getId(), dto);
        }
        String springBootDeps = csbSpringBoot.toString();
        String fabric8Deps = csbFabric8.toString();
        // boot version need the RELEASE suffix
        String bootVersion = springBootVersion.getValue() + ".RELEASE";

        String url = String.format("%s?bootVersion=%s&groupId=%s&artifactId=%s&version=%s&packageName=%s&dependencies=%s",
                STARTER_URL, bootVersion, groupId, projectName, version, groupId, springBootDeps);

        LOG.info("About to query url: " + url);

        // use http client to call start.spring.io that creates the project
        OkHttpClient client = createOkHttpClient();

        Request request = new Request.Builder().url(url).build();

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
        unzip(name, folder);

        LOG.info("Unzipped file to folder: {}", folder.getAbsolutePath());

        // and delete the zip file
        name.delete();

        if (!Strings.isEmpty(fabric8Deps)) {
            addFabric8DependenciesToPom(project, fabric8Deps, selectedDTOs);
        }

        // are there any fabric8 dependencies to add afterwards?
        return Results.success("Created new Spring Boot project in directory: " + folder.getName());
    }

    private void addFabric8DependenciesToPom(Project project, String fabric8Deps, Map<String, SpringBootDependencyDTO> selectedDTOs) {
        String[] deps = fabric8Deps.split(",");
        for (String dep : deps) {
            SpringBootDependencyDTO dto = selectedDTOs.get(dep);
            if (dto != null) {
                DependencyBuilder dp = DependencyBuilder.create().setGroupId(dto.getGroupId()).setArtifactId(dto.getArtifactId()).setVersion(dto.getVersion());
                dependencyInstaller.install(project, dp);
            }
        }
    }

    private boolean isFabric8Dependency(String depId) {
        for (String id : fabric8Deps) {
            if (depId.equals(id)) {
                return true;
            }
        }
        return false;
    }

}

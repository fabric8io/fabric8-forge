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
package io.fabric8.forge.devops.setup;

import io.fabric8.forge.addon.utils.CamelProjectHelper;
import io.fabric8.forge.addon.utils.MavenHelpers;
import io.fabric8.utils.Strings;
import org.apache.maven.model.Model;
import org.jboss.forge.addon.dependencies.Coordinate;
import org.jboss.forge.addon.dependencies.builder.CoordinateBuilder;
import org.jboss.forge.addon.maven.plugins.Configuration;
import org.jboss.forge.addon.maven.plugins.ConfigurationBuilder;
import org.jboss.forge.addon.maven.plugins.ConfigurationElement;
import org.jboss.forge.addon.maven.plugins.ConfigurationElementBuilder;
import org.jboss.forge.addon.maven.plugins.MavenPlugin;
import org.jboss.forge.addon.maven.plugins.MavenPluginBuilder;
import org.jboss.forge.addon.maven.projects.MavenFacet;
import org.jboss.forge.addon.maven.projects.MavenPluginFacet;
import org.jboss.forge.addon.projects.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class DockerSetupHelper {
    private static final transient Logger LOG = LoggerFactory.getLogger(DockerSetupHelper.class);

    public static final String DEFAULT_KARAF_IMAGE = "fabric8/karaf-2.4";
    public static final String DEFAULT_TOMCAT_IMAGE = "fabric8/tomcat-8";
    public static final String DEFAULT_WILDFLY_IMAGE = "jboss/wildfly:9.0.2.Final";
    public static final String S2I_JAVA_IMAGE = "fabric8/s2i-java:1.2.9";

    // see https://github.com/fabric8io/fabric8/issues/4160
    private static String dockerFromImagePrefix = "docker.io/";

    private static String[] jarImages = new String[]{}; // s2i is not yet supported
    private static String[] bundleImages = new String[]{DEFAULT_KARAF_IMAGE};
    private static String[] warImages = new String[]{DEFAULT_TOMCAT_IMAGE, DEFAULT_WILDFLY_IMAGE};

    /**
     * Returns true if it seems like the docker maven plugin is configured
     */
    public static boolean verifyDocker(Project project) {
        return true;
    }

    public static void setupDocker(Project project, String fromImage, String main) {
        MavenFacet maven = project.getFacet(MavenFacet.class);
        Model pom = maven.getModel();

        boolean springBoot = hasSpringBoot(project);
        boolean wildflySwarm = hasWildlySwarm(project);
        String packaging = getProjectPackaging(project);
        boolean war = packaging != null && packaging.equals("war");
        boolean bundle = packaging != null && packaging.equals("bundle");
        boolean jar = packaging == null || packaging.equals("jar"); // jar is default packaging

        Map<String, String> envs = new LinkedHashMap<>();
        if (springBoot) {
            envs.put("JAVA_APP_JAR", "${project.artifactId}-${project.version}.jar");
            envs.put("JAVA_OPTIONS", "-Djava.security.egd=/dev/./urandom");
        } else if (wildflySwarm) {
            envs.put("JAVA_APP_JAR", "${project.build.finalName}-swarm.jar");
        } else if (war) {
            envs.put("CATALINA_OPTS", "-javaagent:/opt/tomcat/jolokia-agent.jar=host=0.0.0.0,port=8778");
        } else if (jar && main != null) {
            // only include main for JAR deployment as WAR/bundle is container based
            envs.put("JAVA_MAIN_CLASS", main);
        }

        MavenPlugin plugin = MavenHelpers.findPlugin(project, "io.fabric8", "fabric8-maven-plugin");
        if (plugin != null) {
            MavenPluginBuilder pluginBuilder = MavenPluginBuilder.create(plugin);
            //Configuration config = plugin.getConfig();
            Configuration config = ConfigurationBuilder.create(pluginBuilder);
            boolean updated = false;
            if (Strings.isNotBlank(main)) {
                setDockerJavaMain(config, main);
                updated = true;
            }
            if (Strings.isNotBlank(fromImage)) {
                setDockerFromImage(config, fromImage);
                updated = true;
            }
            if (updated) {
                pluginBuilder.setConfiguration(config);
                MavenPluginFacet pluginFacet = project.getFacet(MavenPluginFacet.class);
                pluginFacet.updatePlugin(pluginBuilder);
            }

        } else {
            LOG.warn("No fabric8 maven plugin found!!");
        }
    }

    protected static void setDockerJavaMain(Configuration config, String mainClass) {
        ConfigurationElement customizer = MavenHelpers.getOrCreateElement(config, "customizer");
        ConfigurationElementBuilder javaAppMainClass = MavenHelpers.getOrCreateElementBuilder(customizer, "java.app.mainClass");
        javaAppMainClass.setText(mainClass);
    }


    protected static void setDockerFromImage(Configuration config, String fromImage) {
        ConfigurationElement images = MavenHelpers.getOrCreateElement(config, "images");
        // images/image
        ConfigurationElement image = MavenHelpers.getOrCreateElement(images, "image");
        // images/image/name
        ConfigurationElementBuilder name = MavenHelpers.getOrCreateElementBuilder(image, "name");
        if (Strings.isNullOrBlank(name.getText())) {
            name.setText("${project.artifactId}:${project.version}");
        }
        // images/image/build
        ConfigurationElement build = MavenHelpers.getOrCreateElement(image, "build");
        // images/image/build/from
        ConfigurationElementBuilder from = MavenHelpers.getOrCreateElementBuilder(build, "from");
        if (Strings.isNullOrBlank(from.getText())) {
            from.setText(fromImage);
        }

        // images/image/build/assembly
/*        ConfigurationElementBuilder assembly = MavenHelpers.getOrCreateElementBuilder(build, "assembly");
        if (springBoot || wildflySwarm || jar) {
            if (!assembly.hasChildByName("basedir")) {
                ConfigurationElementBuilder baseDir = MavenHelpers.getOrCreateElementBuilder(assembly, "basedir");
                baseDir.setText("/app");
            }
            if (wildflySwarm) {
                ConfigurationElementBuilder inline = MavenHelpers.getOrCreateElementBuilder(assembly, "inline");
                ConfigurationElementBuilder fileSets = MavenHelpers.getOrCreateElementBuilder(inline, "fileSets");
                ConfigurationElementBuilder fileSet = MavenHelpers.getOrCreateElementBuilder(fileSets, "fileSet");
                ConfigurationElementBuilder includes = MavenHelpers.getOrCreateElementBuilder(fileSet, "includes");
                ConfigurationElementBuilder include = MavenHelpers.getOrCreateElementBuilder(includes, "include");
                include.setText("${project.build.finalName}-swarm.jar");

                ConfigurationElementBuilder directory = MavenHelpers.getOrCreateElementBuilder(fileSet, "directory");
                directory.setText("${project.build.directory}");

                ConfigurationElementBuilder outputDirectory = MavenHelpers.getOrCreateElementBuilder(fileSet, "outputDirectory");
                outputDirectory.setText("/");
            }
        }
        if (!wildflySwarm) {
            if (!assembly.hasChildByName("descriptor")) {
                ConfigurationElementBuilder descriptorRef = MavenHelpers.getOrCreateElementBuilder(assembly, "descriptorRef");
                if (Strings.isNullOrBlank(descriptorRef.getText())) {
                    descriptorRef.setText("${docker.assemblyDescriptorRef}");
                }
            }
        }
        // images/image/build/env
        ConfigurationElement env = MavenHelpers.getOrCreateElement(build, "env");
        for (Map.Entry<String, String> entry : envs.entrySet()) {
            ConfigurationElement cfg = ConfigurationElementBuilder.create().setName(entry.getKey()).setText(entry.getValue());
            env.getChildren().add(cfg);
        }
        // images/image/build/cmd
        if (Strings.isNotBlank(commandShell)) {
            ConfigurationElementBuilder shell = MavenHelpers.getOrCreateElementBuilder(build, "cmd", "shell");
            if (Strings.isNullOrBlank(shell.getText())) {
                MavenHelpers.asConfigurationElementBuilder(shell).setText(commandShell);
            }
        }*/
    }

    public static String getDockerFromImage(Project project) {
        if (project != null) {
            // update properties section in pom.xml
            MavenFacet maven = project.getFacet(MavenFacet.class);
            if (maven != null) {
                Model pom = maven.getModel();
                if (pom != null) {
                    Properties properties = pom.getProperties();
                    return properties.getProperty("docker.from");
                }
            }
        }
        return null;
    }

    public static boolean hasSpringBoot(Project project) {
        return CamelProjectHelper.hasDependency(project, "org.springframework.boot");
    }

    public static boolean hasSpringBootWeb(Project project) {
        return CamelProjectHelper.hasDependency(project, "org.springframework.boot", "spring-boot-starter-web");
    }

    public static boolean hasWildlySwarm(Project project) {
        return CamelProjectHelper.hasDependency(project, "org.wildfly.swarm");
    }


    public static boolean hasVertx(Project project) {
        return CamelProjectHelper.hasDependency(project, "io.vertx");
    }

    public static boolean hasFunktion(Project project) {
        return CamelProjectHelper.hasDependency(project, "io.fabric8.funktion");
    }

    public static String defaultDockerImage(Project project) {
        String packaging = getProjectPackaging(project);
        if ("jar".equals(packaging)) {
            return jarImages[0];
        } else if ("bundle".equals(packaging)) {
            return bundleImages[0];
        } else if ("war".equals(packaging)) {
            // we have both tomcat or jboss
            return null;
        }
        return null;
    }

    private static String getProjectPackaging(Project project) {
        if (project != null) {
            MavenFacet maven = project.getFacet(MavenFacet.class);
            return maven.getModel().getPackaging();
        }
        return null;
    }

    public static boolean isJarImage(String fromImage) {
        // is required for jar images
        for (String jar : jarImages) {
            if (jar.equals(fromImage)) {
                return true;
            }
        }
        return false;
    }

    private static Coordinate createCoordinate(String groupId, String artifactId, String version) {
        CoordinateBuilder builder = CoordinateBuilder.create()
                .setGroupId(groupId)
                .setArtifactId(artifactId);
        if (version != null) {
            builder = builder.setVersion(version);
        }

        return builder;
    }

    /**
     * Tries to guess a good default main class to use based on the project.
     *
     * @return the suggested main class, or <tt>null</tt> if not possible to guess/find a good candidate
     */
    public static String defaultMainClass(Project project) {
        // try to guess a default main class
        MavenFacet maven = project.getFacet(MavenFacet.class);
        if (maven != null) {
            String answer = null;
            MavenPlugin plugin = MavenHelpers.findPlugin(project, "io.fabric8", "docker-maven-plugin");
            if (plugin != null) {
                Configuration config = plugin.getConfig();
                ConfigurationElement element = MavenHelpers.getConfigurationElement(config, "images", "image", "build", "env", "JAVA_MAIN_CLASS");
                if (element != null) {
                    answer = element.getText();
                }
            }

            if (Strings.isNullOrBlank(answer)) {
                Model pom = maven.getModel();
                if (pom != null) {
                    Properties properties = pom.getProperties();
                    answer = properties.getProperty("docker.env.MAIN");
                    if (Strings.isNullOrBlank(answer)) {
                        answer = properties.getProperty("fabric8.env.MAIN");
                    }
                }
            }
            if (Strings.isNotBlank(answer)) {
                return answer;
            }
        }

        // if camel-spring is on classpath
        if (CamelProjectHelper.findCamelCDIDependency(project) != null) {
            return "org.apache.camel.cdi.Main";
        } else if (CamelProjectHelper.findCamelSpringDependency(project) != null) {
            return "org.apache.camel.spring.Main";
        } else if (CamelProjectHelper.findCamelBlueprintDependency(project) != null) {
            return "org.apache.camel.test.blueprint.Main";
        }

        // TODO: what about spring-boot / docker-swarm??
        return null;
    }

}

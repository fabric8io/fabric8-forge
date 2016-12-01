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
package io.fabric8.forge.devops;

import java.io.File;
import java.io.FileInputStream;

import io.fabric8.forge.addon.utils.StopWatch;
import io.fabric8.forge.devops.setup.Fabric8SetupStep;
import io.fabric8.forge.devops.springboot.IOHelper;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.letschat.LetsChatClient;
import io.fabric8.letschat.LetsChatKubernetes;
import io.fabric8.taiga.TaigaClient;
import io.fabric8.taiga.TaigaKubernetes;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.result.navigation.NavigationResultBuilder;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.wizard.UIWizard;

public class DevOpsEditCommand extends AbstractDevOpsCommand implements UIWizard {

    private String namespace = KubernetesHelper.defaultNamespace();

    private volatile boolean needFabric8Setup = true;
    private volatile boolean needOptionalStep = false;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(getClass())
                .category(Categories.create(AbstractDevOpsCommand.CATEGORY))
                .name(AbstractDevOpsCommand.CATEGORY + ": Edit")
                .description("Edit the DevOps configuration for this project");
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        StopWatch watch = new StopWatch();

        // load maven pom.xml
        File rootFolder = getSelectionFolder(builder.getUIContext());
        if (rootFolder != null) {
            File pom = new File(rootFolder, "pom.xml");
            if (!pom.exists()) {
                // not a maven project so no need for fabric8
                needFabric8Setup = false;
            } else {
                String text = IOHelper.loadText(new FileInputStream(pom));
                if (text.contains("fabric8-profiles")) {
                    // no need for fabric8 for fuse projects
                    needFabric8Setup = false;
                } else if (text.contains("io.fabric8.funktion")) {
                    // no need for fabric8 for funktion projects
                    needFabric8Setup = false;
                } else if (text.contains("fabric8-maven-plugin")) {
                    // no need for fabric8 if we have f-m-p plugin
                    needFabric8Setup = false;
                }
            }
        }

        // need optional step for chat/issue tracker
        LetsChatClient letsChatClient = LetsChatKubernetes.createLetsChat(getKubernetes());
        if (letsChatClient != null) {
            needOptionalStep = true;
            builder.getUIContext().getAttributeMap().put("letsChatClient", letsChatClient);
        }
        TaigaClient taigaClient = TaigaKubernetes.createTaiga(getKubernetes(), namespace);
        if (taigaClient != null) {
            needOptionalStep = true;
            builder.getUIContext().getAttributeMap().put("taigaClient", taigaClient);
        }

        log.info("Need fabric8 setup? " + needFabric8Setup);
        log.info("Need optional setup? " + needOptionalStep);

        log.info("initializeUI took " + watch.taken());
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        NavigationResultBuilder builder = NavigationResultBuilder.create();
        if (needFabric8Setup) {
            builder.add(Fabric8SetupStep.class);
        }
        if (needOptionalStep) {
            builder.add(DevOpsEditOptionalStep.class);
        }
        builder.add(DevOpsEditStep.class);
        return builder.build();
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        return Results.success();
    }
}

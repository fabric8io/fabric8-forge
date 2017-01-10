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
package io.fabric8.forge.rest.git;

import io.fabric8.forge.rest.main.ProjectFileSystem;
import io.fabric8.utils.Files;

import java.io.File;

/**
 */
public class GitCloneWithTagExample {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Arguments: gitUrl gitTag");
            return;
        }

        String cloneUrl = args[0];
        String tag = args[1];

        File projectFolder = new File("target/myproject");
        if (projectFolder.exists()) {
            Files.recursiveDelete(projectFolder);
        }
        String remote = "origin";

        ProjectFileSystem.cloneRepo(projectFolder, cloneUrl, null, null, null, remote, tag);
    }
}

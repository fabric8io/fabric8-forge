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
package io.fabric8.forge.rest.main;

import io.fabric8.forge.rest.Constants;
import io.fabric8.forge.rest.utils.StopWatch;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.project.support.UserDetails;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.eclipse.jgit.util.Base64;
import org.jboss.forge.furnace.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.Charset;

import static io.fabric8.kubernetes.api.ServiceNames.*;

/**
 * A helper class for working with git user stuff
 */
public class GitUserHelper {
    private static final transient Logger LOG = LoggerFactory.getLogger(GitUserHelper.class);
    private final String gitUser;
    private final String gitPassword;
    private final KubernetesClient kubernetesClient;
    private String address;
    private String internalAddress;

    // TODO it'd be nice to pick either http or https based on the port number of the gogs service
    // so if folks configured it on https then we'd just work
    @Inject
    public GitUserHelper(@ConfigProperty(name = "JENKINS_GOGS_USER") String gitUser,
                         @ConfigProperty(name = "JENKINS_GOGS_PASSWORD") String gitPassword,
                         KubernetesClient kubernetesClient) {
        this.gitUser = gitUser;
        this.gitPassword = gitPassword;
        this.kubernetesClient = kubernetesClient;
    }

    public UserDetails createUserDetails(HttpServletRequest request) {
        StopWatch watch = new StopWatch();

        String user = gitUser;
        String password = gitPassword;
        String authorization = null;
        String emailHeader = null;

        // lets try custom headers or request parameters
        if (request != null) {
            authorization = request.getHeader("GogsAuthorization");
            if (Strings.isNullOrEmpty(authorization)) {
                authorization = request.getParameter(Constants.RequestParameters.GOGS_AUTH);
            }
            emailHeader = request.getHeader("GogsEmail");
            if (Strings.isNullOrEmpty(emailHeader)) {
                emailHeader = request.getParameter(Constants.RequestParameters.GOGS_EMAIL);
            }
        }
        if (!Strings.isNullOrEmpty(authorization)) {
            String basicPrefix = "basic";
            String lower = authorization.toLowerCase();
            if (lower.startsWith(basicPrefix)) {
                String base64Credentials = authorization.substring(basicPrefix.length()).trim();
                String credentials = new String(Base64.decode(base64Credentials),
                        Charset.forName("UTF-8"));
                // credentials = username:password
                String[] values = credentials.split(":", 2);
                if (values != null && values.length > 1) {
                    user = values[0];
                    password = values[1];
                }
            }
        }
        String email = "dummy@gmail.com";
        if (!Strings.isNullOrEmpty(emailHeader)) {
            email = emailHeader;
        }
        if (Strings.isNullOrEmpty(address)) {
            address = getGogsURL(true);
            internalAddress = getGogsURL(false);
            if (!address.endsWith("/")) {
                address += "/";
            }
            if (!internalAddress.endsWith("/")) {
                internalAddress += "/";
            }
        }

        LOG.info("createUserDetails took " + watch.taken());
        return new UserDetails(address, internalAddress, user, password, email);
    }

    protected String getGogsURL(boolean external) {
        StopWatch watch = new StopWatch();

        String namespace = kubernetesClient.getNamespace();
        if (Strings.isNullOrEmpty(namespace)) {
            namespace = KubernetesHelper.defaultNamespace();
        }
        String answer = KubernetesHelper.getServiceURL(kubernetesClient, GOGS, namespace, "http", external);
        if (Strings.isNullOrEmpty(answer)) {
            String kind = external ? "external" : "internal";
            throw new IllegalStateException("Could not find external URL for " + kind + " service: gogs!");
        }

        LOG.info("getGogsURL took " + watch.taken());
        return answer;
    }
}

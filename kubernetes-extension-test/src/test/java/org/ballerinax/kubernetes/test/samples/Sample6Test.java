/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinax.kubernetes.test.samples;

import io.fabric8.docker.api.model.ImageInspect;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.ballerinax.kubernetes.KubernetesConstants;
import org.ballerinax.kubernetes.exceptions.KubernetesPluginException;
import org.ballerinax.kubernetes.test.utils.KubernetesTestUtils;
import org.ballerinax.kubernetes.utils.KubernetesUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import static org.ballerinax.kubernetes.KubernetesConstants.DOCKER;
import static org.ballerinax.kubernetes.KubernetesConstants.KUBERNETES;
import static org.ballerinax.kubernetes.test.utils.KubernetesTestUtils.getDockerImage;

public class Sample6Test implements SampleTest {

    private final String sourceDirPath = SAMPLE_DIR + File.separator + "sample6";
    private final String targetPath = sourceDirPath + File.separator + KUBERNETES;
    private final String dockerImage = "hello_world_secret_mount_k8s:latest";

    @BeforeClass
    public void compileSample() throws IOException, InterruptedException {
        Assert.assertEquals(KubernetesTestUtils.compileBallerinaFile(sourceDirPath, "hello_world_secret_mount_k8s" +
                ".bal"), 0);
    }

    @Test
    public void validateDockerfile() {
        File dockerFile = new File(targetPath + File.separator + DOCKER + File.separator + "Dockerfile");
        Assert.assertTrue(dockerFile.exists());
    }

    @Test
    public void validateDockerImage() {
        ImageInspect imageInspect = getDockerImage(dockerImage);
        Assert.assertEquals(1, imageInspect.getContainerConfig().getExposedPorts().size());
        Assert.assertTrue(imageInspect.getContainerConfig().getExposedPorts().keySet().contains("9090/tcp"));
    }

    @Test
    public void validateDeployment() throws IOException {
        File deploymentYAML = new File(targetPath + File.separator + "hello_world_secret_mount_k8s_deployment.yaml");
        Assert.assertTrue(deploymentYAML.exists());
        Deployment deployment = KubernetesHelper.loadYaml(deploymentYAML);
        // Assert Deployment
        Assert.assertEquals("hello-world-secret-mount-k8s-deployment", deployment.getMetadata().getName());
        Assert.assertEquals(1, deployment.getSpec().getReplicas().intValue());
        Assert.assertEquals(3, deployment.getSpec().getTemplate().getSpec().getVolumes().size());
        Assert.assertEquals("hello_world_secret_mount_k8s", deployment.getMetadata().getLabels().get(KubernetesConstants
                .KUBERNETES_SELECTOR_KEY));
        Assert.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());

        // Assert Containers
        Container container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        Assert.assertEquals(3, container.getVolumeMounts().size());
        Assert.assertEquals(dockerImage, container.getImage());
        Assert.assertEquals(KubernetesConstants.ImagePullPolicy.IfNotPresent.name(), container.getImagePullPolicy());
        Assert.assertEquals(1, container.getPorts().size());
    }

    @Test
    public void validateSecret() throws IOException {
        File secretYAML = new File(targetPath + File.separator + "hello_world_secret_mount_k8s_secret.yaml");
        Assert.assertTrue(secretYAML.exists());
        KubernetesClient client = new DefaultKubernetesClient();
        List<HasMetadata> k8sItems = client.load(new FileInputStream(secretYAML)).get();
        Assert.assertEquals(3, k8sItems.size());
        Secret sslSecret = null;
        Secret privateSecret = null;
        Secret publicSecret = null;
        for (HasMetadata data : k8sItems) {
            switch (data.getMetadata().getName()) {
                case "helloworldep-secure-socket":
                    sslSecret = (Secret) data;
                    break;
                case "private":
                    privateSecret = (Secret) data;
                    break;
                case "public":
                    publicSecret = (Secret) data;
                    break;
                default:
                    break;
            }
        }
        // Assert SSL CERTs
        Assert.assertNotNull(sslSecret);
        Assert.assertEquals(2, sslSecret.getData().size());

        // Assert private secrets
        Assert.assertNotNull(privateSecret);
        Assert.assertEquals(1, privateSecret.getData().size());

        // Assert public secrets
        Assert.assertNotNull(publicSecret);
        Assert.assertEquals(2, publicSecret.getData().size());
    }

    @AfterClass
    public void cleanUp() throws KubernetesPluginException {
        KubernetesUtils.deleteDirectory(targetPath);
        KubernetesTestUtils.deleteDockerImage(dockerImage);
    }

}

package io.jenkins.plugins.quay;

import static org.junit.Assert.*;

import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Integration tests for QuayImageParameterDefinition.
 * Uses JenkinsRule for a real Jenkins instance.
 */
public class QuayImageParameterDefinitionTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testParameterDefinitionCreation() throws Exception {
        QuayImageParameterDefinition param =
                new QuayImageParameterDefinition("QUAY_IMAGE", "Select an image", "coreos", "etcd");

        assertEquals("QUAY_IMAGE", param.getName());
        assertEquals("Select an image", param.getDescription());
        assertEquals("coreos", param.getOrganization());
        assertEquals("etcd", param.getRepository());
    }

    @Test
    public void testDefaultParameterValue() throws Exception {
        QuayImageParameterDefinition param =
                new QuayImageParameterDefinition("QUAY_IMAGE", "Select an image", "coreos", "etcd");
        param.setDefaultTag("v3.5.0");

        QuayImageParameterValue value = (QuayImageParameterValue) param.getDefaultParameterValue();

        assertEquals("QUAY_IMAGE", value.getName());
        assertEquals("coreos", value.getOrganization());
        assertEquals("etcd", value.getRepository());
        assertEquals("v3.5.0", value.getTag());
        assertEquals("quay.io/coreos/etcd:v3.5.0", value.getImageReference());
    }

    @Test
    public void testParameterValueEnvironmentVariables() throws Exception {
        QuayImageParameterValue value = new QuayImageParameterValue("MY_IMAGE", "myorg", "myrepo", "v1.0.0");

        assertEquals("quay.io/myorg/myrepo:v1.0.0", value.getValue());
        assertEquals("MY_IMAGE=quay.io/myorg/myrepo:v1.0.0", value.getShortDescription());
    }

    @Test
    public void testAddParameterToJob() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("test-job");

        QuayImageParameterDefinition param =
                new QuayImageParameterDefinition("DEPLOY_IMAGE", "Image to deploy", "mycompany", "myapp");
        param.setTagLimit(10);
        param.setDefaultTag("latest");

        project.addProperty(new ParametersDefinitionProperty(param));

        // Verify parameter was added
        ParametersDefinitionProperty props = project.getProperty(ParametersDefinitionProperty.class);
        assertNotNull(props);
        assertEquals(1, props.getParameterDefinitions().size());

        QuayImageParameterDefinition retrieved =
                (QuayImageParameterDefinition) props.getParameterDefinition("DEPLOY_IMAGE");
        assertNotNull(retrieved);
        assertEquals("mycompany", retrieved.getOrganization());
        assertEquals("myapp", retrieved.getRepository());
        assertEquals(10, retrieved.getTagLimit());
    }

    @Test
    public void testDescriptorDisplayName() {
        QuayImageParameterDefinition.DescriptorImpl descriptor = new QuayImageParameterDefinition.DescriptorImpl();
        assertEquals("Quay.io Image Parameter", descriptor.getDisplayName());
    }
}

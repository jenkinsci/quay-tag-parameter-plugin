package io.jenkins.plugins.quay;

import static org.junit.Assert.*;

import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Comprehensive regression tests for QuayImageParameterDefinition.
 * Covers parameter creation, custom endpoint propagation, default values,
 * createValue with JSON, job configuration persistence, and descriptor validation.
 */
public class QuayImageParameterDefinitionRegressionTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    // =====================================================================
    // Basic Parameter Creation
    // =====================================================================

    @Test
    public void testParameterCreation() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "Test", "myorg", "myrepo");
        assertEquals("IMG", param.getName());
        assertEquals("Test", param.getDescription());
        assertEquals("myorg", param.getOrganization());
        assertEquals("myrepo", param.getRepository());
    }

    @Test
    public void testDefaultTagLimit() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");
        assertEquals(20, param.getTagLimit());
    }

    @Test
    public void testSetTagLimit() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");
        param.setTagLimit(50);
        assertEquals(50, param.getTagLimit());
    }

    @Test
    public void testInvalidTagLimitResetsToDefault() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");
        param.setTagLimit(0);
        assertEquals(20, param.getTagLimit());

        param.setTagLimit(-10);
        assertEquals(20, param.getTagLimit());
    }

    @Test
    public void testSetCredentialsId() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");
        param.setCredentialsId("my-creds");
        assertEquals("my-creds", param.getCredentialsId());
    }

    @Test
    public void testSetDefaultTag() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");
        param.setDefaultTag("v1.0.0");
        assertEquals("v1.0.0", param.getDefaultTag());
    }

    // =====================================================================
    // Custom Endpoint Tests
    // =====================================================================

    @Test
    public void testDefaultEndpointIsQuayIo() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");
        assertEquals("quay.io", param.getQuayEndpoint());
    }

    @Test
    public void testSetCustomEndpoint() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");
        param.setQuayEndpoint("quay.mycompany.com");
        assertEquals("quay.mycompany.com", param.getQuayEndpoint());
    }

    @Test
    public void testSetNullEndpointDefaultsToQuayIo() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");
        param.setQuayEndpoint(null);
        assertEquals("quay.io", param.getQuayEndpoint());
    }

    @Test
    public void testSetEmptyEndpointDefaultsToQuayIo() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");
        param.setQuayEndpoint("");
        assertEquals("quay.io", param.getQuayEndpoint());
    }

    // =====================================================================
    // Default Parameter Value Tests
    // =====================================================================

    @Test
    public void testDefaultParameterValueWithDefaultTag() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");
        param.setDefaultTag("v3.5.0");

        QuayImageParameterValue value = (QuayImageParameterValue) param.getDefaultParameterValue();
        assertEquals("IMG", value.getName());
        assertEquals("org", value.getOrganization());
        assertEquals("repo", value.getRepository());
        assertEquals("v3.5.0", value.getTag());
        assertEquals("quay.io", value.getQuayEndpoint());
    }

    @Test
    public void testDefaultParameterValueFallsBackToLatest() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");
        // No default tag set

        QuayImageParameterValue value = (QuayImageParameterValue) param.getDefaultParameterValue();
        assertEquals("latest", value.getTag());
    }

    @Test
    public void testDefaultParameterValueEmptyDefaultTagFallsBackToLatest() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");
        param.setDefaultTag("   ");

        QuayImageParameterValue value = (QuayImageParameterValue) param.getDefaultParameterValue();
        assertEquals("latest", value.getTag());
    }

    @Test
    public void testDefaultParameterValuePropagatesCustomEndpoint() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");
        param.setQuayEndpoint("quay.company.com");
        param.setDefaultTag("v1.0");

        QuayImageParameterValue value = (QuayImageParameterValue) param.getDefaultParameterValue();
        assertEquals("quay.company.com", value.getQuayEndpoint());
        assertEquals("quay.company.com/org/repo:v1.0", value.getImageReference());
    }

    // =====================================================================
    // createValue with JSON Tests
    // =====================================================================

    @Test
    public void testCreateValueFromJsonWithValueKey() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");

        JSONObject json = new JSONObject();
        json.put("value", "v2.0.0");

        ParameterValue result = param.createValue((StaplerRequest2) null, json);
        assertNotNull(result);
        assertTrue(result instanceof QuayImageParameterValue);

        QuayImageParameterValue qv = (QuayImageParameterValue) result;
        assertEquals("v2.0.0", qv.getTag());
        assertEquals("org", qv.getOrganization());
        assertEquals("repo", qv.getRepository());
    }

    @Test
    public void testCreateValueFromJsonWithTagKey() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");

        JSONObject json = new JSONObject();
        json.put("tag", "v3.0.0");

        ParameterValue result = param.createValue((StaplerRequest2) null, json);
        QuayImageParameterValue qv = (QuayImageParameterValue) result;
        assertEquals("v3.0.0", qv.getTag());
    }

    @Test
    public void testCreateValueFromJsonWithParamNameKey() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("MY_IMAGE", "", "org", "repo");

        JSONObject json = new JSONObject();
        json.put("MY_IMAGE", "v4.0.0");

        ParameterValue result = param.createValue((StaplerRequest2) null, json);
        QuayImageParameterValue qv = (QuayImageParameterValue) result;
        assertEquals("v4.0.0", qv.getTag());
    }

    @Test
    public void testCreateValueFromJsonEmptyValueFallsBackToDefault() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");
        param.setDefaultTag("v1.0-default");

        JSONObject json = new JSONObject();
        json.put("value", "");

        ParameterValue result = param.createValue((StaplerRequest2) null, json);
        QuayImageParameterValue qv = (QuayImageParameterValue) result;
        assertEquals("v1.0-default", qv.getTag());
    }

    @Test
    public void testCreateValueFromJsonNoKeysFallsBackToLatest() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");

        JSONObject json = new JSONObject();

        ParameterValue result = param.createValue((StaplerRequest2) null, json);
        QuayImageParameterValue qv = (QuayImageParameterValue) result;
        assertEquals("latest", qv.getTag());
    }

    @Test
    public void testCreateValueFromJsonPropagatesCustomEndpoint() {
        QuayImageParameterDefinition param = new QuayImageParameterDefinition("IMG", "", "org", "repo");
        param.setQuayEndpoint("registry.internal.com");

        JSONObject json = new JSONObject();
        json.put("value", "v5.0");

        ParameterValue result = param.createValue((StaplerRequest2) null, json);
        QuayImageParameterValue qv = (QuayImageParameterValue) result;
        assertEquals("registry.internal.com", qv.getQuayEndpoint());
        assertEquals("registry.internal.com/org/repo:v5.0", qv.getImageReference());
    }

    // =====================================================================
    // Job Configuration Round-Trip Tests
    // =====================================================================

    @Test
    public void testAddParameterToJobAndRetrieve() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("regression-test");

        QuayImageParameterDefinition param =
                new QuayImageParameterDefinition("DEPLOY_IMG", "Deploy image", "company", "app");
        param.setTagLimit(30);
        param.setDefaultTag("stable");
        param.setQuayEndpoint("quay.company.com");
        param.setCredentialsId("quay-creds");

        project.addProperty(new ParametersDefinitionProperty(param));

        ParametersDefinitionProperty props = project.getProperty(ParametersDefinitionProperty.class);
        assertNotNull(props);

        QuayImageParameterDefinition retrieved =
                (QuayImageParameterDefinition) props.getParameterDefinition("DEPLOY_IMG");
        assertNotNull(retrieved);
        assertEquals("company", retrieved.getOrganization());
        assertEquals("app", retrieved.getRepository());
        assertEquals(30, retrieved.getTagLimit());
        assertEquals("stable", retrieved.getDefaultTag());
        assertEquals("quay.company.com", retrieved.getQuayEndpoint());
        assertEquals("quay-creds", retrieved.getCredentialsId());
    }

    @Test
    public void testMultipleParametersOnSameJob() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("multi-param-test");

        QuayImageParameterDefinition param1 = new QuayImageParameterDefinition("APP_IMG", "App", "org1", "app");
        param1.setQuayEndpoint("quay.io");

        QuayImageParameterDefinition param2 =
                new QuayImageParameterDefinition("SIDECAR_IMG", "Sidecar", "org2", "sidecar");
        param2.setQuayEndpoint("quay.company.com");

        project.addProperty(new ParametersDefinitionProperty(param1, param2));

        ParametersDefinitionProperty props = project.getProperty(ParametersDefinitionProperty.class);
        assertEquals(2, props.getParameterDefinitions().size());

        QuayImageParameterDefinition r1 = (QuayImageParameterDefinition) props.getParameterDefinition("APP_IMG");
        QuayImageParameterDefinition r2 = (QuayImageParameterDefinition) props.getParameterDefinition("SIDECAR_IMG");

        assertEquals("quay.io", r1.getQuayEndpoint());
        assertEquals("quay.company.com", r2.getQuayEndpoint());
    }

    // =====================================================================
    // Descriptor Tests
    // =====================================================================

    @Test
    public void testDescriptorDisplayName() {
        QuayImageParameterDefinition.DescriptorImpl descriptor = new QuayImageParameterDefinition.DescriptorImpl();
        assertEquals("Quay.io Image Parameter", descriptor.getDisplayName());
    }

    @Test
    public void testDescriptorIsParameterDescriptor() {
        QuayImageParameterDefinition.DescriptorImpl descriptor = new QuayImageParameterDefinition.DescriptorImpl();
        assertTrue(descriptor instanceof ParameterDefinition.ParameterDescriptor);
    }
}

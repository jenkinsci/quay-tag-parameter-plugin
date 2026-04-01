package io.jenkins.plugins.quay;

import static org.junit.Assert.*;

import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.queue.QueueTaskFuture;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Comprehensive regression tests for QuayImageParameterValue.
 * Covers environment variables, variable resolvers, custom endpoints,
 * backward compatibility, equality, serialization, and integration with Jenkins builds.
 */
public class QuayImageParameterValueRegressionTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    // =====================================================================
    // Constructor Tests
    // =====================================================================

    @Test
    public void testFourArgConstructorDefaultsEndpoint() {
        QuayImageParameterValue value = new QuayImageParameterValue("IMG", "org", "repo", "v1.0");
        assertEquals("quay.io", value.getQuayEndpoint());
    }

    @Test
    public void testFiveArgConstructorCustomEndpoint() {
        QuayImageParameterValue value = new QuayImageParameterValue("IMG", "org", "repo", "v1.0", "quay.mycompany.com");
        assertEquals("quay.mycompany.com", value.getQuayEndpoint());
    }

    @Test
    public void testFiveArgConstructorNullEndpointDefaultsToQuayIo() {
        QuayImageParameterValue value = new QuayImageParameterValue("IMG", "org", "repo", "v1.0", null);
        assertEquals("quay.io", value.getQuayEndpoint());
    }

    @Test
    public void testFiveArgConstructorEmptyEndpointDefaultsToQuayIo() {
        QuayImageParameterValue value = new QuayImageParameterValue("IMG", "org", "repo", "v1.0", "");
        assertEquals("quay.io", value.getQuayEndpoint());
    }

    @Test
    public void testFiveArgConstructorWhitespaceEndpointDefaultsToQuayIo() {
        QuayImageParameterValue value = new QuayImageParameterValue("IMG", "org", "repo", "v1.0", "   ");
        assertEquals("quay.io", value.getQuayEndpoint());
    }

    // =====================================================================
    // Getters and Image Reference Tests
    // =====================================================================

    @Test
    public void testGetters() {
        QuayImageParameterValue value = new QuayImageParameterValue("IMG", "myorg", "myrepo", "v2.0");
        assertEquals("IMG", value.getName());
        assertEquals("myorg", value.getOrganization());
        assertEquals("myrepo", value.getRepository());
        assertEquals("v2.0", value.getTag());
    }

    @Test
    public void testImageReferenceDefaultEndpoint() {
        QuayImageParameterValue value = new QuayImageParameterValue("IMG", "coreos", "etcd", "v3.5.0");
        assertEquals("quay.io/coreos/etcd:v3.5.0", value.getImageReference());
    }

    @Test
    public void testImageReferenceCustomEndpoint() {
        QuayImageParameterValue value =
                new QuayImageParameterValue("IMG", "coreos", "etcd", "v3.5.0", "quay.mycompany.com");
        assertEquals("quay.mycompany.com/coreos/etcd:v3.5.0", value.getImageReference());
    }

    @Test
    public void testGetValueReturnsImageReference() {
        QuayImageParameterValue value = new QuayImageParameterValue("IMG", "org", "repo", "latest");
        assertEquals("quay.io/org/repo:latest", value.getValue());
    }

    @Test
    public void testGetValueCustomEndpoint() {
        QuayImageParameterValue value =
                new QuayImageParameterValue("IMG", "org", "repo", "latest", "quay.internal.com");
        assertEquals("quay.internal.com/org/repo:latest", value.getValue());
    }

    // =====================================================================
    // Short Description Tests
    // =====================================================================

    @Test
    public void testShortDescriptionDefault() {
        QuayImageParameterValue value = new QuayImageParameterValue("DEPLOY_IMAGE", "org", "app", "v1.0");
        assertEquals("DEPLOY_IMAGE=quay.io/org/app:v1.0", value.getShortDescription());
    }

    @Test
    public void testShortDescriptionCustomEndpoint() {
        QuayImageParameterValue value =
                new QuayImageParameterValue("DEPLOY_IMAGE", "org", "app", "v1.0", "registry.company.io");
        assertEquals("DEPLOY_IMAGE=registry.company.io/org/app:v1.0", value.getShortDescription());
    }

    // =====================================================================
    // Environment Variable Tests
    // =====================================================================

    @Test
    public void testBuildEnvironmentDefaultEndpoint() throws Exception {
        QuayImageParameterValue value = new QuayImageParameterValue("MY_IMG", "myorg", "myrepo", "v1.0");

        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(
                new QuayImageParameterDefinition("MY_IMG", "desc", "myorg", "myrepo")));

        QueueTaskFuture<FreeStyleBuild> future = project.scheduleBuild2(0, new ParametersAction(value));
        FreeStyleBuild build = future.get();

        EnvVars env = new EnvVars();
        value.buildEnvironment(build, env);

        assertEquals("quay.io/myorg/myrepo:v1.0", env.get("MY_IMG"));
        assertEquals("myorg", env.get("MY_IMG_ORG"));
        assertEquals("myrepo", env.get("MY_IMG_REPO"));
        assertEquals("v1.0", env.get("MY_IMG_TAG"));
        assertEquals("quay.io/myorg/myrepo", env.get("MY_IMG_FULL_REPO"));
        assertEquals("quay.io", env.get("MY_IMG_ENDPOINT"));
    }

    @Test
    public void testBuildEnvironmentCustomEndpoint() throws Exception {
        QuayImageParameterValue value =
                new QuayImageParameterValue("MY_IMG", "myorg", "myrepo", "v1.0", "quay.company.com");

        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(
                new QuayImageParameterDefinition("MY_IMG", "desc", "myorg", "myrepo")));

        QueueTaskFuture<FreeStyleBuild> future = project.scheduleBuild2(0, new ParametersAction(value));
        FreeStyleBuild build = future.get();

        EnvVars env = new EnvVars();
        value.buildEnvironment(build, env);

        assertEquals("quay.company.com/myorg/myrepo:v1.0", env.get("MY_IMG"));
        assertEquals("myorg", env.get("MY_IMG_ORG"));
        assertEquals("myrepo", env.get("MY_IMG_REPO"));
        assertEquals("v1.0", env.get("MY_IMG_TAG"));
        assertEquals("quay.company.com/myorg/myrepo", env.get("MY_IMG_FULL_REPO"));
        assertEquals("quay.company.com", env.get("MY_IMG_ENDPOINT"));
    }

    // =====================================================================
    // Variable Resolver Tests
    // =====================================================================

    @Test
    public void testVariableResolverMainVariable() {
        QuayImageParameterValue value = new QuayImageParameterValue("IMG", "org", "repo", "v1.0");
        assertEquals("quay.io/org/repo:v1.0", value.createVariableResolver(null).resolve("IMG"));
    }

    @Test
    public void testVariableResolverOrg() {
        QuayImageParameterValue value = new QuayImageParameterValue("IMG", "myorg", "repo", "v1.0");
        assertEquals("myorg", value.createVariableResolver(null).resolve("IMG_ORG"));
    }

    @Test
    public void testVariableResolverRepo() {
        QuayImageParameterValue value = new QuayImageParameterValue("IMG", "org", "myrepo", "v1.0");
        assertEquals("myrepo", value.createVariableResolver(null).resolve("IMG_REPO"));
    }

    @Test
    public void testVariableResolverTag() {
        QuayImageParameterValue value = new QuayImageParameterValue("IMG", "org", "repo", "v2.5.0");
        assertEquals("v2.5.0", value.createVariableResolver(null).resolve("IMG_TAG"));
    }

    @Test
    public void testVariableResolverFullRepo() {
        QuayImageParameterValue value = new QuayImageParameterValue("IMG", "org", "repo", "v1.0");
        assertEquals("quay.io/org/repo", value.createVariableResolver(null).resolve("IMG_FULL_REPO"));
    }

    @Test
    public void testVariableResolverEndpoint() {
        QuayImageParameterValue value = new QuayImageParameterValue("IMG", "org", "repo", "v1.0", "quay.company.com");
        assertEquals("quay.company.com", value.createVariableResolver(null).resolve("IMG_ENDPOINT"));
    }

    @Test
    public void testVariableResolverFullRepoCustomEndpoint() {
        QuayImageParameterValue value = new QuayImageParameterValue("IMG", "org", "repo", "v1.0", "quay.company.com");
        assertEquals(
                "quay.company.com/org/repo", value.createVariableResolver(null).resolve("IMG_FULL_REPO"));
    }

    @Test
    public void testVariableResolverUnknownReturnsNull() {
        QuayImageParameterValue value = new QuayImageParameterValue("IMG", "org", "repo", "v1.0");
        assertNull(value.createVariableResolver(null).resolve("UNKNOWN"));
    }

    @Test
    public void testVariableResolverImageRefCustomEndpoint() {
        QuayImageParameterValue value =
                new QuayImageParameterValue("IMG", "org", "repo", "v1.0", "registry.internal:5000");
        assertEquals(
                "registry.internal:5000/org/repo:v1.0",
                value.createVariableResolver(null).resolve("IMG"));
    }

    // =====================================================================
    // Equality and HashCode Tests
    // =====================================================================

    @Test
    public void testEqualsSameValues() {
        QuayImageParameterValue v1 = new QuayImageParameterValue("IMG", "org", "repo", "v1.0", "quay.io");
        QuayImageParameterValue v2 = new QuayImageParameterValue("IMG", "org", "repo", "v1.0", "quay.io");
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    public void testNotEqualsDifferentTag() {
        QuayImageParameterValue v1 = new QuayImageParameterValue("IMG", "org", "repo", "v1.0");
        QuayImageParameterValue v2 = new QuayImageParameterValue("IMG", "org", "repo", "v2.0");
        assertNotEquals(v1, v2);
    }

    @Test
    public void testNotEqualsDifferentOrg() {
        QuayImageParameterValue v1 = new QuayImageParameterValue("IMG", "org1", "repo", "v1.0");
        QuayImageParameterValue v2 = new QuayImageParameterValue("IMG", "org2", "repo", "v1.0");
        assertNotEquals(v1, v2);
    }

    @Test
    public void testNotEqualsDifferentRepo() {
        QuayImageParameterValue v1 = new QuayImageParameterValue("IMG", "org", "repo1", "v1.0");
        QuayImageParameterValue v2 = new QuayImageParameterValue("IMG", "org", "repo2", "v1.0");
        assertNotEquals(v1, v2);
    }

    @Test
    public void testNotEqualsDifferentEndpoint() {
        QuayImageParameterValue v1 = new QuayImageParameterValue("IMG", "org", "repo", "v1.0", "quay.io");
        QuayImageParameterValue v2 = new QuayImageParameterValue("IMG", "org", "repo", "v1.0", "quay.company.com");
        assertNotEquals(v1, v2);
    }

    @Test
    public void testNotEqualsNull() {
        QuayImageParameterValue v1 = new QuayImageParameterValue("IMG", "org", "repo", "v1.0");
        assertNotEquals(v1, null);
    }

    @Test
    public void testEqualsSameObject() {
        QuayImageParameterValue v1 = new QuayImageParameterValue("IMG", "org", "repo", "v1.0");
        assertEquals(v1, v1);
    }

    // =====================================================================
    // toString Test
    // =====================================================================

    @Test
    public void testToStringContainsAllFields() {
        QuayImageParameterValue value =
                new QuayImageParameterValue("IMG", "myorg", "myrepo", "v1.0", "quay.company.com");
        String str = value.toString();
        assertTrue(str.contains("IMG"));
        assertTrue(str.contains("myorg"));
        assertTrue(str.contains("myrepo"));
        assertTrue(str.contains("v1.0"));
        assertTrue(str.contains("quay.company.com"));
    }

    @Test
    public void testToStringDefaultEndpoint() {
        QuayImageParameterValue value = new QuayImageParameterValue("IMG", "org", "repo", "latest");
        String str = value.toString();
        assertTrue(str.contains("quay.io"));
    }
}

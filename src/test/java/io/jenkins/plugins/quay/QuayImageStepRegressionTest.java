package io.jenkins.plugins.quay;

import static org.junit.Assert.*;

import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Comprehensive regression tests for QuayImageStep.
 * Covers step creation, custom endpoint, all setters/getters,
 * descriptor, and edge cases for production readiness.
 */
public class QuayImageStepRegressionTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    // =====================================================================
    // Step Creation Tests
    // =====================================================================

    @Test
    public void testStepCreationDefaults() {
        QuayImageStep step = new QuayImageStep("myorg", "myrepo");
        assertEquals("myorg", step.getOrganization());
        assertEquals("myrepo", step.getRepository());
        assertEquals("quay.io", step.getQuayEndpoint());
        assertNull(step.getCredentialsId());
        assertNull(step.getTag());
        assertFalse(step.isListTags());
        assertEquals(20, step.getTagLimit());
    }

    // =====================================================================
    // Custom Endpoint Tests
    // =====================================================================

    @Test
    public void testStepDefaultEndpoint() {
        QuayImageStep step = new QuayImageStep("org", "repo");
        assertEquals("quay.io", step.getQuayEndpoint());
    }

    @Test
    public void testStepCustomEndpoint() {
        QuayImageStep step = new QuayImageStep("org", "repo");
        step.setQuayEndpoint("quay.mycompany.com");
        assertEquals("quay.mycompany.com", step.getQuayEndpoint());
    }

    @Test
    public void testStepNullEndpointDefaultsToQuayIo() {
        QuayImageStep step = new QuayImageStep("org", "repo");
        step.setQuayEndpoint(null);
        assertEquals("quay.io", step.getQuayEndpoint());
    }

    @Test
    public void testStepEmptyEndpointDefaultsToQuayIo() {
        QuayImageStep step = new QuayImageStep("org", "repo");
        step.setQuayEndpoint("");
        assertEquals("quay.io", step.getQuayEndpoint());
    }

    @Test
    public void testStepWhitespaceEndpointDefaultsToQuayIo() {
        QuayImageStep step = new QuayImageStep("org", "repo");
        step.setQuayEndpoint("   ");
        assertEquals("quay.io", step.getQuayEndpoint());
    }

    @Test
    public void testStepEndpointWithPort() {
        QuayImageStep step = new QuayImageStep("org", "repo");
        step.setQuayEndpoint("registry.internal:8443");
        assertEquals("registry.internal:8443", step.getQuayEndpoint());
    }

    // =====================================================================
    // Setter/Getter Tests
    // =====================================================================

    @Test
    public void testSetCredentialsId() {
        QuayImageStep step = new QuayImageStep("org", "repo");
        step.setCredentialsId("my-creds");
        assertEquals("my-creds", step.getCredentialsId());
    }

    @Test
    public void testSetTag() {
        QuayImageStep step = new QuayImageStep("org", "repo");
        step.setTag("v1.0.0");
        assertEquals("v1.0.0", step.getTag());
    }

    @Test
    public void testSetListTags() {
        QuayImageStep step = new QuayImageStep("org", "repo");
        step.setListTags(true);
        assertTrue(step.isListTags());
    }

    @Test
    public void testSetTagLimitValid() {
        QuayImageStep step = new QuayImageStep("org", "repo");
        step.setTagLimit(50);
        assertEquals(50, step.getTagLimit());
    }

    @Test
    public void testSetTagLimitZeroResetsToDefault() {
        QuayImageStep step = new QuayImageStep("org", "repo");
        step.setTagLimit(0);
        assertEquals(20, step.getTagLimit());
    }

    @Test
    public void testSetTagLimitNegativeResetsToDefault() {
        QuayImageStep step = new QuayImageStep("org", "repo");
        step.setTagLimit(-5);
        assertEquals(20, step.getTagLimit());
    }

    @Test
    public void testSetTagLimitOne() {
        QuayImageStep step = new QuayImageStep("org", "repo");
        step.setTagLimit(1);
        assertEquals(1, step.getTagLimit());
    }

    @Test
    public void testSetTagLimitLarge() {
        QuayImageStep step = new QuayImageStep("org", "repo");
        step.setTagLimit(1000);
        assertEquals(1000, step.getTagLimit());
    }

    // =====================================================================
    // Full Configuration Test
    // =====================================================================

    @Test
    public void testStepWithAllOptions() {
        QuayImageStep step = new QuayImageStep("myorg", "myrepo");
        step.setQuayEndpoint("quay.mycompany.com");
        step.setCredentialsId("my-creds");
        step.setTag("v1.0.0");
        step.setListTags(true);
        step.setTagLimit(50);

        assertEquals("myorg", step.getOrganization());
        assertEquals("myrepo", step.getRepository());
        assertEquals("quay.mycompany.com", step.getQuayEndpoint());
        assertEquals("my-creds", step.getCredentialsId());
        assertEquals("v1.0.0", step.getTag());
        assertTrue(step.isListTags());
        assertEquals(50, step.getTagLimit());
    }

    // =====================================================================
    // Descriptor Tests
    // =====================================================================

    @Test
    public void testDescriptorFunctionName() {
        QuayImageStep.DescriptorImpl descriptor = new QuayImageStep.DescriptorImpl();
        assertEquals("quayImage", descriptor.getFunctionName());
    }

    @Test
    public void testDescriptorDisplayName() {
        QuayImageStep.DescriptorImpl descriptor = new QuayImageStep.DescriptorImpl();
        assertEquals("Get Quay.io Image Reference", descriptor.getDisplayName());
    }

    @Test
    public void testDescriptorRequiredContext() {
        QuayImageStep.DescriptorImpl descriptor = new QuayImageStep.DescriptorImpl();
        Set<? extends Class<?>> required = descriptor.getRequiredContext();
        assertTrue(required.contains(Run.class));
        assertTrue(required.contains(TaskListener.class));
        assertEquals(2, required.size());
    }

    @Test
    public void testDescriptorDoesNotTakeImplicitBlock() {
        QuayImageStep.DescriptorImpl descriptor = new QuayImageStep.DescriptorImpl();
        assertFalse(descriptor.takesImplicitBlockArgument());
    }
}

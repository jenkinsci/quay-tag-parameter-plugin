package io.jenkins.plugins.quay;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests for QuayImageStep Pipeline step.
 */
public class QuayImageStepTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testStepCreation() {
        QuayImageStep step = new QuayImageStep("myorg", "myrepo");

        assertEquals("myorg", step.getOrganization());
        assertEquals("myrepo", step.getRepository());
        assertNull(step.getCredentialsId());
        assertNull(step.getTag());
        assertFalse(step.isListTags());
        assertEquals(20, step.getTagLimit());
    }

    @Test
    public void testStepWithAllOptions() {
        QuayImageStep step = new QuayImageStep("myorg", "myrepo");
        step.setCredentialsId("my-creds");
        step.setTag("v1.0.0");
        step.setListTags(true);
        step.setTagLimit(50);

        assertEquals("my-creds", step.getCredentialsId());
        assertEquals("v1.0.0", step.getTag());
        assertTrue(step.isListTags());
        assertEquals(50, step.getTagLimit());
    }

    @Test
    public void testDescriptorFunctionName() {
        QuayImageStep.DescriptorImpl descriptor = new QuayImageStep.DescriptorImpl();
        assertEquals("quayImage", descriptor.getFunctionName());
        assertEquals("Get Quay.io Image Reference", descriptor.getDisplayName());
    }

    @Test
    public void testTagLimitValidation() {
        QuayImageStep step = new QuayImageStep("org", "repo");

        // Invalid value should default to 20
        step.setTagLimit(-5);
        assertEquals(20, step.getTagLimit());

        step.setTagLimit(0);
        assertEquals(20, step.getTagLimit());

        // Valid value should be kept
        step.setTagLimit(100);
        assertEquals(100, step.getTagLimit());
    }
}

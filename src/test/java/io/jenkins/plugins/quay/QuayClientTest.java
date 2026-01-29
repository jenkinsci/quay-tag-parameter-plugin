package io.jenkins.plugins.quay;

import static org.junit.Assert.*;

import io.jenkins.plugins.quay.model.QuayTag;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for QuayClient.
 */
public class QuayClientTest {

    @Before
    public void setUp() {
        QuayClient.clearCache();
    }

    @Test
    public void testBuildImageReference() {
        String result = QuayClient.buildImageReference("myorg", "myrepo", "v1.0.0");
        assertEquals("quay.io/myorg/myrepo:v1.0.0", result);
    }

    @Test
    public void testBuildImageReferenceWithLatest() {
        String result = QuayClient.buildImageReference("coreos", "etcd", "latest");
        assertEquals("quay.io/coreos/etcd:latest", result);
    }

    @Test(expected = QuayClient.QuayApiException.class)
    public void testEmptyOrganization() throws QuayClient.QuayApiException {
        QuayClient client = new QuayClient();
        client.getTags("", "repo");
    }

    @Test(expected = QuayClient.QuayApiException.class)
    public void testNullOrganization() throws QuayClient.QuayApiException {
        QuayClient client = new QuayClient();
        client.getTags(null, "repo");
    }

    @Test(expected = QuayClient.QuayApiException.class)
    public void testEmptyRepository() throws QuayClient.QuayApiException {
        QuayClient client = new QuayClient();
        client.getTags("org", "");
    }

    @Test(expected = QuayClient.QuayApiException.class)
    public void testInvalidOrganizationCharacters() throws QuayClient.QuayApiException {
        QuayClient client = new QuayClient();
        client.getTags("org/../../../etc", "repo");
    }

    @Test(expected = QuayClient.QuayApiException.class)
    public void testInvalidRepositoryCharacters() throws QuayClient.QuayApiException {
        QuayClient client = new QuayClient();
        client.getTags("org", "repo; rm -rf /");
    }

    @Test
    public void testQuayTagComparison() {
        QuayTag tag1 = new QuayTag("v1.0.0");
        tag1.setStartTimestamp(1000L);

        QuayTag tag2 = new QuayTag("v2.0.0");
        tag2.setStartTimestamp(2000L);

        // tag2 should come before tag1 (more recent first)
        assertTrue(tag1.compareTo(tag2) > 0);
        assertTrue(tag2.compareTo(tag1) < 0);
    }

    @Test
    public void testQuayApiExceptionWithCode() {
        QuayClient.QuayApiException ex = new QuayClient.QuayApiException("Not found", 404);
        assertEquals(404, ex.getHttpCode());
        assertEquals("Not found", ex.getMessage());
    }

    @Test
    public void testQuayApiExceptionWithoutCode() {
        QuayClient.QuayApiException ex = new QuayClient.QuayApiException("Error");
        assertEquals(-1, ex.getHttpCode());
        assertEquals("Error", ex.getMessage());
    }
}

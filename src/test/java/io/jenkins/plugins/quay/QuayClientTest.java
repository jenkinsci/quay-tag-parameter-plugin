package io.jenkins.plugins.quay;

import static org.junit.jupiter.api.Assertions.*;

import io.jenkins.plugins.quay.model.QuayTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for QuayClient.
 */
class QuayClientTest {

    @BeforeEach
    void beforeEach() {
        QuayClient.clearCache();
    }

    @Test
    void testBuildImageReference() {
        String result = QuayClient.buildImageReference("myorg", "myrepo", "v1.0.0");
        assertEquals("quay.io/myorg/myrepo:v1.0.0", result);
    }

    @Test
    void testBuildImageReferenceWithLatest() {
        String result = QuayClient.buildImageReference("coreos", "etcd", "latest");
        assertEquals("quay.io/coreos/etcd:latest", result);
    }

    @Test
    void testEmptyOrganization() {
        QuayClient client = new QuayClient();
        assertThrows(QuayClient.QuayApiException.class, () -> client.getTags("", "repo"));
    }

    @Test
    void testNullOrganization() {
        QuayClient client = new QuayClient();
        assertThrows(QuayClient.QuayApiException.class, () -> client.getTags(null, "repo"));
    }

    @Test
    void testEmptyRepository() {
        QuayClient client = new QuayClient();
        assertThrows(QuayClient.QuayApiException.class, () -> client.getTags("org", ""));
    }

    @Test
    void testInvalidOrganizationCharacters() {
        QuayClient client = new QuayClient();
        assertThrows(QuayClient.QuayApiException.class, () -> client.getTags("org/../../../etc", "repo"));
    }

    @Test
    void testInvalidRepositoryCharacters() {
        QuayClient client = new QuayClient();
        assertThrows(QuayClient.QuayApiException.class, () -> client.getTags("org", "repo; rm -rf /"));
    }

    @Test
    void testQuayTagComparison() {
        QuayTag tag1 = new QuayTag("v1.0.0");
        tag1.setStartTimestamp(1000L);

        QuayTag tag2 = new QuayTag("v2.0.0");
        tag2.setStartTimestamp(2000L);

        // tag2 should come before tag1 (more recent first)
        assertTrue(tag1.compareTo(tag2) > 0);
        assertTrue(tag2.compareTo(tag1) < 0);
    }

    @Test
    void testQuayApiExceptionWithCode() {
        QuayClient.QuayApiException ex = new QuayClient.QuayApiException("Not found", 404);
        assertEquals(404, ex.getHttpCode());
        assertEquals("Not found", ex.getMessage());
    }

    @Test
    void testQuayApiExceptionWithoutCode() {
        QuayClient.QuayApiException ex = new QuayClient.QuayApiException("Error");
        assertEquals(-1, ex.getHttpCode());
        assertEquals("Error", ex.getMessage());
    }
}

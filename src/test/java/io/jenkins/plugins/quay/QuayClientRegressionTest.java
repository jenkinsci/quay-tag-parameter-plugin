package io.jenkins.plugins.quay;

import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.util.Secret;
import io.jenkins.plugins.quay.model.QuayTag;
import io.jenkins.plugins.quay.model.QuayTagResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Comprehensive regression tests for QuayClient.
 * Covers custom endpoint, input validation, caching, image references,
 * error handling, and edge cases for production readiness.
 */
public class QuayClientRegressionTest {

    @Before
    public void setUp() {
        QuayClient.clearCache();
    }

    // =====================================================================
    // Custom Endpoint Tests
    // =====================================================================

    @Test
    public void testDefaultEndpointIsQuayIo() {
        QuayClient client = new QuayClient();
        assertEquals("quay.io", client.getQuayEndpoint());
    }

    @Test
    public void testCustomEndpointWithHostname() {
        QuayClient client = new QuayClient("quay.mycompany.com", (String) null);
        assertEquals("quay.mycompany.com", client.getQuayEndpoint());
    }

    @Test
    public void testCustomEndpointStripsHttpsProtocol() {
        QuayClient client = new QuayClient("https://quay.mycompany.com", (String) null);
        assertEquals("quay.mycompany.com", client.getQuayEndpoint());
    }

    @Test
    public void testCustomEndpointStripsHttpProtocol() {
        QuayClient client = new QuayClient("http://quay.mycompany.com", (String) null);
        assertEquals("quay.mycompany.com", client.getQuayEndpoint());
    }

    @Test
    public void testCustomEndpointStripsTrailingSlash() {
        QuayClient client = new QuayClient("quay.mycompany.com/", (String) null);
        assertEquals("quay.mycompany.com", client.getQuayEndpoint());
    }

    @Test
    public void testCustomEndpointStripsMultipleTrailingSlashes() {
        QuayClient client = new QuayClient("quay.mycompany.com///", (String) null);
        assertEquals("quay.mycompany.com", client.getQuayEndpoint());
    }

    @Test
    public void testCustomEndpointStripsProtocolAndTrailingSlash() {
        QuayClient client = new QuayClient("https://quay.mycompany.com/", (String) null);
        assertEquals("quay.mycompany.com", client.getQuayEndpoint());
    }

    @Test
    public void testNullEndpointDefaultsToQuayIo() {
        QuayClient client = new QuayClient(null, (String) null);
        assertEquals("quay.io", client.getQuayEndpoint());
    }

    @Test
    public void testEmptyEndpointDefaultsToQuayIo() {
        QuayClient client = new QuayClient("", (String) null);
        assertEquals("quay.io", client.getQuayEndpoint());
    }

    @Test
    public void testWhitespaceEndpointDefaultsToQuayIo() {
        QuayClient client = new QuayClient("   ", (String) null);
        assertEquals("quay.io", client.getQuayEndpoint());
    }

    @Test
    public void testEndpointWithPort() {
        QuayClient client = new QuayClient("quay.mycompany.com:8443", (String) null);
        assertEquals("quay.mycompany.com:8443", client.getQuayEndpoint());
    }

    @Test
    public void testEndpointWithSubdomain() {
        QuayClient client = new QuayClient("registry.quay.internal.company.com", (String) null);
        assertEquals("registry.quay.internal.company.com", client.getQuayEndpoint());
    }

    // =====================================================================
    // Constructor Tests
    // =====================================================================

    @Test
    public void testNoArgConstructor() {
        QuayClient client = new QuayClient();
        assertEquals("quay.io", client.getQuayEndpoint());
    }

    @Test
    public void testStringTokenConstructor() {
        QuayClient client = new QuayClient("my-token");
        assertEquals("quay.io", client.getQuayEndpoint());
    }

    @Test
    public void testSecretTokenConstructor() {
        QuayClient client = new QuayClient(Secret.fromString("my-token"));
        assertEquals("quay.io", client.getQuayEndpoint());
    }

    @Test
    public void testNullStringTokenConstructor() {
        QuayClient client = new QuayClient((String) null);
        assertEquals("quay.io", client.getQuayEndpoint());
    }

    @Test
    public void testNullSecretTokenConstructor() {
        QuayClient client = new QuayClient((Secret) null);
        assertEquals("quay.io", client.getQuayEndpoint());
    }

    @Test
    public void testEmptyStringTokenConstructor() {
        QuayClient client = new QuayClient("");
        assertEquals("quay.io", client.getQuayEndpoint());
    }

    @Test
    public void testEndpointWithStringToken() {
        QuayClient client = new QuayClient("quay.mycompany.com", "my-token");
        assertEquals("quay.mycompany.com", client.getQuayEndpoint());
    }

    @Test
    public void testEndpointWithSecretToken() {
        QuayClient client = new QuayClient("quay.mycompany.com", Secret.fromString("my-token"));
        assertEquals("quay.mycompany.com", client.getQuayEndpoint());
    }

    // =====================================================================
    // Image Reference Tests
    // =====================================================================

    @Test
    public void testBuildImageReferenceDefault() {
        String ref = QuayClient.buildImageReference("myorg", "myrepo", "v1.0.0");
        assertEquals("quay.io/myorg/myrepo:v1.0.0", ref);
    }

    @Test
    public void testBuildImageReferenceWithCustomEndpoint() {
        String ref = QuayClient.buildImageReference("quay.mycompany.com", "myorg", "myrepo", "v1.0.0");
        assertEquals("quay.mycompany.com/myorg/myrepo:v1.0.0", ref);
    }

    @Test
    public void testBuildImageReferenceWithEndpointHttpsStripped() {
        String ref = QuayClient.buildImageReference("https://quay.mycompany.com", "org", "repo", "latest");
        assertEquals("quay.mycompany.com/org/repo:latest", ref);
    }

    @Test
    public void testBuildImageReferenceWithNullEndpoint() {
        String ref = QuayClient.buildImageReference(null, "org", "repo", "v1.0");
        assertEquals("quay.io/org/repo:v1.0", ref);
    }

    @Test
    public void testBuildImageReferenceWithEmptyEndpoint() {
        String ref = QuayClient.buildImageReference("", "org", "repo", "v1.0");
        assertEquals("quay.io/org/repo:v1.0", ref);
    }

    @Test
    public void testBuildImageReferenceWithLatestTag() {
        String ref = QuayClient.buildImageReference("coreos", "etcd", "latest");
        assertEquals("quay.io/coreos/etcd:latest", ref);
    }

    @Test
    public void testBuildImageReferenceWithShaTag() {
        String ref = QuayClient.buildImageReference("myorg", "myrepo", "sha-abc1234");
        assertEquals("quay.io/myorg/myrepo:sha-abc1234", ref);
    }

    @Test
    public void testBuildImageReferenceWithEndpointPort() {
        String ref = QuayClient.buildImageReference("quay.internal:8443", "myorg", "myrepo", "v2.0.0");
        assertEquals("quay.internal:8443/myorg/myrepo:v2.0.0", ref);
    }

    @Test
    public void testBuildImageReferenceWithNestedRepo() {
        String ref = QuayClient.buildImageReference("myorg", "team/myrepo", "v1.0.0");
        assertEquals("quay.io/myorg/team/myrepo:v1.0.0", ref);
    }

    // =====================================================================
    // Input Validation Tests
    // =====================================================================

    @Test(expected = QuayClient.QuayApiException.class)
    public void testNullOrganizationThrows() throws QuayClient.QuayApiException {
        new QuayClient().getTags(null, "repo");
    }

    @Test(expected = QuayClient.QuayApiException.class)
    public void testEmptyOrganizationThrows() throws QuayClient.QuayApiException {
        new QuayClient().getTags("", "repo");
    }

    @Test(expected = QuayClient.QuayApiException.class)
    public void testWhitespaceOrganizationThrows() throws QuayClient.QuayApiException {
        new QuayClient().getTags("   ", "repo");
    }

    @Test(expected = QuayClient.QuayApiException.class)
    public void testNullRepositoryThrows() throws QuayClient.QuayApiException {
        new QuayClient().getTags("org", null);
    }

    @Test(expected = QuayClient.QuayApiException.class)
    public void testEmptyRepositoryThrows() throws QuayClient.QuayApiException {
        new QuayClient().getTags("org", "");
    }

    @Test(expected = QuayClient.QuayApiException.class)
    public void testPathTraversalOrgRejected() throws QuayClient.QuayApiException {
        new QuayClient().getTags("org/../../../etc", "repo");
    }

    @Test(expected = QuayClient.QuayApiException.class)
    public void testCommandInjectionRepoRejected() throws QuayClient.QuayApiException {
        new QuayClient().getTags("org", "repo; rm -rf /");
    }

    @Test(expected = QuayClient.QuayApiException.class)
    public void testSpecialCharsOrgRejected() throws QuayClient.QuayApiException {
        new QuayClient().getTags("org<script>", "repo");
    }

    @Test(expected = QuayClient.QuayApiException.class)
    public void testSpacesInOrgRejected() throws QuayClient.QuayApiException {
        new QuayClient().getTags("my org", "repo");
    }

    @Test(expected = QuayClient.QuayApiException.class)
    public void testSpacesInRepoRejected() throws QuayClient.QuayApiException {
        new QuayClient().getTags("org", "my repo");
    }

    // Valid characters should not throw
    @Test
    public void testValidOrgWithDotsAndHyphens() throws QuayClient.QuayApiException {
        // This will fail at the network level, but should not fail validation
        QuayClient client = new QuayClient("localhost:1", (String) null);
        try {
            client.getTags("my-org.name", "repo", 1);
        } catch (QuayClient.QuayApiException e) {
            // Network error is expected, but validation should pass
            assertTrue(
                    "Should be a network error, not validation", e.getMessage().contains("Network error"));
        }
    }

    @Test
    public void testValidRepoWithSlash() throws QuayClient.QuayApiException {
        QuayClient client = new QuayClient("localhost:1", (String) null);
        try {
            client.getTags("org", "team/repo", 1);
        } catch (QuayClient.QuayApiException e) {
            assertTrue(
                    "Should be a network error, not validation", e.getMessage().contains("Network error"));
        }
    }

    // =====================================================================
    // Cache Tests
    // =====================================================================

    @Test
    public void testClearCache() {
        QuayClient.clearCache();
        // Should not throw
    }

    @Test
    public void testClearCacheMultipleTimes() {
        QuayClient.clearCache();
        QuayClient.clearCache();
        QuayClient.clearCache();
        // Should not throw
    }

    // =====================================================================
    // Exception Tests
    // =====================================================================

    @Test
    public void testQuayApiExceptionWithHttpCode() {
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

    @Test
    public void testQuayApiExceptionWithCause() {
        IOException cause = new IOException("connection refused");
        QuayClient.QuayApiException ex = new QuayClient.QuayApiException("Network error", cause);
        assertEquals(-1, ex.getHttpCode());
        assertEquals("Network error", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    public void testQuayApiException401() {
        QuayClient.QuayApiException ex = new QuayClient.QuayApiException("Auth failed", 401);
        assertEquals(401, ex.getHttpCode());
    }

    @Test
    public void testQuayApiException403() {
        QuayClient.QuayApiException ex = new QuayClient.QuayApiException("Forbidden", 403);
        assertEquals(403, ex.getHttpCode());
    }

    @Test
    public void testQuayApiException429() {
        QuayClient.QuayApiException ex = new QuayClient.QuayApiException("Rate limited", 429);
        assertEquals(429, ex.getHttpCode());
    }

    // =====================================================================
    // QuayTag Model Tests
    // =====================================================================

    @Test
    public void testQuayTagDefaultConstructor() {
        QuayTag tag = new QuayTag();
        assertNull(tag.getName());
        assertNull(tag.getManifestDigest());
        assertNull(tag.getSize());
        assertNull(tag.getLastModified());
        assertNull(tag.getExpiration());
        assertNull(tag.getStartTimestamp());
        assertNull(tag.getEndTimestamp());
        assertEquals(0L, tag.getSortTimestamp());
    }

    @Test
    public void testQuayTagNameConstructor() {
        QuayTag tag = new QuayTag("v1.0.0");
        assertEquals("v1.0.0", tag.getName());
    }

    @Test
    public void testQuayTagSetters() {
        QuayTag tag = new QuayTag();
        tag.setName("v2.0.0");
        tag.setManifestDigest("sha256:abc123");
        tag.setSize(1024L);
        tag.setLastModified("2026-01-01T00:00:00Z");
        tag.setExpiration("2027-01-01T00:00:00Z");
        tag.setStartTimestamp(1000L);
        tag.setEndTimestamp(2000L);

        assertEquals("v2.0.0", tag.getName());
        assertEquals("sha256:abc123", tag.getManifestDigest());
        assertEquals(Long.valueOf(1024L), tag.getSize());
        assertEquals("2026-01-01T00:00:00Z", tag.getLastModified());
        assertEquals("2027-01-01T00:00:00Z", tag.getExpiration());
        assertEquals(Long.valueOf(1000L), tag.getStartTimestamp());
        assertEquals(Long.valueOf(2000L), tag.getEndTimestamp());
    }

    @Test
    public void testQuayTagSortTimestampWithValue() {
        QuayTag tag = new QuayTag("v1.0.0");
        tag.setStartTimestamp(5000L);
        assertEquals(5000L, tag.getSortTimestamp());
    }

    @Test
    public void testQuayTagSortTimestampWithNull() {
        QuayTag tag = new QuayTag("v1.0.0");
        assertEquals(0L, tag.getSortTimestamp());
    }

    @Test
    public void testQuayTagSortingMostRecentFirst() {
        QuayTag older = new QuayTag("v1.0.0");
        older.setStartTimestamp(1000L);

        QuayTag newer = new QuayTag("v2.0.0");
        newer.setStartTimestamp(2000L);

        QuayTag newest = new QuayTag("v3.0.0");
        newest.setStartTimestamp(3000L);

        List<QuayTag> tags = new ArrayList<>(Arrays.asList(older, newest, newer));
        Collections.sort(tags);

        assertEquals("v3.0.0", tags.get(0).getName());
        assertEquals("v2.0.0", tags.get(1).getName());
        assertEquals("v1.0.0", tags.get(2).getName());
    }

    @Test
    public void testQuayTagSortingWithNullTimestamps() {
        QuayTag withTs = new QuayTag("v1.0.0");
        withTs.setStartTimestamp(1000L);

        QuayTag noTs = new QuayTag("v2.0.0");

        assertTrue(withTs.compareTo(noTs) < 0); // withTs comes first (more recent)
        assertTrue(noTs.compareTo(withTs) > 0);
    }

    @Test
    public void testQuayTagEqualsSameObject() {
        QuayTag tag = new QuayTag("v1.0.0");
        assertEquals(tag, tag);
    }

    @Test
    public void testQuayTagEqualsSameName() {
        QuayTag tag1 = new QuayTag("v1.0.0");
        QuayTag tag2 = new QuayTag("v1.0.0");
        assertEquals(tag1, tag2);
    }

    @Test
    public void testQuayTagNotEqualsDifferentName() {
        QuayTag tag1 = new QuayTag("v1.0.0");
        QuayTag tag2 = new QuayTag("v2.0.0");
        assertNotEquals(tag1, tag2);
    }

    @Test
    public void testQuayTagNotEqualsNull() {
        QuayTag tag = new QuayTag("v1.0.0");
        assertNotEquals(tag, null);
    }

    @Test
    public void testQuayTagNotEqualsDifferentClass() {
        QuayTag tag = new QuayTag("v1.0.0");
        assertNotEquals(tag, "v1.0.0");
    }

    @Test
    public void testQuayTagEqualsNullNames() {
        QuayTag tag1 = new QuayTag();
        QuayTag tag2 = new QuayTag();
        assertEquals(tag1, tag2);
    }

    @Test
    public void testQuayTagHashCodeConsistency() {
        QuayTag tag1 = new QuayTag("v1.0.0");
        QuayTag tag2 = new QuayTag("v1.0.0");
        assertEquals(tag1.hashCode(), tag2.hashCode());
    }

    @Test
    public void testQuayTagHashCodeNullName() {
        QuayTag tag = new QuayTag();
        assertEquals(0, tag.hashCode());
    }

    @Test
    public void testQuayTagToString() {
        QuayTag tag = new QuayTag("v1.0.0");
        assertEquals("v1.0.0", tag.toString());
    }

    @Test
    public void testQuayTagToStringNull() {
        QuayTag tag = new QuayTag();
        assertNull(tag.toString());
    }

    // =====================================================================
    // QuayTagResponse Model Tests
    // =====================================================================

    @Test
    public void testQuayTagResponseDefaults() {
        QuayTagResponse response = new QuayTagResponse();
        assertNotNull(response.getTags());
        assertTrue(response.getTags().isEmpty());
        assertNull(response.getPage());
        assertNull(response.getHasAdditional());
    }

    @Test
    public void testQuayTagResponseSetTags() {
        QuayTagResponse response = new QuayTagResponse();
        List<QuayTag> tags = Arrays.asList(new QuayTag("v1"), new QuayTag("v2"));
        response.setTags(tags);
        assertEquals(2, response.getTags().size());
    }

    @Test
    public void testQuayTagResponseSetNullTagsBecomesEmpty() {
        QuayTagResponse response = new QuayTagResponse();
        response.setTags(null);
        assertNotNull(response.getTags());
        assertTrue(response.getTags().isEmpty());
    }

    @Test
    public void testQuayTagResponsePage() {
        QuayTagResponse response = new QuayTagResponse();
        response.setPage(2);
        assertEquals(Integer.valueOf(2), response.getPage());
    }

    @Test
    public void testQuayTagResponseHasAdditional() {
        QuayTagResponse response = new QuayTagResponse();
        response.setHasAdditional(true);
        assertTrue(response.getHasAdditional());
    }

    // =====================================================================
    // Jackson Deserialization Tests
    // =====================================================================

    @Test
    public void testQuayTagJsonDeserialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = "{\"name\":\"v1.0.0\",\"manifest_digest\":\"sha256:abc\",\"size\":2048,"
                + "\"last_modified\":\"Mon, 01 Jan 2026 00:00:00 -0000\","
                + "\"start_ts\":1704067200,\"end_ts\":null}";

        QuayTag tag = mapper.readValue(json, QuayTag.class);
        assertEquals("v1.0.0", tag.getName());
        assertEquals("sha256:abc", tag.getManifestDigest());
        assertEquals(Long.valueOf(2048), tag.getSize());
        assertEquals(Long.valueOf(1704067200L), tag.getStartTimestamp());
        assertNull(tag.getEndTimestamp());
    }

    @Test
    public void testQuayTagJsonIgnoresUnknownFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = "{\"name\":\"v1.0.0\",\"unknown_field\":\"something\",\"another\":123}";

        QuayTag tag = mapper.readValue(json, QuayTag.class);
        assertEquals("v1.0.0", tag.getName());
    }

    @Test
    public void testQuayTagResponseJsonDeserialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = "{\"tags\":[{\"name\":\"v1.0.0\"},{\"name\":\"v2.0.0\"}],\"page\":1,\"has_additional\":true}";

        QuayTagResponse response = mapper.readValue(json, QuayTagResponse.class);
        assertEquals(2, response.getTags().size());
        assertEquals("v1.0.0", response.getTags().get(0).getName());
        assertEquals("v2.0.0", response.getTags().get(1).getName());
        assertEquals(Integer.valueOf(1), response.getPage());
        assertTrue(response.getHasAdditional());
    }

    @Test
    public void testQuayTagResponseEmptyTagsJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = "{\"tags\":[],\"page\":1,\"has_additional\":false}";

        QuayTagResponse response = mapper.readValue(json, QuayTagResponse.class);
        assertNotNull(response.getTags());
        assertTrue(response.getTags().isEmpty());
        assertFalse(response.getHasAdditional());
    }

    @Test
    public void testQuayTagResponseJsonIgnoresUnknownFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = "{\"tags\":[],\"unknown_field\":\"value\"}";

        QuayTagResponse response = mapper.readValue(json, QuayTagResponse.class);
        assertNotNull(response.getTags());
    }

    // Inner class alias so the import is unnecessary
    private static class IOException extends java.io.IOException {
        IOException(String msg) {
            super(msg);
        }
    }
}

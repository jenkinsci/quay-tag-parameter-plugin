package io.jenkins.plugins.quay;

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.util.Secret;
import io.jenkins.plugins.quay.model.QuayTag;
import io.jenkins.plugins.quay.model.QuayTagResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Client for interacting with Quay.io REST API v1.
 * Supports both public and private repositories.
 * Implements caching with 5-minute TTL.
 */
public class QuayClient {

    private static final Logger LOGGER = Logger.getLogger(QuayClient.class.getName());
    private static final String DEFAULT_QUAY_ENDPOINT = "quay.io";
    private static final int DEFAULT_LIMIT = 20;
    private static final int CONNECTION_TIMEOUT_SECONDS = 30;
    private static final int READ_TIMEOUT_SECONDS = 30;
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5);

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Secret apiToken;
    private final String quayEndpoint;
    private final String apiBase;

    // Simple cache with TTL
    private static final Map<String, CacheEntry> tagCache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final List<QuayTag> tags;
        final long timestamp;

        CacheEntry(List<QuayTag> tags) {
            this.tags = tags;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    /**
     * Create a QuayClient for public repository access using the default quay.io endpoint.
     */
    public QuayClient() {
        this(DEFAULT_QUAY_ENDPOINT, (Secret) null);
    }

    /**
     * Create a QuayClient with optional authentication token using the default quay.io endpoint.
     *
     * @param apiToken Quay API token (robot token) for private repos, or null for public repos
     */
    public QuayClient(String apiToken) {
        this(DEFAULT_QUAY_ENDPOINT, apiToken);
    }

    /**
     * Create a QuayClient with optional authentication token as Secret using the default quay.io endpoint.
     *
     * @param apiToken Quay API token as Secret for private repos, or null for public repos
     */
    public QuayClient(Secret apiToken) {
        this(DEFAULT_QUAY_ENDPOINT, apiToken);
    }

    /**
     * Create a QuayClient with a custom Quay endpoint and optional authentication token.
     *
     * @param quayEndpoint The Quay registry hostname (e.g., quay.io or quay.mycompany.com)
     * @param apiToken     Quay API token (robot token) for private repos, or null for public repos
     */
    public QuayClient(String quayEndpoint, String apiToken) {
        this(quayEndpoint, apiToken != null && !apiToken.trim().isEmpty() ? Secret.fromString(apiToken) : null);
    }

    /**
     * Create a QuayClient with a custom Quay endpoint and optional authentication token as Secret.
     *
     * @param quayEndpoint The Quay registry hostname (e.g., quay.io or quay.mycompany.com)
     * @param apiToken     Quay API token as Secret for private repos, or null for public repos
     */
    public QuayClient(String quayEndpoint, Secret apiToken) {
        this.quayEndpoint = normalizeEndpoint(quayEndpoint);
        this.apiBase = "https://" + this.quayEndpoint + "/api/v1";
        this.apiToken = apiToken;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Get the Quay endpoint hostname.
     */
    public String getQuayEndpoint() {
        return quayEndpoint;
    }

    private static String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return DEFAULT_QUAY_ENDPOINT;
        }
        String normalized = endpoint.trim();
        // Strip protocol if provided
        if (normalized.startsWith("https://")) {
            normalized = normalized.substring("https://".length());
        } else if (normalized.startsWith("http://")) {
            normalized = normalized.substring("http://".length());
        }
        // Strip trailing slash
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isEmpty() ? DEFAULT_QUAY_ENDPOINT : normalized;
    }

    /**
     * Fetch tags from a Quay.io repository.
     *
     * @param organization The organization/namespace
     * @param repository   The repository name
     * @return List of tags sorted by most recent first
     * @throws QuayApiException if the API call fails
     */
    public List<QuayTag> getTags(String organization, String repository) throws QuayApiException {
        return getTags(organization, repository, DEFAULT_LIMIT);
    }

    /**
     * Fetch tags from a Quay.io repository with a specified limit.
     *
     * @param organization The organization/namespace
     * @param repository   The repository name
     * @param limit        Maximum number of tags to return
     * @return List of tags sorted by most recent first
     * @throws QuayApiException if the API call fails
     */
    public List<QuayTag> getTags(String organization, String repository, int limit) throws QuayApiException {
        validateInput(organization, "organization");
        validateInput(repository, "repository");

        String cacheKey = buildCacheKey(organization, repository, limit);

        // Check cache first
        CacheEntry cached = tagCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            LOGGER.fine("Returning cached tags for " + organization + "/" + repository);
            return new ArrayList<>(cached.tags);
        }

        // Remove expired entry
        if (cached != null) {
            tagCache.remove(cacheKey);
        }

        // Fetch from API
        List<QuayTag> tags = fetchTagsFromApi(organization, repository, limit);

        // Cache the result
        tagCache.put(cacheKey, new CacheEntry(new ArrayList<>(tags)));

        return tags;
    }

    /**
     * Validate a repository exists and is accessible.
     *
     * @param organization The organization/namespace
     * @param repository   The repository name
     * @return true if the repository exists and is accessible
     */
    public boolean validateRepository(String organization, String repository) {
        try {
            String url = String.format("%s/repository/%s/%s", apiBase, organization, repository);
            Request.Builder requestBuilder = new Request.Builder().url(url).get();

            addAuthHeader(requestBuilder);

            try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to validate repository: " + e.getMessage());
            return false;
        }
    }

    /**
     * Clear the tag cache. Useful for testing or forcing refresh.
     */
    public static void clearCache() {
        tagCache.clear();
    }

    /**
     * Build the full image reference string using the default quay.io endpoint.
     *
     * @param organization The organization/namespace
     * @param repository   The repository name
     * @param tag          The tag name
     * @return Full image reference (e.g., quay.io/org/repo:tag)
     */
    public static String buildImageReference(String organization, String repository, String tag) {
        return buildImageReference(DEFAULT_QUAY_ENDPOINT, organization, repository, tag);
    }

    /**
     * Build the full image reference string with a custom endpoint.
     *
     * @param quayEndpoint The Quay registry hostname
     * @param organization The organization/namespace
     * @param repository   The repository name
     * @param tag          The tag name
     * @return Full image reference (e.g., quay.mycompany.com/org/repo:tag)
     */
    public static String buildImageReference(String quayEndpoint, String organization, String repository, String tag) {
        String endpoint = normalizeEndpoint(quayEndpoint);
        return String.format("%s/%s/%s:%s", endpoint, organization, repository, tag);
    }

    private List<QuayTag> fetchTagsFromApi(String organization, String repository, int limit) throws QuayApiException {
        String url = String.format(
                "%s/repository/%s/%s/tag/?limit=%d&onlyActiveTags=true", apiBase, organization, repository, limit);

        LOGGER.fine("Fetching tags from: " + url);

        Request.Builder requestBuilder = new Request.Builder().url(url).get().header("Accept", "application/json");

        addAuthHeader(requestBuilder);

        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                handleErrorResponse(response, organization, repository);
            }

            okhttp3.ResponseBody body = response.body();
            String responseBody = body != null ? body.string() : "";
            QuayTagResponse tagResponse = objectMapper.readValue(responseBody, QuayTagResponse.class);

            List<QuayTag> tags = tagResponse.getTags();
            if (tags == null) {
                return new ArrayList<>();
            }

            // Sort by most recent first
            Collections.sort(tags);

            return tags;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Network error fetching tags: " + e.getMessage());
            throw new QuayApiException("Network error: " + e.getMessage(), e);
        }
    }

    private void addAuthHeader(Request.Builder requestBuilder) {
        if (apiToken != null) {
            String token = apiToken.getPlainText();
            if (token != null && !token.trim().isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }
        }
    }

    private void handleErrorResponse(Response response, String organization, String repository)
            throws QuayApiException {
        int code = response.code();
        String message;

        switch (code) {
            case 401:
                message = "Authentication failed. Please check your Quay.io credentials.";
                break;
            case 403:
                message = "Access denied to repository " + organization + "/" + repository
                        + ". Ensure you have permission and valid credentials.";
                break;
            case 404:
                message = "Repository " + organization + "/" + repository + " not found. "
                        + "Please verify the organization and repository names.";
                break;
            case 429:
                message = "Rate limit exceeded. Please try again later.";
                break;
            default:
                message = "Quay.io API error (HTTP " + code + ")";
        }

        LOGGER.warning("Quay API error: " + message);
        throw new QuayApiException(message, code);
    }

    private void validateInput(String value, String fieldName) throws QuayApiException {
        if (value == null || value.trim().isEmpty()) {
            throw new QuayApiException(fieldName + " cannot be empty");
        }
        // Basic validation - no special characters that could cause issues
        if (!value.matches("^[a-zA-Z0-9._/-]+$")) {
            throw new QuayApiException(fieldName + " contains invalid characters. "
                    + "Only alphanumeric characters, dots, underscores, slashes, and hyphens are allowed.");
        }
    }

    private String buildCacheKey(String organization, String repository, int limit) {
        String tokenHash =
                apiToken != null ? String.valueOf(apiToken.getEncryptedValue().hashCode()) : "public";
        return String.format("%s:%s/%s:%d:%s", quayEndpoint, organization, repository, limit, tokenHash);
    }

    /**
     * Exception class for Quay API errors.
     */
    public static class QuayApiException extends Exception {

        private static final long serialVersionUID = 1L;
        private final int httpCode;

        public QuayApiException(String message) {
            super(message);
            this.httpCode = -1;
        }

        public QuayApiException(String message, int httpCode) {
            super(message);
            this.httpCode = httpCode;
        }

        public QuayApiException(String message, Throwable cause) {
            super(message, cause);
            this.httpCode = -1;
        }

        public int getHttpCode() {
            return httpCode;
        }
    }
}

package io.jenkins.plugins.quay.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Serializable;

/**
 * Represents a single tag from a Quay.io repository.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuayTag implements Serializable, Comparable<QuayTag> {

    private static final long serialVersionUID = 1L;

    @JsonProperty("name")
    private String name;

    @JsonProperty("manifest_digest")
    private String manifestDigest;

    @JsonProperty("size")
    private Long size;

    @JsonProperty("last_modified")
    private String lastModified;

    @JsonProperty("expiration")
    private String expiration;

    @JsonProperty("start_ts")
    private Long startTimestamp;

    @JsonProperty("end_ts")
    private Long endTimestamp;

    public QuayTag() {}

    public QuayTag(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getManifestDigest() {
        return manifestDigest;
    }

    public void setManifestDigest(String manifestDigest) {
        this.manifestDigest = manifestDigest;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getLastModified() {
        return lastModified;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }

    public String getExpiration() {
        return expiration;
    }

    public void setExpiration(String expiration) {
        this.expiration = expiration;
    }

    public Long getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(Long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public Long getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(Long endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    /**
     * Get the effective timestamp for sorting (most recent first).
     */
    public long getSortTimestamp() {
        if (startTimestamp != null) {
            return startTimestamp;
        }
        return 0L;
    }

    @Override
    @SuppressFBWarnings("EQ_COMPARETO_USE_OBJECT_EQUALS")
    public int compareTo(QuayTag other) {
        // Sort by timestamp descending (most recent first)
        return Long.compare(other.getSortTimestamp(), this.getSortTimestamp());
    }

    @Override
    public String toString() {
        return name;
    }
}

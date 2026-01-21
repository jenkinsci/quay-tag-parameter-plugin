package io.jenkins.plugins.quay.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents the response from Quay.io API for listing repository tags.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuayTagResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("tags")
    private List<QuayTag> tags;

    @JsonProperty("page")
    private Integer page;

    @JsonProperty("has_additional")
    private Boolean hasAdditional;

    public QuayTagResponse() {
        this.tags = new ArrayList<>();
    }

    public List<QuayTag> getTags() {
        return tags;
    }

    public void setTags(List<QuayTag> tags) {
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Boolean getHasAdditional() {
        return hasAdditional;
    }

    public void setHasAdditional(Boolean hasAdditional) {
        this.hasAdditional = hasAdditional;
    }
}

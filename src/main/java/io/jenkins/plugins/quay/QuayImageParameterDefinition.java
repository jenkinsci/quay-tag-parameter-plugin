package io.jenkins.plugins.quay;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.quay.model.QuayTag;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

/**
 * Build parameter definition for selecting Quay.io image tags.
 * Provides a dropdown in the Jenkins job configuration to select Docker image tags.
 */
public class QuayImageParameterDefinition extends ParameterDefinition {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(QuayImageParameterDefinition.class.getName());
    private static final int DEFAULT_TAG_LIMIT = 20;

    private final String organization;
    private final String repository;
    private String quayEndpoint;
    private String credentialsId;
    private int tagLimit = DEFAULT_TAG_LIMIT;
    private String defaultTag;

    @DataBoundConstructor
    public QuayImageParameterDefinition(String name, String description, String organization, String repository) {
        super(name, description);
        this.organization = organization;
        this.repository = repository;
    }

    public String getOrganization() {
        return organization;
    }

    public String getRepository() {
        return repository;
    }

    public String getQuayEndpoint() {
        return quayEndpoint != null && !quayEndpoint.trim().isEmpty() ? quayEndpoint : "quay.io";
    }

    @DataBoundSetter
    public void setQuayEndpoint(String quayEndpoint) {
        this.quayEndpoint = quayEndpoint;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public int getTagLimit() {
        return tagLimit;
    }

    @DataBoundSetter
    public void setTagLimit(int tagLimit) {
        this.tagLimit = tagLimit > 0 ? tagLimit : DEFAULT_TAG_LIMIT;
    }

    public String getDefaultTag() {
        return defaultTag;
    }

    @DataBoundSetter
    public void setDefaultTag(String defaultTag) {
        this.defaultTag = defaultTag;
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
        String tag = null;

        // Try to get value from various possible keys
        if (jo.has("value") && !jo.getString("value").isEmpty()) {
            tag = jo.getString("value");
        } else if (jo.has("tag") && !jo.getString("tag").isEmpty()) {
            tag = jo.getString("tag");
        } else if (jo.has(getName()) && !jo.getString(getName()).isEmpty()) {
            tag = jo.getString(getName());
        }

        // Fall back to default if no tag found
        if (tag == null || tag.isEmpty()) {
            tag = defaultTag != null ? defaultTag : "latest";
        }

        String repo = repository;
        if (jo.has("repository") && !jo.getString("repository").trim().isEmpty()) {
            repo = jo.getString("repository").trim();
        }

        return new QuayImageParameterValue(getName(), organization, repo, tag, getQuayEndpoint());
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req) {
        String[] tagValues = req.getParameterValues(getName());
        String[] valueParams = req.getParameterValues("value");
        String[] repoParams = req.getParameterValues("repository");

        String repo = repository;
        if (repoParams != null && repoParams.length > 0 && !repoParams[0].trim().isEmpty()) {
            repo = repoParams[0].trim();
        }

        if (tagValues != null && tagValues.length > 0) {
            return new QuayImageParameterValue(getName(), organization, repo, tagValues[0], getQuayEndpoint());
        }
        if (valueParams != null && valueParams.length > 0) {
            return new QuayImageParameterValue(getName(), organization, repo, valueParams[0], getQuayEndpoint());
        }
        // Return default value if no tag specified
        String tag = defaultTag != null ? defaultTag : "latest";
        return new QuayImageParameterValue(getName(), organization, repo, tag, getQuayEndpoint());
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        String tag = defaultTag != null && !defaultTag.trim().isEmpty() ? defaultTag : "latest";
        return new QuayImageParameterValue(getName(), organization, repository, tag, getQuayEndpoint());
    }

    /**
     * Get available tags for display in the UI dropdown.
     */
    public List<QuayTag> getAvailableTags() {
        try {
            String token = resolveCredentials(credentialsId);
            QuayClient client = QuayClient.getShared(getQuayEndpoint(), token);
            List<QuayTag> tags = new java.util.ArrayList<>(client.getTags(organization, repository, tagLimit));
            tags.sort(java.util.Comparator.comparing(QuayTag::getName, String.CASE_INSENSITIVE_ORDER));
            return tags;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to fetch tags for " + organization + "/" + repository, e);
            return Collections.emptyList();
        }
    }

    private String resolveCredentials(String credentialsId) {
        if (credentialsId == null || credentialsId.trim().isEmpty()) {
            return null;
        }

        StringCredentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItemGroup(
                        StringCredentials.class, Jenkins.get(), ACL.SYSTEM2, Collections.emptyList()),
                CredentialsMatchers.withId(credentialsId));

        return credentials != null ? credentials.getSecret().getPlainText() : null;
    }

    @Symbol("quayImageParameter")
    @Extension
    public static class DescriptorImpl extends ParameterDefinition.ParameterDescriptor {

        @Override
        @NonNull
        public String getDisplayName() {
            return "Quay.io Image Parameter";
        }

        /**
         * Populate credentials dropdown.
         */
        @POST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            StandardListBoxModel model = new StandardListBoxModel();

            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return model.includeCurrentValue(credentialsId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return model.includeCurrentValue(credentialsId);
                }
            }

            model.includeEmptyValue();
            model.includeAs(ACL.SYSTEM2, item, StringCredentials.class);
            return model.includeCurrentValue(credentialsId);
        }

        /**
         * Dynamically fetch tags for the UI dropdown via AJAX.
         */
        @POST
        public ListBoxModel doFillTagItems(
                @AncestorInPath Item item,
                @QueryParameter String quayEndpoint,
                @QueryParameter String organization,
                @QueryParameter String repository,
                @QueryParameter String credentialsId,
                @QueryParameter int tagLimit) {
            ListBoxModel model = new ListBoxModel();

            // Check permissions
            if (item != null && !item.hasPermission(Item.CONFIGURE)) {
                return model;
            }

            if (organization == null
                    || organization.trim().isEmpty()
                    || repository == null
                    || repository.trim().isEmpty()) {
                model.add("-- Enter organization and repository --", "");
                return model;
            }

            try {
                String token = null;
                if (credentialsId != null && !credentialsId.trim().isEmpty()) {
                    StringCredentials credentials = CredentialsMatchers.firstOrNull(
                            CredentialsProvider.lookupCredentialsInItem(
                                    StringCredentials.class, item, ACL.SYSTEM2, Collections.emptyList()),
                            CredentialsMatchers.withId(credentialsId));
                    if (credentials != null) {
                        token = credentials.getSecret().getPlainText();
                    }
                }

                int limit = tagLimit > 0 ? tagLimit : DEFAULT_TAG_LIMIT;
                QuayClient client = QuayClient.getShared(quayEndpoint, token);
                List<QuayTag> tags = client.getTags(organization, repository, limit);

                if (tags.isEmpty()) {
                    model.add("-- No tags found --", "");
                } else {
                    for (QuayTag tag : tags) {
                        model.add(tag.getName(), tag.getName());
                    }
                }
            } catch (QuayClient.QuayApiException e) {
                LOGGER.log(Level.WARNING, "Error fetching tags: " + e.getMessage());
                model.add("-- Error: " + e.getMessage() + " --", "");
            }

            return model;
        }

        /**
         * Build-time AJAX endpoint: returns tags as JSON so the build dialog
         * can refresh its dropdown when the user types a different repository.
         */
        @POST
        public HttpResponse doFetchTagsJson(
                @QueryParameter String quayEndpoint,
                @QueryParameter String organization,
                @QueryParameter String repository,
                @QueryParameter String credentialsId,
                @QueryParameter int tagLimit) {
            Jenkins.get().checkPermission(Jenkins.READ);

            JSONObject result = new JSONObject();
            JSONArray tagsArray = new JSONArray();

            if (organization == null
                    || organization.trim().isEmpty()
                    || repository == null
                    || repository.trim().isEmpty()) {
                result.put("tags", tagsArray);
                result.put("error", "Organization and repository are required");
                return jsonResponse(result);
            }

            try {
                String token = null;
                if (credentialsId != null && !credentialsId.trim().isEmpty()) {
                    StringCredentials credentials = CredentialsMatchers.firstOrNull(
                            CredentialsProvider.lookupCredentialsInItemGroup(
                                    StringCredentials.class, Jenkins.get(), ACL.SYSTEM2, Collections.emptyList()),
                            CredentialsMatchers.withId(credentialsId));
                    if (credentials != null) {
                        token = credentials.getSecret().getPlainText();
                    }
                }

                int limit = tagLimit > 0 ? tagLimit : DEFAULT_TAG_LIMIT;
                QuayClient client = QuayClient.getShared(quayEndpoint, token);
                List<QuayTag> tags = new java.util.ArrayList<>(
                        client.getTags(organization.trim(), repository.trim(), limit));
                tags.sort(java.util.Comparator.comparing(QuayTag::getName, String.CASE_INSENSITIVE_ORDER));
                for (QuayTag t : tags) {
                    tagsArray.add(t.getName());
                }
                result.put("tags", tagsArray);
                result.put("error", JSONNull.getInstance());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error fetching tags for build dialog: " + e.getMessage());
                result.put("tags", tagsArray);
                result.put("error", e.getMessage());
            }
            return jsonResponse(result);
        }

        private static HttpResponse jsonResponse(final JSONObject body) {
            return new HttpResponse() {
                @Override
                public void generateResponse(
                        org.kohsuke.stapler.StaplerRequest2 req,
                        org.kohsuke.stapler.StaplerResponse2 rsp,
                        Object node)
                        throws java.io.IOException {
                    rsp.setContentType("application/json;charset=UTF-8");
                    rsp.getWriter().write(body.toString());
                }
            };
        }

        /**
         * Validate organization name.
         */
        @POST
        public FormValidation doCheckOrganization(@QueryParameter String organization) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            if (organization == null || organization.trim().isEmpty()) {
                return FormValidation.error("Organization is required");
            }
            if (!organization.matches("^[a-zA-Z0-9._-]+$")) {
                return FormValidation.error("Organization contains invalid characters");
            }
            return FormValidation.ok();
        }

        /**
         * Validate repository name.
         */
        @POST
        public FormValidation doCheckRepository(@QueryParameter String repository) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            if (repository == null || repository.trim().isEmpty()) {
                return FormValidation.error("Repository is required");
            }
            if (!repository.matches("^[a-zA-Z0-9._/-]+$")) {
                return FormValidation.error("Repository contains invalid characters");
            }
            return FormValidation.ok();
        }

        /**
         * Test connection to Quay.io repository.
         */
        @POST
        public FormValidation doTestConnection(
                @AncestorInPath Item item,
                @QueryParameter String quayEndpoint,
                @QueryParameter String organization,
                @QueryParameter String repository,
                @QueryParameter String credentialsId) {
            if (item != null) {
                item.checkPermission(Item.CONFIGURE);
            } else {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            }

            if (organization == null || organization.trim().isEmpty()) {
                return FormValidation.error("Organization is required");
            }
            if (repository == null || repository.trim().isEmpty()) {
                return FormValidation.error("Repository is required");
            }

            try {
                String token = null;
                if (credentialsId != null && !credentialsId.trim().isEmpty()) {
                    StringCredentials credentials = CredentialsMatchers.firstOrNull(
                            CredentialsProvider.lookupCredentialsInItem(
                                    StringCredentials.class, item, ACL.SYSTEM2, Collections.emptyList()),
                            CredentialsMatchers.withId(credentialsId));
                    if (credentials != null) {
                        token = credentials.getSecret().getPlainText();
                    }
                }

                QuayClient client = QuayClient.getShared(quayEndpoint, token);
                List<QuayTag> tags = client.getTags(organization, repository, 5);

                return FormValidation.ok("Success! Found " + tags.size() + " tags.");

            } catch (QuayClient.QuayApiException e) {
                return FormValidation.error("Connection failed: " + e.getMessage());
            }
        }
    }
}

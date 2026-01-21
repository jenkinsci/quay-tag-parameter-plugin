package io.jenkins.plugins.quay;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.quay.model.QuayTag;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.CheckForNull;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
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

        return new QuayImageParameterValue(getName(), organization, repository, tag);
    }

    @Override
    public ParameterValue createValue(StaplerRequest req) {
        String[] tagValues = req.getParameterValues(getName());
        String[] valueParams = req.getParameterValues("value");

        if (tagValues != null && tagValues.length > 0) {
            return new QuayImageParameterValue(getName(), organization, repository, tagValues[0]);
        }
        if (valueParams != null && valueParams.length > 0) {
            return new QuayImageParameterValue(getName(), organization, repository, valueParams[0]);
        }
        // Return default value if no tag specified
        String tag = defaultTag != null ? defaultTag : "latest";
        return new QuayImageParameterValue(getName(), organization, repository, tag);
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        String tag = defaultTag != null && !defaultTag.trim().isEmpty() ? defaultTag : "latest";
        return new QuayImageParameterValue(getName(), organization, repository, tag);
    }

    /**
     * Get available tags for display in the UI dropdown.
     */
    public List<QuayTag> getAvailableTags() {
        try {
            String token = resolveCredentials(credentialsId);
            QuayClient client = new QuayClient(token);
            return client.getTags(organization, repository, tagLimit);
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
                CredentialsProvider.lookupCredentials(
                        StringCredentials.class,
                        Jenkins.get(),
                        ACL.SYSTEM,
                        Collections.emptyList()
                ),
                CredentialsMatchers.withId(credentialsId)
        );

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
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item,
                                                      @QueryParameter String credentialsId) {
            StandardListBoxModel model = new StandardListBoxModel();

            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return model.includeCurrentValue(credentialsId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ) &&
                    !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return model.includeCurrentValue(credentialsId);
                }
            }

            model.includeEmptyValue();
            model.includeAs(ACL.SYSTEM, item, StringCredentials.class);
            return model.includeCurrentValue(credentialsId);
        }

        /**
         * Dynamically fetch tags for the UI dropdown via AJAX.
         */
        @POST
        public ListBoxModel doFillTagItems(@AncestorInPath Item item,
                                            @QueryParameter String organization,
                                            @QueryParameter String repository,
                                            @QueryParameter String credentialsId,
                                            @QueryParameter int tagLimit) {
            ListBoxModel model = new ListBoxModel();

            // Check permissions
            if (item != null && !item.hasPermission(Item.CONFIGURE)) {
                return model;
            }

            if (organization == null || organization.trim().isEmpty() ||
                repository == null || repository.trim().isEmpty()) {
                model.add("-- Enter organization and repository --", "");
                return model;
            }

            try {
                String token = null;
                if (credentialsId != null && !credentialsId.trim().isEmpty()) {
                    StringCredentials credentials = CredentialsMatchers.firstOrNull(
                            CredentialsProvider.lookupCredentials(
                                    StringCredentials.class,
                                    item,
                                    ACL.SYSTEM,
                                    Collections.emptyList()
                            ),
                            CredentialsMatchers.withId(credentialsId)
                    );
                    if (credentials != null) {
                        token = credentials.getSecret().getPlainText();
                    }
                }

                int limit = tagLimit > 0 ? tagLimit : DEFAULT_TAG_LIMIT;
                QuayClient client = new QuayClient(token);
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
        public FormValidation doTestConnection(@AncestorInPath Item item,
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
                            CredentialsProvider.lookupCredentials(
                                    StringCredentials.class,
                                    item,
                                    ACL.SYSTEM,
                                    Collections.emptyList()
                            ),
                            CredentialsMatchers.withId(credentialsId)
                    );
                    if (credentials != null) {
                        token = credentials.getSecret().getPlainText();
                    }
                }

                QuayClient client = new QuayClient(token);
                List<QuayTag> tags = client.getTags(organization, repository, 5);

                return FormValidation.ok("Success! Found " + tags.size() + " tags.");

            } catch (QuayClient.QuayApiException e) {
                return FormValidation.error("Connection failed: " + e.getMessage());
            }
        }
    }
}

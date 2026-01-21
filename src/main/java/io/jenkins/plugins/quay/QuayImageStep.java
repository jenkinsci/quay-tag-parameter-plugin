package io.jenkins.plugins.quay;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.AbortException;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.quay.model.QuayTag;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pipeline step for fetching Quay.io image tags.
 *
 * Usage in Jenkinsfile:
 * <pre>
 * def imageRef = quayImage(
 *     organization: 'my-org',
 *     repository: 'my-repo',
 *     credentialsId: 'quay-token',
 *     tag: 'latest'  // optional, defaults to most recent
 * )
 * echo "Image: ${imageRef}"
 * </pre>
 */
public class QuayImageStep extends Step {

    private static final Logger LOGGER = Logger.getLogger(QuayImageStep.class.getName());

    private final String organization;
    private final String repository;
    private String credentialsId;
    private String tag;
    private boolean listTags = false;
    private int tagLimit = 20;

    @DataBoundConstructor
    public QuayImageStep(@NonNull String organization, @NonNull String repository) {
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

    public String getTag() {
        return tag;
    }

    @DataBoundSetter
    public void setTag(String tag) {
        this.tag = tag;
    }

    public boolean isListTags() {
        return listTags;
    }

    @DataBoundSetter
    public void setListTags(boolean listTags) {
        this.listTags = listTags;
    }

    public int getTagLimit() {
        return tagLimit;
    }

    @DataBoundSetter
    public void setTagLimit(int tagLimit) {
        this.tagLimit = tagLimit > 0 ? tagLimit : 20;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new QuayImageStepExecution(this, context);
    }

    /**
     * Step execution implementation.
     */
    private static class QuayImageStepExecution extends SynchronousNonBlockingStepExecution<Object> {

        private static final long serialVersionUID = 1L;

        private final transient QuayImageStep step;

        QuayImageStepExecution(QuayImageStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Object run() throws Exception {
            TaskListener listener = getContext().get(TaskListener.class);
            Run<?, ?> run = getContext().get(Run.class);

            // Resolve credentials
            String token = resolveCredentials(run, step.credentialsId);

            QuayClient client = new QuayClient(token);

            try {
                if (step.listTags) {
                    // Return list of tag names
                    List<QuayTag> tags = client.getTags(step.organization, step.repository, step.tagLimit);
                    listener.getLogger().println("[Quay.io] Found " + tags.size() + " tags for " +
                            step.organization + "/" + step.repository);
                    return tags.stream().map(QuayTag::getName).toArray(String[]::new);
                } else {
                    // Return single image reference
                    String selectedTag = step.tag;

                    if (selectedTag == null || selectedTag.trim().isEmpty()) {
                        // Get the most recent tag
                        List<QuayTag> tags = client.getTags(step.organization, step.repository, 1);
                        if (tags.isEmpty()) {
                            throw new AbortException("No tags found in repository " +
                                    step.organization + "/" + step.repository);
                        }
                        selectedTag = tags.get(0).getName();
                        listener.getLogger().println("[Quay.io] Using most recent tag: " + selectedTag);
                    }

                    String imageRef = QuayClient.buildImageReference(
                            step.organization, step.repository, selectedTag);

                    listener.getLogger().println("[Quay.io] Image reference: " + imageRef);
                    return imageRef;
                }
            } catch (QuayClient.QuayApiException e) {
                LOGGER.log(Level.SEVERE, "Quay API error", e);
                throw new AbortException("[Quay.io] Error: " + e.getMessage());
            }
        }

        private String resolveCredentials(Run<?, ?> run, String credentialsId) {
            if (credentialsId == null || credentialsId.trim().isEmpty()) {
                return null;
            }

            Item item = run.getParent();
            StringCredentials credentials = CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(
                            StringCredentials.class,
                            item,
                            ACL.SYSTEM2,
                            Collections.emptyList()
                    ),
                    CredentialsMatchers.withId(credentialsId)
            );

            if (credentials == null) {
                LOGGER.warning("Credentials not found: " + credentialsId);
                return null;
            }

            return credentials.getSecret().getPlainText();
        }
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "quayImage";
        }

        @Override
        @NonNull
        public String getDisplayName() {
            return "Get Quay.io Image Reference";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Run.class, TaskListener.class);
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }

        /**
         * Populate credentials dropdown for snippet generator.
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
            model.includeAs(ACL.SYSTEM2, item, StringCredentials.class);
            return model.includeCurrentValue(credentialsId);
        }
    }
}

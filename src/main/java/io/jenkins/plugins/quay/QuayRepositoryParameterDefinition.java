package io.jenkins.plugins.quay;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import hudson.security.ACL;
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
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

/**
 * Build parameter definition for picking a Quay.io repository from a dynamically
 * fetched list of repositories under an organization/namespace.
 */
public class QuayRepositoryParameterDefinition extends ParameterDefinition {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(QuayRepositoryParameterDefinition.class.getName());

    private final String organization;
    private String quayEndpoint;
    private String credentialsId;
    private String defaultRepository;

    @DataBoundConstructor
    public QuayRepositoryParameterDefinition(String name, String description, String organization) {
        super(name, description);
        this.organization = organization;
    }

    public String getOrganization() {
        return organization;
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

    public String getDefaultRepository() {
        return defaultRepository;
    }

    @DataBoundSetter
    public void setDefaultRepository(String defaultRepository) {
        this.defaultRepository = defaultRepository;
    }

    /**
     * Called by the Jelly view as a fallback. The async JS path fetches this
     * via doFetchReposJson and populates the dropdown after page load.
     */
    public List<String> getAvailableRepositories() {
        try {
            String token = resolveCredentials(credentialsId);
            QuayClient client = QuayClient.getShared(getQuayEndpoint(), token);
            return client.getRepositories(organization);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to fetch repositories for " + organization, e);
            return Collections.emptyList();
        }
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
        String repo = null;
        if (jo.has("value") && !jo.getString("value").isEmpty()) {
            repo = jo.getString("value");
        } else if (jo.has(getName()) && !jo.getString(getName()).isEmpty()) {
            repo = jo.getString(getName());
        }
        if (repo == null || repo.isEmpty()) {
            repo = defaultRepository != null ? defaultRepository : "";
        }
        return new StringParameterValue(getName(), repo, getDescription());
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req) {
        String[] values = req.getParameterValues(getName());
        String repo = (values != null && values.length > 0) ? values[0] : defaultRepository;
        return new StringParameterValue(getName(), repo != null ? repo : "", getDescription());
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        String repo = defaultRepository != null ? defaultRepository : "";
        return new StringParameterValue(getName(), repo, getDescription());
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

    @Symbol("quayRepositoryParameter")
    @Extension
    public static class DescriptorImpl extends ParameterDefinition.ParameterDescriptor {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Quay.io Repository Parameter";
        }

        /**
         * Build-time AJAX endpoint: returns repositories under an organization as JSON.
         */
        @POST
        public HttpResponse doFetchReposJson(
                @QueryParameter String quayEndpoint,
                @QueryParameter String organization,
                @QueryParameter String credentialsId) {
            Jenkins.get().checkPermission(Jenkins.READ);

            JSONObject result = new JSONObject();
            JSONArray reposArray = new JSONArray();

            if (organization == null || organization.trim().isEmpty()) {
                result.put("repositories", reposArray);
                result.put("error", "Organization is required");
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
                QuayClient client = QuayClient.getShared(quayEndpoint, token);
                List<String> repos = client.getRepositories(organization.trim());
                for (String name : repos) {
                    reposArray.add(name);
                }
                result.put("repositories", reposArray);
                result.put("error", JSONNull.getInstance());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error fetching repositories: " + e.getMessage());
                result.put("repositories", reposArray);
                result.put("error", e.getMessage());
            }
            return jsonResponse(result);
        }

        private static HttpResponse jsonResponse(final JSONObject body) {
            return new HttpResponse() {
                @Override
                public void generateResponse(
                        org.kohsuke.stapler.StaplerRequest2 req, org.kohsuke.stapler.StaplerResponse2 rsp, Object node)
                        throws java.io.IOException {
                    rsp.setContentType("application/json;charset=UTF-8");
                    rsp.getWriter().write(body.toString());
                }
            };
        }
    }
}

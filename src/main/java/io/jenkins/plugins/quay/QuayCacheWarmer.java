package io.jenkins.plugins.quay;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.security.ACL;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/**
 * Pre-warms the QuayClient cache at Jenkins startup by scanning jobs that have
 * a Quay parameter configured and firing a background fetch for each unique
 * (endpoint, organization, repository) tuple. Pipeline-only parameters defined
 * inline in a Jenkinsfile cannot be discovered here and are not pre-warmed.
 */
public final class QuayCacheWarmer {

    private static final Logger LOGGER = Logger.getLogger(QuayCacheWarmer.class.getName());

    private QuayCacheWarmer() {}

    @Initializer(after = InitMilestone.JOB_LOADED)
    public static void warmCache() {
        Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "quay-cache-warmer");
                    t.setDaemon(true);
                    return t;
                })
                .submit(QuayCacheWarmer::doWarm);
    }

    private static void doWarm() {
        try {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) return;

            for (Job<?, ?> job : jenkins.getAllItems(Job.class)) {
                ParametersDefinitionProperty prop = job.getProperty(ParametersDefinitionProperty.class);
                if (prop == null) continue;
                for (ParameterDefinition def : prop.getParameterDefinitions()) {
                    try {
                        if (def instanceof QuayImageParameterDefinition) {
                            QuayImageParameterDefinition q = (QuayImageParameterDefinition) def;
                            warmTags(q);
                        } else if (def instanceof QuayRepositoryParameterDefinition) {
                            QuayRepositoryParameterDefinition q = (QuayRepositoryParameterDefinition) def;
                            warmRepos(q);
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.FINE, "Warmup skipped for parameter: " + e.getMessage());
                    }
                }
            }
            LOGGER.fine("Quay cache warmup complete");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Quay cache warmup failed: " + e.getMessage());
        }
    }

    private static void warmTags(QuayImageParameterDefinition q) {
        String token = resolveToken(q.getCredentialsId());
        QuayClient client = QuayClient.getShared(q.getQuayEndpoint(), token);
        try {
            client.getTags(q.getOrganization(), q.getRepository(), q.getTagLimit());
        } catch (Exception ignored) {
        }
    }

    private static void warmRepos(QuayRepositoryParameterDefinition q) {
        String token = resolveToken(q.getCredentialsId());
        QuayClient client = QuayClient.getShared(q.getQuayEndpoint(), token);
        try {
            client.getRepositories(q.getOrganization());
        } catch (Exception ignored) {
        }
    }

    private static String resolveToken(String credentialsId) {
        if (credentialsId == null || credentialsId.trim().isEmpty()) return null;
        try {
            StringCredentials credentials = CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentialsInItemGroup(
                            StringCredentials.class, Jenkins.get(), ACL.SYSTEM2, Collections.emptyList()),
                    CredentialsMatchers.withId(credentialsId));
            return credentials != null ? credentials.getSecret().getPlainText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

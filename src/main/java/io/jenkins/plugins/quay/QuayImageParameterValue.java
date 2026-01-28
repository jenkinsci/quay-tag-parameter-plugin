package io.jenkins.plugins.quay;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.util.VariableResolver;
import java.util.Objects;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Represents the value of a QuayImageParameter.
 * Contains the selected tag and provides the full image reference as an environment variable.
 */
public class QuayImageParameterValue extends ParameterValue {

    private static final long serialVersionUID = 1L;

    private final String organization;
    private final String repository;
    private final String tag;

    @DataBoundConstructor
    public QuayImageParameterValue(String name, String organization, String repository, String tag) {
        super(name);
        this.organization = organization;
        this.repository = repository;
        this.tag = tag;
    }

    public String getOrganization() {
        return organization;
    }

    public String getRepository() {
        return repository;
    }

    public String getTag() {
        return tag;
    }

    /**
     * Get the full image reference (e.g., quay.io/org/repo:tag)
     */
    public String getImageReference() {
        return QuayClient.buildImageReference(organization, repository, tag);
    }

    @Override
    public Object getValue() {
        return getImageReference();
    }

    @Override
    public void buildEnvironment(Run<?, ?> build, EnvVars env) {
        String paramName = getName();

        // Main variable: full image reference
        env.put(paramName, getImageReference());

        // Additional variables for flexibility
        env.put(paramName + "_ORG", organization);
        env.put(paramName + "_REPO", repository);
        env.put(paramName + "_TAG", tag);
        env.put(paramName + "_FULL_REPO", "quay.io/" + organization + "/" + repository);
    }

    @Override
    public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
        return name -> {
            String paramName = getName();
            if (paramName.equals(name)) {
                return getImageReference();
            }
            if ((paramName + "_ORG").equals(name)) {
                return organization;
            }
            if ((paramName + "_REPO").equals(name)) {
                return repository;
            }
            if ((paramName + "_TAG").equals(name)) {
                return tag;
            }
            if ((paramName + "_FULL_REPO").equals(name)) {
                return "quay.io/" + organization + "/" + repository;
            }
            return null;
        };
    }

    @Override
    public String getShortDescription() {
        return getName() + "=" + getImageReference();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        QuayImageParameterValue that = (QuayImageParameterValue) o;
        return Objects.equals(organization, that.organization)
                && Objects.equals(repository, that.repository)
                && Objects.equals(tag, that.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), organization, repository, tag);
    }

    @Override
    public String toString() {
        return "QuayImageParameterValue{" + "name='"
                + getName() + '\'' + ", organization='"
                + organization + '\'' + ", repository='"
                + repository + '\'' + ", tag='"
                + tag + '\'' + '}';
    }
}

# Quay.io Image Plugin for Jenkins

A Jenkins plugin that integrates with Quay.io to fetch and select Docker image tags from Quay repositories. Supports both Freestyle jobs (via build parameters) and Pipeline jobs (via the `quayImage` step).

## Features

- **Build Parameter**: `QuayImageParameter` - Dropdown selection of Quay.io image tags
- **Pipeline Step**: `quayImage()` - Fetch image references in Jenkinsfiles
- **Public & Private Repos**: Support for both public repositories and private repos via robot tokens
- **Dynamic Tag Fetching**: AJAX-powered tag dropdown with real-time updates
- **Caching**: 5-minute cache for API responses to reduce load
- **Secure**: Uses Jenkins Credentials API, never exposes tokens in logs

## Requirements

- Jenkins LTS 2.387.3 or newer
- Java 11 or newer
- Credentials Plugin (bundled with Jenkins)
- Plain Credentials Plugin (bundled with Jenkins)

## Installation

### From Source

```bash
# Clone or download the plugin source
cd quay-image-plugin

# Build the plugin
mvn clean package

# The .hpi file will be at: target/quay-image-plugin.hpi
```

### Install in Jenkins

1. Go to **Manage Jenkins → Plugins → Advanced settings**
2. Under **Deploy Plugin**, upload `quay-image-plugin.hpi`
3. Restart Jenkins

Or copy directly:
```bash
cp target/quay-image-plugin.hpi $JENKINS_HOME/plugins/
# Restart Jenkins
```

## Configuration

### Setting Up Credentials (for Private Repos)

1. Go to **Manage Jenkins → Credentials**
2. Add a new credential:
   - **Kind**: Secret text
   - **Secret**: Your Quay.io robot token
   - **ID**: e.g., `quay-robot-token`
   - **Description**: Quay.io Robot Token

#### Getting a Robot Token from Quay.io

1. Log in to [quay.io](https://quay.io)
2. Go to your organization → **Robot Accounts**
3. Create a new robot account
4. Grant read access to your repository
5. Copy the robot token

## Usage

### Freestyle Job - Build Parameter

1. Create or configure a Freestyle job
2. Check **This project is parameterized**
3. Add parameter → **Quay.io Image Parameter**
4. Configure:
   - **Name**: `QUAY_IMAGE` (or any name)
   - **Organization**: Your Quay.io org (e.g., `mycompany`)
   - **Repository**: Repository name (e.g., `myapp`)
   - **Credentials**: Select your robot token (optional for public repos)
   - **Tag Limit**: Number of tags to show (default: 20)
   - **Default Tag**: Fallback tag (default: `latest`)

5. Click **Test Connection** to verify

#### Environment Variables

During the build, these environment variables are available:

| Variable | Example Value |
|----------|---------------|
| `QUAY_IMAGE` | `quay.io/mycompany/myapp:v1.2.3` |
| `QUAY_IMAGE_ORG` | `mycompany` |
| `QUAY_IMAGE_REPO` | `myapp` |
| `QUAY_IMAGE_TAG` | `v1.2.3` |
| `QUAY_IMAGE_FULL_REPO` | `quay.io/mycompany/myapp` |

### Pipeline - Jenkinsfile

#### Get Image Reference (Most Recent Tag)

```groovy
pipeline {
    agent any
    stages {
        stage('Get Image') {
            steps {
                script {
                    def imageRef = quayImage(
                        organization: 'my-org',
                        repository: 'my-repo',
                        credentialsId: 'quay-robot-token'
                    )
                    echo "Using image: ${imageRef}"
                    // Output: quay.io/my-org/my-repo:latest-tag-name
                }
            }
        }
    }
}
```

#### Get Specific Tag

```groovy
def imageRef = quayImage(
    organization: 'my-org',
    repository: 'my-repo',
    tag: 'v1.0.0'
)
// Output: quay.io/my-org/my-repo:v1.0.0
```

#### List Available Tags

```groovy
def tags = quayImage(
    organization: 'my-org',
    repository: 'my-repo',
    listTags: true,
    tagLimit: 10
)

tags.each { tag ->
    echo "Available tag: ${tag}"
}
```

#### Public Repository (No Credentials)

```groovy
def imageRef = quayImage(
    organization: 'coreos',
    repository: 'etcd'
)
```

### Pipeline with Build Parameter

```groovy
pipeline {
    agent any
    parameters {
        quayImageParameter(
            name: 'DEPLOY_IMAGE',
            description: 'Select image to deploy',
            organization: 'mycompany',
            repository: 'myapp',
            credentialsId: 'quay-robot-token',
            tagLimit: 20,
            defaultTag: 'latest'
        )
    }
    stages {
        stage('Deploy') {
            steps {
                echo "Deploying: ${params.DEPLOY_IMAGE}"
                sh "docker pull ${params.DEPLOY_IMAGE}"
            }
        }
    }
}
```

## Pipeline Step Reference

### `quayImage`

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `organization` | String | Yes | - | Quay.io organization/namespace |
| `repository` | String | Yes | - | Repository name |
| `credentialsId` | String | No | - | Jenkins credential ID for private repos |
| `tag` | String | No | (most recent) | Specific tag to use |
| `listTags` | Boolean | No | `false` | Return array of tag names |
| `tagLimit` | Integer | No | `20` | Max tags when listTags=true |

**Returns:**
- If `listTags=false`: String with full image reference (e.g., `quay.io/org/repo:tag`)
- If `listTags=true`: String array of tag names

## Troubleshooting

### "Authentication failed"
- Verify your robot token is correct
- Check the token hasn't expired
- Ensure the credential is a "Secret text" type

### "Access denied"
- Verify the robot account has read access to the repository
- Check the organization and repository names are correct

### "Repository not found"
- Verify the organization name (case-sensitive)
- Verify the repository name
- For private repos, ensure credentials are configured

### "Rate limit exceeded"
- Wait a few minutes and try again
- Quay.io has API rate limits

### Tags not updating
- Tags are cached for 5 minutes
- Wait for cache to expire or restart Jenkins

## Development

### Build

```bash
mvn clean package
```

### Run Local Jenkins with Plugin

```bash
mvn hpi:run
```

Then open http://localhost:8080/jenkins/

### Run Tests

```bash
mvn test
```

## Architecture

```
src/main/java/io/jenkins/plugins/quay/
├── QuayClient.java              # Quay.io API client with caching
├── QuayImageParameterDefinition.java  # Build parameter definition
├── QuayImageParameterValue.java       # Parameter value container
├── QuayImageStep.java                 # Pipeline step
└── model/
    ├── QuayTag.java             # Tag model
    └── QuayTagResponse.java     # API response model

src/main/resources/io/jenkins/plugins/quay/
├── QuayImageParameterDefinition/
│   ├── config.jelly             # Parameter configuration UI
│   ├── index.jelly              # Build-time parameter UI
│   ├── help.jelly               # General help
│   └── help-*.html              # Field-specific help
└── QuayImageStep/
    ├── config.jelly             # Pipeline snippet generator UI
    └── help.html                # Step documentation
```

## Security

- API tokens are stored using Jenkins Credentials API
- Tokens are never logged or exposed in build output
- All API calls use HTTPS
- Input validation prevents injection attacks

## License

MIT License - see LICENSE file for details.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests: `mvn test`
5. Submit a pull request

## Support

For issues and feature requests, please use the GitHub issue tracker.

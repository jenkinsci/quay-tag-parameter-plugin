# Contributing to Quay Tag Parameter Plugin

Thank you for your interest in contributing to this Jenkins plugin!

## Development Setup

### Prerequisites

- Java 17 or newer
- Maven 3.8+
- Git

### Build

```bash
mvn clean package
```

The `.hpi` file will be generated at `target/quay-tag-parameter.hpi`.

### Run Local Jenkins with Plugin

```bash
mvn hpi:run
```

Then open http://localhost:8080/jenkins/

### Run Tests

```bash
mvn test
```

### Code Style

This project uses Spotless for code formatting. Run before committing:

```bash
mvn spotless:apply
```

## Architecture

```
src/main/java/io/jenkins/plugins/quay/
├── QuayClient.java                    # Quay.io API client with caching
├── QuayImageParameterDefinition.java  # Build parameter definition
├── QuayImageParameterValue.java       # Parameter value container
├── QuayImageStep.java                 # Pipeline step
└── model/
    ├── QuayTag.java                   # Tag model
    └── QuayTagResponse.java           # API response model

src/main/resources/io/jenkins/plugins/quay/
├── QuayImageParameterDefinition/
│   ├── config.jelly                   # Parameter configuration UI
│   ├── index.jelly                    # Build-time parameter UI
│   ├── help.jelly                     # General help
│   └── help-*.html                    # Field-specific help
└── QuayImageStep/
    ├── config.jelly                   # Pipeline snippet generator UI
    └── help.html                      # Step documentation
```

## Submitting Changes

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Make your changes
4. Run tests: `mvn test`
5. Run code style check: `mvn spotless:check`
6. Commit your changes
7. Push to your fork
8. Submit a pull request

## Reporting Issues

Please use the GitHub issue tracker for bug reports and feature requests.

When reporting bugs, please include:
- Jenkins version
- Plugin version
- Steps to reproduce
- Expected vs actual behavior
- Relevant log output

# Contributing to AuditFlow

Thank you for your interest in contributing to AuditFlow! This document provides guidelines for contributing code, reporting bugs, and suggesting features.

## Code of Conduct

Please note that this project is released with a [Code of Conduct](CODE_OF_CONDUCT.md). By participating in this project you agree to abide by its terms.

## Reporting Bugs

Before creating bug reports, please check the issue list as you might find out that you don't need to create one. When you are creating a bug report, please include as many details as possible:

- Use a clear and descriptive title
- Describe the exact steps which reproduce the problem
- Provide specific examples to demonstrate the steps
- Describe the behavior you observed after following the steps
- Explain which behavior you expected to see instead and why
- Include screenshots if possible
- Include your Jenkins version and plugin version

## Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, please include:

- Use a clear and descriptive title
- Provide a step-by-step description of the suggested enhancement
- Provide specific examples to demonstrate the steps
- Describe the current behavior and the expected behavior
- Include screenshots and animated GIFs if applicable
- Explain why this enhancement would be useful

## Pull Requests

- Follow the existing code style
- Add unit tests for any new functionality
- Ensure all tests pass: `mvnw clean package`
- Update the README.md if applicable
- End all files with a newline

## Development Setup

1. Fork the repository
2. Clone your fork: `git clone https://github.com/YOUR-USERNAME/auditflow-plugin.git`
3. Create a feature branch: `git checkout -b feature/your-feature-name`
4. Make your changes
5. Run tests: `mvnw clean package`
6. Commit your changes: `git commit -am 'Add your feature'`
7. Push to the branch: `git push origin feature/your-feature-name`
8. Create a Pull Request

## Commit Messages

- Use the present tense ("Add feature" not "Added feature")
- Use the imperative mood ("Move cursor to..." not "Moves cursor to...")
- Limit the first line to 72 characters or less
- Reference issues and pull requests liberally after the first line

## Jenkins Plugin Development Guidelines

This plugin follows the [Jenkins Plugin Development Guidelines](https://www.jenkins.io/doc/developer/plugin-development/).

For information on publishing the plugin, see [Publishing Plugins](https://www.jenkins.io/doc/developer/publishing/).

## Questions?

Feel free to open an issue for questions or reach out to the Jenkins community on their [mailing lists](https://www.jenkins.io/mailing-lists/) or [chat channels](https://www.jenkins.io/chat/).

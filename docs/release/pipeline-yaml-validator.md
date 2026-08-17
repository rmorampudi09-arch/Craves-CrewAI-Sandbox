# Pipeline YAML validator

Validates every Azure DevOps and GitHub Actions YAML file before functional CI begins.

Checks:
- YAML parses successfully.
- Tabs are rejected.
- Azure pipeline files contain steps, jobs, or stages.
- Unexpected automatic triggers are rejected for manual-rollout pipelines.

Run `azure-pipelines-release-pipeline-yaml-validator.yml`. It performs no repository, Azure, APIM, database, or application mutation.

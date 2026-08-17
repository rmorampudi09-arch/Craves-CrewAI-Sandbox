# Rollback readiness gate

Inspects deployment-capable Azure pipeline YAML files and requires:

- immutable image tags rather than `latest`;
- visible capture of the currently deployed Container App image before replacement;
- a matching rollback pipeline or explicit rollback stage.

The module is static and read-only. It does not deploy or roll back anything. A failure means the affected deployment pipeline must be amended before rollout.

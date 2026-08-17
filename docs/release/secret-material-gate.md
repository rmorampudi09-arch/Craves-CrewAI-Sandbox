# Secret-material gate

Scans tracked text files for private keys, hard-coded bearer tokens, connection strings, access keys and common cloud API-key formats.

The scanner deliberately excludes generated/binary directories and never prints the suspected value. Findings must be removed and affected credentials rotated before rollout.

Run `azure-pipelines-release-secret-material-gate.yml`. It is read-only and does not access Azure Key Vault or any runtime secret store.

# Immutable release manifest

Generates a JSON artifact containing the exact commit, Git tree, creation time, clean/dirty state, and SHA-256 hashes for release-critical inputs such as POMs, package files, Dockerfiles, pipelines, migrations, infrastructure assets, and release scripts.

The manifest contains paths, sizes, and hashes only—never file contents or secret values. It is published as a pipeline artifact and does not modify the repository or any runtime environment.

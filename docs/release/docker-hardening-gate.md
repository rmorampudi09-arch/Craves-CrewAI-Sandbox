# Docker hardening gate

Checks every Dockerfile for a pinned base-image tag, explicit non-root user, command/entrypoint, absence of secret-like build arguments, and absence of remote `ADD` downloads.

The module performs static source validation only. It does not build, push, scan, deploy, or delete an image.

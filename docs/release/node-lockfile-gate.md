# Node lockfile and npm-ci gate

Requires every application under `apps/**/package.json` to contain a reviewed `package-lock.json` and pass `npm ci --ignore-scripts`.

When present, the module's `typecheck`, `test`, and `build` scripts are executed. Missing lockfiles are an intentional hard failure and must be resolved before web or mobile deployment.

This pipeline performs dependency installation and local compilation only. It does not publish packages, images, applications, or Azure resources.

# Craves stacked PR validator

## Purpose

Prevents out-of-order rollout of the large stacked feature set. It checks that each PR in a supplied range exists, remains open and draft, is not explicitly unmergeable, and targets the immediately preceding feature branch.

## Files

- `scripts/release/validate-stacked-pr-chain.sh`
- `azure-pipelines-release-pr-stack-validator.yml`

## Manual input

Create secret pipeline variable `GITHUB_TOKEN_READONLY` with read-only repository metadata permission. Do not paste the token into chat or source control.

## Execution

Run this pipeline before any functional CI. Set the PR range to the exact rollout batch. The validator performs no merge, deployment or repository mutation.

## Failure handling

A failure means the stack must be repaired before CI/deployment begins. Do not bypass the check by changing the script or skipping a missing PR without recording the reason.

## Security

The token is supplied only through the pipeline environment. The checkout does not persist credentials. The script calls only GitHub read APIs.

## Current status

Code-only. Not executed. No GitHub, Azure, APIM or application state was changed by this module.

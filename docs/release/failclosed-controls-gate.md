# Fail-closed execution controls

Validates both source defaults and the deployed Integration Service environment.

Required runtime state:
- delivery command disabled
- create reconciliation disabled
- webhook processing disabled
- tracking reconciliation disabled
- status publisher disabled
- Borzo API disabled
- delivery intelligence enabled

The pipeline uses Azure read operations only. It never changes an environment variable or creates a revision.

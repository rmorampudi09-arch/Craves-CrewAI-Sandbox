# Observability baseline gate

Checks each Spring service for visible Actuator health/probe configuration and verifies that the Azure Container Apps managed environment sends application logs to Log Analytics or Azure Monitor.

The Azure portion is read-only. It does not create workspaces, diagnostic settings, alerts, dashboards, or retention policies. Any paid observability expansion remains a later manual decision.

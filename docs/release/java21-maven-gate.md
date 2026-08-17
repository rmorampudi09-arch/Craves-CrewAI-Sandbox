# Java 21 and Maven release gate

Runs `mvn verify` for every Spring service under `services/*/pom.xml` using Java 21.

The gate also requires each POM to explicitly target Java 21. A failure identifies the exact service and blocks rollout. It does not build or push a container image and does not access Azure.

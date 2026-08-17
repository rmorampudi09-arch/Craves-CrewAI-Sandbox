FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system --gid 10001 craves \
    && useradd --system --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin craves
COPY --from=builder --chown=10001:10001 /workspace/target/catalog-service-*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

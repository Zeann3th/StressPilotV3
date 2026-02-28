FROM maven:4.0.0-rc-5-eclipse-temurin-25 AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

ARG APM_VERSION=1.55.4

COPY lib/elastic-apm-agent-${APM_VERSION}.jar /app/elastic-apm-agent.jar

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
            "-javaagent:/app/elastic-apm-agent.jar", \
            "-Delastic.apm.service_name=stresspilot", \
            "-Delastic.apm.application_packages=dev.zeann3th.stresspilot", \
            "-Delastic.apm.server_urls=${APM_URL:-http://host.docker.internal:8200}", \
            "-Delastic.apm.log_sending=true", \
            "-jar", "app.jar"]
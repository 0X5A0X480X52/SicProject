FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY platform ./platform
COPY services ./services
# Every Compose image has the same source tree. Building the reactor in one
# identical layer lets BuildKit reuse it for every service image, while the
# cache mount keeps downloaded Maven artifacts across source-only rebuilds.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -Dmaven.test.skip=true package

FROM build AS selected-artifact
ARG MODULE
RUN artifact="$(find "/workspace/${MODULE}/target" -maxdepth 1 -type f -name '*-exec.jar' -print -quit)" \
    && test -n "${artifact}" \
    && cp "${artifact}" /application.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /application
COPY --from=selected-artifact /application.jar application.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/application/application.jar"]

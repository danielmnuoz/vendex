# Multi-stage: build the fat jar with Maven, then run it on a slim JRE image.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Resolve dependencies first so they cache across source-only changes.
COPY pom.xml .
COPY proto/pom.xml proto/pom.xml
COPY services/auth/pom.xml services/auth/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    apt-get update && apt-get install -y --no-install-recommends maven && \
    mvn -B -pl services/auth -am dependency:go-offline -DskipTests || true

# Build.
COPY proto/src proto/src
COPY services/auth/src services/auth/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl services/auth -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/auth/target/auth-exec.jar /app/auth.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "/app/auth.jar"]

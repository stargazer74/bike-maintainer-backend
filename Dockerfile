# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jre-noble
WORKDIR /app

# The base image often lags behind Ubuntu's security fixes; pull them in at build time (as root, before USER).
RUN apt-get update \
    && apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

COPY target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

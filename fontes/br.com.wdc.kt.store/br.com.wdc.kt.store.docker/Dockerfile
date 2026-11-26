# The WDC Store backend, from what Gradle built (./prepare.sh stages it in build/context): the backend's distribution
# (installDist, with the TypeScript remote client inside) and the deployed web clients. No JDK, Node or Android SDK here.
FROM eclipse-temurin:21.0.13_9-jre-noble

RUN useradd --system --create-home --home-dir /work store

COPY build/context/backend /app
COPY build/context/deployment /work/deployment
COPY build/context/logback.xml /work/config/logback.xml
COPY entrypoint.sh /app/entrypoint.sh

RUN mkdir -p /work/data /work/log /work/tmp && chown -R store:store /work && chmod +x /app/entrypoint.sh

USER store
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]

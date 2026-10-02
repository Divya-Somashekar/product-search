# Which module's boot jar to package. `.` is the single-process assembly used for local
# development; `catalog-app` and `search-app` are the two deployable services.
#   docker build --build-arg MODULE=catalog-app -t catalog-service:dev .
ARG MODULE=.

FROM eclipse-temurin:25-jre AS extract
ARG MODULE
WORKDIR /build
COPY ${MODULE}/build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app app
WORKDIR /app
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./
USER 10001
EXPOSE 8080 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
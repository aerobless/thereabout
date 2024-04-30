FROM openjdk:21-jdk AS builder

WORKDIR /app

COPY backend/target/thereabout-backend*.jar ./thereabout.jar

RUN java -Djarmode=layertools -jar ./thereabout.jar extract

FROM openjdk:21-jdk

WORKDIR /app

COPY --from=builder app/dependencies ./
COPY --from=builder app/spring-boot-loader ./
COPY --from=builder app/snapshot-dependencies ./
COPY --from=builder app/application ./

COPY frontend/dist/thereabout/browser /frontend

EXPOSE 9050

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
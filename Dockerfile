FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app

COPY backend/target/thereabout-backend*.jar ./thereabout.jar

RUN java -Djarmode=tools -jar ./thereabout.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jdk

# Download and extract dockerize directly using the ADD command
ARG TARGETARCH
ENV DOCKERIZE_VERSION=v0.14.0
ADD https://github.com/jwilder/dockerize/releases/download/$DOCKERIZE_VERSION/dockerize-linux-$TARGETARCH-$DOCKERIZE_VERSION.tar.gz /usr/local/bin/dockerize.tar.gz
RUN tar -C /usr/local/bin -xzf /usr/local/bin/dockerize.tar.gz && rm /usr/local/bin/dockerize.tar.gz

WORKDIR /app

COPY --from=builder app/extracted/dependencies ./
COPY --from=builder app/extracted/spring-boot-loader ./
COPY --from=builder app/extracted/snapshot-dependencies ./
COPY --from=builder app/extracted/application ./

COPY frontend/dist/thereabout/browser /frontend

EXPOSE 9050

# Set the entrypoint to use dockerize to wait for the MariaDB service
ENTRYPOINT ["sh", "-c", "dockerize -wait tcp://mariadb:3306 -timeout 60s && java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

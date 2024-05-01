FROM openjdk:21-jdk AS builder

WORKDIR /app

COPY backend/target/thereabout-backend*.jar ./thereabout.jar

RUN java -Djarmode=layertools -jar ./thereabout.jar extract

FROM openjdk:21-jdk

# Download and extract dockerize directly using the ADD command
ENV DOCKERIZE_VERSION v0.7.0
ADD https://github.com/jwilder/dockerize/releases/download/$DOCKERIZE_VERSION/dockerize-linux-amd64-$DOCKERIZE_VERSION.tar.gz /usr/local/bin/
RUN tar -C /usr/local/bin -xzf /usr/local/bin/dockerize-linux-amd64-$DOCKERIZE_VERSION.tar.gz && rm /usr/local/bin/dockerize-linux-amd64-$DOCKERIZE_VERSION.tar.gz

WORKDIR /app

COPY --from=builder app/dependencies ./
COPY --from=builder app/spring-boot-loader ./
COPY --from=builder app/snapshot-dependencies ./
COPY --from=builder app/application ./

COPY frontend/dist/thereabout/browser /frontend

EXPOSE 9050

# Set the entrypoint to use dockerize to wait for the MariaDB service
ENTRYPOINT ["sh", "-c", "dockerize -wait tcp://mariadb:3306 -timeout 60s && java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
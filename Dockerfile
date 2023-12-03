FROM maven:3-amazoncorretto-21 AS BUILD_IMAGE

ARG MVN_CACHE_DIR=/root/.m2
ENV APP_HOME=/app
WORKDIR $APP_HOME
COPY . $APP_HOME/
RUN --mount=type=cache,target=$MVN_CACHE_DIR mvn -f $APP_HOME/pom.xml clean package

#Build core image
FROM maven:3-amazoncorretto-21 as RUN_IMAGE
ENV JAVA_OPTS="--enable-preview"
ENV APP_HOME=/app
WORKDIR $APP_HOME
COPY --from=BUILD_IMAGE /app/target/knx2mqtt_to_homekit2mqtt.jar /app/
VOLUME ["/data"]
CMD java $JAVA_OPTS -jar knx2mqtt_to_homekit2mqtt.jar

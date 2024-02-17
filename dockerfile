FROM gradle:jdk21-graal as gradle

COPY ./ ./

RUN gradle fatJar

FROM findepi/graalvm:java21

WORKDIR /znatokiBot

COPY --from=gradle /home/gradle/build/libs/checkPassportBot-1.0-SNAPSHOT-standalone.jar .

RUN apt-get update && apt-get install -y --no-install-recommends procps

CMD ["java", "-jar", "checkPassportBot-1.0-SNAPSHOT-standalone.jar", "--add-opens java.base/java.net=ALL-UNNAMED", "--add-opens java.base/sun.net.www.protocol.https=ALL-UNNAMED" ]

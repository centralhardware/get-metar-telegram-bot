FROM maven:3.6.3-openjdk-15 as maven

COPY ./pom.xml ./pom.xml

RUN mvn dependency:go-offline -B

COPY ./src ./src

RUN mvn package

FROM openjdk:15-alpine

COPY --from=maven target/unbbGetMetarInfoBot-1.0-SNAPSHOT.jar .

CMD ["java", "-jar", "unbbGetMetarInfoBot-1.0-SNAPSHOT.jar" ]
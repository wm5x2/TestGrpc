FROM openjdk:16-jdk-alpine
ARG JAR_FILE=target/*.jar
#CMD ls
#CMD pwd
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
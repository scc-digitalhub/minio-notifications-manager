# syntax=docker/dockerfile:experimental
FROM maven:3-jdk-8 as mvn
COPY src /tmp/src
COPY pom.xml /tmp/pom.xml
WORKDIR /tmp
RUN apt-get update && apt-get install -y libxml2-utils
##RUN mvn -Dexec.executable='echo' -Dexec.args='${project.artifactId}' --non-recursive exec:exec -q > .version
RUN xmllint --xpath "//*[local-name()='project']/*[local-name()='artifactId']/text()" pom.xml > .artifact && \
	xmllint --xpath "//*[local-name()='project']/*[local-name()='version']/text()" pom.xml > .version 	
RUN mvn package -DskipTests

FROM adoptopenjdk/openjdk8:alpine
ARG ARTIFACT=minio-notifications-manager
ARG VER=0.1
ARG BRANCH="master"
ARG COMMIT=""
ARG USER=spring
ARG USER_ID=805
ARG USER_GROUP=spring
ARG USER_GROUP_ID=805
ARG USER_HOME=/home/${USER}
ENV APP=${ARTIFACT}.jar
LABEL branch=${BRANCH}
LABEL commit=${COMMIT}
# create a user group and a user
RUN  addgroup -g ${USER_GROUP_ID} ${USER_GROUP}; \
     adduser -u ${USER_ID} -D -g '' -h ${USER_HOME} -G ${USER_GROUP} ${USER};

WORKDIR ${USER_HOME}
COPY --chown=spring:spring --from=mvn /tmp/.artifact /tmp/.version ${USER_HOME}/
COPY --chown=spring:spring --from=mvn /tmp/target/*.jar ${USER_HOME}/${APP}
USER spring
EXPOSE 8080
ENTRYPOINT java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -jar ${APP}

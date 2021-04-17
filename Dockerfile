FROM adoptopenjdk/openjdk16:alpine as builder

LABEL maintainer="https://jbake.org/community/team.html"

ENV JBAKE_HOME=/opt/jbake

RUN mkdir -p ${JBAKE_HOME}

COPY . /usr/src/jbake

RUN cd /usr/src/jbake && ls -la && ./gradlew installDist && cp -r jbake-dist/build/install/jbake/* $JBAKE_HOME && \
    rm -r ~/.gradle /usr/src/jbake

# Image Stage
FROM adoptopenjdk/openjdk16:alpine-jre

ENV JBAKE_USER=jbake
ENV JBAKE_HOME=/opt/jbake
ENV PATH ${JBAKE_HOME}/bin:$PATH

RUN adduser -D -g "" ${JBAKE_USER} ${JBAKE_USER}

USER ${JBAKE_USER}

COPY --from=builder /opt/jbake /opt/jbake

WORKDIR /mnt/site

VOLUME ["/mnt/site"]

ENTRYPOINT ["jbake"]
CMD ["-b","-s"]

# Expose default port
EXPOSE 8820

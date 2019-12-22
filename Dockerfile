FROM ringcentral/jdk:8u202
COPY . /usr/local/kiftd
WORKDIR /usr/local/kiftd
EXPOSE 8081
CMD java -jar kiftd-1.0.25-RELEASE.jar -start


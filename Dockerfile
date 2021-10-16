FROM openjdk:16

ADD ./src src
ADD ./run.sh run.sh

ENTRYPOINT ["run.sh"]

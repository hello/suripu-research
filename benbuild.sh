#!/bin/sh

if mvn clean package
then
s3cmd put target/suripu-research-*-SNAPSHOT.jar s3://hello-research-deploy/suripu-research2.jar
else
say "fail"
fi

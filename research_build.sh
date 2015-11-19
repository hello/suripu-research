#!/bin/sh

git pull
mvn clean package


s3cmd put configs/suripu-research.prod.yml s3://hello-research-deploy/suripu-research.prod.yml
s3cmd put target/suripu-research-*-SNAPSHOT.jar s3://hello-research-deploy/suripu-research.jar
#!/bin/bash
#mvn install:install-file -Dfile=./external_jars/deeplearning4j-core-0.4-rc3.9.jar -DgroupId=org.deeplearning4j -DartifactId=deeplearning4j-core -Dversion=0.4-rc3.9 -Dpackaging=jar
#mvn install:install-file -Dfile=./external_jars/lombok-1.16.7.jar -DgroupId=org.projectlombok -DartifactId=lombok -Dversion=1.16.7 -Dpackaging=jar
mvn deploy:deploy-file -Dfile=./external_jars/deeplearning4j-core-0.4-rc3.9.jar -Durl="file:///Users/benjo/dev/suripu-research/repo/" -DgroupId=org.deeplearning4j -DartifactId=deeplearning4j-core -Dversion=0.4-rc3.9 -Dpackaging=jar
mvn deploy:deploy-file -Dfile=./external_jars/lombok-1.16.7.jar -Durl="file:///Users/benjo/dev/suripu-research/repo/" -DgroupId=org.projectlombok -DartifactId=lombok -Dversion=1.16.7 -Dpackaging=jar

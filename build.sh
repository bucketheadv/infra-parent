#!/usr/bin/env sh
rm -rf ~/.m2/repository/org/infra/structure/

cd infra-pom
mvn install

cd ..
mvn clean install
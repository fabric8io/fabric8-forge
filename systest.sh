#!/usr/bin/env bash

echo "running fabric8 forge system test on the current kubernetes cluster"
cd fabric8-forge-core
mvn install
cd ../fabric8-forge-rest-client
mvn test -Dtest="*KT"

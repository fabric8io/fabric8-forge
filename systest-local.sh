#!/usr/bin/env bash

echo "running fabric8 forge system test locally against minikube / minishift"

export TERM=dumb
export JENKINS_URL=`gofabric8 service jenkins -u`
export FABRIC8_FORGE_URL=`gofabric8 service fabric8-forge -u`

echo "Using Jenkins $JENKINS_URL and Forge $FABRIC8_FORGE_URL"

./systest.sh

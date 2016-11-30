#!/usr/bin/env bash

echo "re-creating minikube then running the system test"

APP="minishift"

${APP} delete
sleep 5

gofabric8 start  --open-console=false  --minishift
sleep 30
gofabric8 wait-for --all --timeout=2h

./systest-local.sh

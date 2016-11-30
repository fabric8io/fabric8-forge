#!/usr/bin/env bash

echo "re-creating minikube then running the system test"

APP="minikube"

${APP} delete
sleep 5

gofabric8 start  --open-console=false
sleep 30
gofabric8 wait-for --all --timeout=60m

./systest-local.sh

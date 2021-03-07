#!/bin/bash

# clean may not be necessary but its still pretty quick
./gradlew clean installDist

docker-compose build


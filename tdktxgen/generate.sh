#!/usr/bin/env bash

echo "---------------------------------------------------------------------------------------------"
echo "                                   Building Generator Jar"
echo "---------------------------------------------------------------------------------------------"
./gradlew :tdktxgen:clean jar

echo "---------------------------------------------------------------------------------------------"
echo "                          Generate Telegram Api Kotlin Extensions"
echo "---------------------------------------------------------------------------------------------"
java -jar tdktxgen/build/libs/tdktxgen.jar

echo "Done"
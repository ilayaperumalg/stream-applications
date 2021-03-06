#!/bin/bash

if [ "$#" -ne 2 ]; then
    echo "Please specify the release version and java-functions.version property"
    exit
fi

function git_commit_push {
 echo "in git commit"
 git commit -am"Stream Applications Core: Release - $1"
 git push origin master
}

pushd ../..

VERSION=$1
./mvnw -f applications/stream-applications-core versions:set -DnewVersion=$VERSION -DgenerateBackupPoms=false

sed -i '' 's/<java-functions.version>.*/<java-functions.version>'"$2"'<\/java-functions.version>/g' pom.xml

if [[ $VERSION =~ M[0-9]|RC[0-9] ]]; then
 lines=$(find applications/stream-applications-core -type f -name pom.xml | xargs grep SNAPSHOT | grep -v ".contains(" | grep -v regex | wc -l)
 if [ $lines -eq 0 ]; then
  echo "All good"
  git_commit_push 
 else
   echo "Snapshots found. Exiting build"
   find applications/stream-applications-core -type f -name pom.xml | xargs grep SNAPSHOT | grep -v ".contains(" | grep -v regex
   git checkout -f
 fi
else 
  lines=$(find applications/stream-applications-core -type f -name pom.xml | xargs egrep "SNAPSHOT|M[0-9]|RC[0-9]" | grep -v ".contains(" | grep -v regex | wc -l)
  if [ $lines -eq 0 ]; then
   echo "All good"
   git_commit_push 
  else
   echo "Non Release versions found. Exiting build"
   find applications/stream-applications-core -type f -name pom.xml | xargs egrep "SNAPSHOT|M[0-9]|RC[0-9]" | grep -v ".contains(" | grep -v regex
   git checkout -f
  fi
fi

popd

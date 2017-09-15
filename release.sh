#!/bin/bash -x

# This script updates versions for should be run from mat-calcite-plugin causing Travis deploy releases to Bintray and GitHub releases
# Later the staged repositories can be inspected or promoted
# Note: if script fails in the middle, manual git reset, and removal of the tag might be required
# Note: the script should be run like ./release_stage.sh 1.3.0 1.4.0

RELEASE_VERSION=$1
NEXT_VERSION=$2

if [ -z "$RELEASE_VERSION" ]; then
  echo "Release version is not set"
  exit 1
fi
if [ -z "$NEXT_VERSION" ]; then
  echo "Next version is not set"
  exit 1
fi

mvn tycho-versions:set-version -DnewVersion=$RELEASE_VERSION &&\
(cd MatCalciteDependencies && mvn versions:set -DnewVersion=$RELEASE_VERSION && mvn versions:commit) &&\
git add -u &&\
git commit -m "Prepare for release v$RELEASE_VERSION" &&\
git push &&\
read -p "About to release v$RELEASE_VERSION to master" &&\
git tag v$RELEASE_VERSION &&\
git push origin v$RELEASE_VERSION &&\
mvn tycho-versions:set-version -DnewVersion=$NEXT_VERSION-SNAPSHOT &&\
(cd MatCalciteDependencies && mvn versions:set -DnewVersion=$NEXT_VERSION-SNAPSHOT && mvn versions:commit) &&\
git add -u &&\
git commit -m "Prepare for next development iteration" &&\
git push

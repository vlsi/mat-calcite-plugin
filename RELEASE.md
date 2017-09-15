Introduction
Follow these steps if you want to release a new version of Spray. Later we plan to leverage the Maven Release Plugin to facilitate these steps.

Pull the latest state from the repository
Open a command-line and go to the releng directory
Set project version to release version with the Tycho Versions plugin
mvn org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion=0.1.0
Manually change the releng/repository/category.xml file. Replace 0.1.0.qualifier by 0.1.0
Execute a Maven build with goals clean verify to assure that everything builds
mvn clean verify
Copy the created repository to the releases directory of the spray.distribution repository.
cp -R repository/target/repository/* ../../spray.distribution/releases/
Commit the changed files
git commit -a -m "prepare for release"
Create a release tag with pattern v<version>
git tag v0.1.0
Increment to next development version
mvn org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion=0.2.0-SNAPSHOT
Manually change the releng/repository/category.xml file. Replace 0.1.0 by 0.2.0.qualifier
Execute a Maven build with goals clean verify to assure that everything builds
mvn clean verify
Commit the changes
git commit -a -m "increment to next development version"
Go one directory up to the root of the repository
cd ..
Push the changes including the tag to the server
git --tags push origin master
Switch to the root directoy of spray.distribution
Add, commit and push files in the spray.distribution repository
git add *
git commit -m "releasing version 0.1.0"
git push origin master
Change to the releases directory and zip the contents
cd releases
zip -r spray-0.1.0.zip *
Upload the file to the Downloads section of the project
Announce the release on the spray-dev mailing list

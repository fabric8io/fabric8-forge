## Fabric8 Forge

This project contains the Fabric8 extensions for [JBoss Forge](http://forge.jboss.org/). It includes:

* [addons](addons) are various Forge addons for working with Apache Camel and [Fabric8 DevOps](http://fabric8.io/guide/fabric8DevOps.html)
* rest provides a REST service for working with JBoss Forge with [Fabric8 DevOps](http://fabric8.io/guide/fabric8DevOps.html) inside the [Fabric8 Console](http://fabric8.io/guide/console.html)


### Documentation

For more details see the [Fabric8 Forge Documentation](http://fabric8.io/guide/forge.html)

### Building the addons

To try out addons:

    cd addons
    mvn install
    
Then you can install the addons into forge via the [forge addon-install command](http://fabric8.io/guide/forge.html) using the current snapshot build version 
    
### Building and testing REST service

To build everything and run it in your local OpenShift installation on your laptop try:

    mvn -Pf8-local-deploy

To push the docker image first then provision it onto a remote OpenShift cluster try:

    mvn -Pf8-deploy

If you just want to build the docker image and kubernetes resources but not deploy them use:

    mvn -Pf8-build


The test case in the [fabric8-forge](fabric8-forge) module takes a while to build as it pre-populates the local maven repository with all the required jars for the Forge tooling.
    
So you might want to only include tests in the [fabric8-forge](fabric8-forge) module the first build of the day, then disable tests after that?

## Fabric8 Forge

This project contains the Fabric8 extensions for [JBoss Forge](http://forge.jboss.org/). It includes:

* [addons](addons) are various Forge addons for working with Apache Camel and [Fabric8 DevOps](http://fabric8.io/guide/fabric8DevOps.html)
* rest provides a REST service for working with JBoss Forge with [Fabric8 DevOps](http://fabric8.io/guide/fabric8DevOps.html) inside the [Fabric8 Console](http://fabric8.io/guide/console.html)


### Documentation

For more details see the [Fabric8 Forge Documentation](http://fabric8.io/guide/forge.html)

### Fabric8 Forge based system tests

To check out the systems tests check out the [system test documentation](systest/ReadMe.md)

### Building the addons

To try out addons:

    cd addons
    mvn install
    
Then you can install the addons into forge via the [forge addon-install command](http://fabric8.io/guide/forge.html) using the current snapshot build version 

#### Trying the addons locally
    
If you startup forge you can then install the local builds of the addons via:
    
    addon-install --coordinate io.fabric8.forge:camel,2.3-SNAPSHOT
    addon-install --coordinate io.fabric8.forge:camel-commands,2.3-SNAPSHOT
    addon-install --coordinate io.fabric8.forge:devops,2.3-SNAPSHOT
    
To remove any of them type:
   
    addon-remove --addons io.fabric8.forge:camel,2.3-SNAPSHOT
    addon-remove --addons io.fabric8.forge:camel-commands,2.3-SNAPSHOT
    addon-remove --addons io.fabric8.forge:devops,2.3-SNAPSHOT

### Building and testing REST service

To build everything and run it in your local OpenShift installation on your laptop try:

    mvn -Dtest=false install 
    cd fabric8-forge
    mvn fabric8:resource-apply
    
The test case in the [fabric8-forge](fabric8-forge) module takes a while to build as it pre-populates the local maven repository with all the required jars for the Forge tooling.
    
So you might want to only include tests in the [fabric8-forge](fabric8-forge) module the first build of the day, then disable tests after that?

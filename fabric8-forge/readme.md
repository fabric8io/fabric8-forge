fabric8-forge
-------------

The fabric8-forge docker image can include a pre-downloded maven repository.

To download this repository you can run

    mvn compile fabric8-forge:download
    
And then it downloads all the JARs into `localMavenRepo` directory. You can then keep this directory around
    and do rebuild of fabric8-forge, or do mvn fabric8:run to run it locally etc.
    
  
#!/usr/bin/groovy
def updateDependencies(source){

// disable dependency updates until we move to the push deps approach
  // def properties = []
  // properties << ['<fabric8.version>','io/fabric8/kubernetes-api']
  // properties << ['<docker.maven.plugin.version>','io/fabric8/docker-maven-plugin']
  // properties << ['<fabric8.devops.version>','io/fabric8/devops/apps/jenkins']
  // properties << ['<fabric8.archetypes.release.version>','io/fabric8/archetypes/archetypes-catalog']
  // properties << ['<gitective.version>','io/fabric8/gitective-core']
  //
  // updatePropertyVersion{
  //   updates = properties
  //   repository = source
  //   project = 'fabric8io/fabric8-forge'
  // }
}

def stage(){
  return stageProject{
    project = 'fabric8io/fabric8-forge'
    useGitTagForNextVersion = true
  }
}

def approveRelease(project){
  def releaseVersion = project[1]
  approve{
    room = null
    version = releaseVersion
    console = null
    environment = 'fabric8'
  }
}

def release(project){
  releaseProject{
    stagedProject = project
    useGitTagForNextVersion = true
    helmPush = false
    groupId = 'io.fabric8.forge.distro'
    githubOrganisation = 'fabric8io'
    artifactIdToWatchInCentral = 'distro'
    artifactExtensionToWatchInCentral = 'pom'
    promoteToDockerRegistry = 'docker.io'
    dockerOrganisation = 'fabric8'
    imagesToPromoteToDockerHub = ['fabric8-forge']
    extraImagesToTag = null
  }
}

def mergePullRequest(prId){
  mergeAndWaitForPullRequest{
    project = 'fabric8io/fabric8-forge'
    pullRequestId = prId
  }

}
return this;

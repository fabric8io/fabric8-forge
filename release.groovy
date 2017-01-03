#!/usr/bin/groovy
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

def updateDownstreamDependencies(stagedProject) {
  pushPomPropertyChangePR {
    propertyName = 'fabric8.forge.version'
    projects = [
            'fabric8io/fabric8-maven-dependencies',
            'fabric8io/fabric8-platform',
            'fabric8io/ipaas-platform',
            'fabric8io/django'
    ]
    version = stagedProject[1]
  }
}

return this;

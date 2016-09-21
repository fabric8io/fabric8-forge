# Fabric8 Forge Core

This library is used to build the Fabric8 Forge Web app

## REST API

This library provides the REST API for working with JBoss Forge from the fabric8 console.

### Simple REST API for Forge commands

Typically multi-page wizards are kinda complex to use via REST.

However we have a simpler REST API to make things a little easier to use from consoles


    curl -k "http://fabric8-forge/api/forge/invoke/{commandName}/{namespace}/{projectName}?secret={secretName}&secretNamespace={secretNamespace}&kubeUserName={userName}"

Where:

* `commandName` is the JBoss Forge command name in shell syntax such as `camel-get-overview`
* `namespace` is the kubernetes namespace (or openshift project name); which is `default` initially
* `projectName` is the name of the BuildConfig or app
* `secretNamespace` is the kubernetes namespace used to store the Secret to acess git 
* `secretName` is the name of the `Secret` resource in the `secretNamespace` used for login and password/passcode or SSH keys for git access 
* `userName` is the user name to use for git

Any extra parameters to the Forge command can be supplied using query arguments.


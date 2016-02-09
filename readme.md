Kubernetes Workflow
-------------------

Kubernetes Workflow is Jenkins plugin which extends [Jenkins Workflow](https://github.com/jenkinsci/workflow-plugin) to allow building and testing inside Kubernetes Pods reusing kubernetes features like pods, build images, service accounts, volumes and secrets while providing an elastic slave pool (each build runs in new pods).

## Features
- Build steps inside Kubernetes Pods
    - Service Accounts
    - Volumes
    - Secrets
- Building, Pushing and using Docker Images


## Working with Kubernetes Pods

### Using a maven kubernetes pod

    kubernetes.pod('buildpod').withImage('maven').inside {      
        git 'https://github.com/fabric8io/kubernetes-workflow.git'
        sh 'mvn clean install'
    }    
    
### Using environment variables

    kubernetes.pod('buildpod').withImage('maven').withEnvVar('DOCKER_CONFIG','/home/jenkins/.docker/').inside {      
        git 'https://github.com/fabric8io/kubernetes-workflow.git'
        sh 'mvn clean install'
    }    
     
### Using Volumes

Currently the following volume types are supported:
       
- Secrets
- Host Path
- Empty Dir
        
#### Using secrets

    kubernetes.pod('buildpod').withImage('maven').withSecret('gpg-key','/home/jenkins/.gnupg').inside {      
        git 'https://github.com/fabric8io/kubernetes-workflow.git'
        sh 'mvn clean install'
    }    

#### Using host path mounts
    
    kubernetes.pod('buildpod').withImage('maven').withHostPathMount('/path/on/host', '/path/on/container').inside {      
        git 'https://github.com/fabric8io/kubernetes-workflow.git'
        sh 'mvn clean install'
    }  
      
#### Using empty Dir mounts
    
    kubernetes.pod('buildpod').withImage('maven').withEmptyDir('/path/on/container').inside {      
        git 'https://github.com/fabric8io/kubernetes-workflow.git'
        sh 'mvn clean install'
    }     
         
This also supports specifying the medium (e.g. "Memory")

         
    kubernetes.pod('buildpod').withImage('maven').withEmptyDir('/path/on/container', 'Memory').inside {      
        git 'https://github.com/fabric8io/kubernetes-workflow.git'
        sh 'mvn clean install'
    }                
    
### Using privileged containers

    kubernetes.pod('buildpod').withImage('maven').withPrivileged(true).inside {      
        git 'https://github.com/fabric8io/kubernetes-workflow.git'
        sh 'mvn clean install'
    }   
    
## Working with Docker Images

### Building, Tagging and Pushing

    node {
        git 'https://github.com/fabric8-quickstarts/node-example.git'
        if (!fileExists ('Dockerfile')) {
          writeFile file: 'Dockerfile', text: 'FROM node:5.3-onbuild'
        }
        kubernetes.image().withName("example").build().fromPath(".")
        kubernetes.image().withName("example").tag().inRepository("172.30.101.121:5000/default/example").withTag("1.0")
        kubernetes.image().withName("172.30.101.121:5000/default/example").push().withTag("1.0").toRegistry()
    } 
    
### Skipping the tagging part
    
You can directly tag the image during the build step:

    node {
        git 'https://github.com/fabric8-quickstarts/node-example.git'
        if (!fileExists ('Dockerfile')) {
          writeFile file: 'Dockerfile', text: 'FROM node:5.3-onbuild'
        }
        kubernetes.image().withName("172.30.101.121:5000/default/example").build().fromPath(".")
        kubernetes.image().withName("172.30.101.121:5000/default/example").push().toRegistry()
    } 

## Working with Kubernetes

You can apply Kubernetes resources in order to create and update pods, services, replication controllers, lists and openshift templates.  When running on openshift it will also create routes so that services are exposed.

Apply changes by passing the JSON formatted resource and the required environment.  If the enlvironment does not exist then a new namespace is created with the environment name.

The KubernetesApply step will enrich Pod or Replication Controller manifests, adding the platform default docker registry if no `registry` parameter set.


    node {
        def rc = getKubernetesJson {
          port = 8080
          label = 'node'
          icon = 'https://cdn.rawgit.com/fabric8io/fabric8/dc05040/website/src/images/logos/nodejs.svg'
          version = newVersion
        }

        kubernetesApply(file: rc, environment: 'my-cool-app-staging', registry: 'myexternalregistry.io:5000')
    }

__NOTE__ By default [DeploymentEvent](https://github.com/fabric8io/kubernetes-workflow/blob/master/src/main/java/io/fabric8/kubernetes/workflow/elasticsearch/DeploymentEvent.java) are sent to elasticsearch (if running in the same namespace) when a pod or replication controller is deployed.


## Working with Elasticsearch

You can send events to elasticsearch providing it is running in the current namespace with a Kubernetes service name of `elasticsearch`.  The fabric8 logging template does exactly this.


    node {
        def event = '{"user" : "rawlingsj","post_date" : "2016-01-30T13:29:36+00:00","project" : "my-cool-project"}'

        sendEvent(json: event, elasticsearchType: 'mytype')
    }

__NOTE__ The elasticsearch index used is `pipeline` and if no elasticsearchType is set the `custom` type is used.  For more information see https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-index_.html#docs-index_

## Technical notes

Under the hood the plugin is using hostPath mounts. This requires two things

- A service account associated with a security context constraint that allows hostPath mounts.
- A host capable of hostPath mounts

An example security context constraint that configures *myserviceacccount* in the *default* namespace can be found [here](docs/scc-example.json)

In some linux distros in order to use hostPath mounts you may need to use the following command on the docker host:

    chcon -Rt svirt_sandbox_file_t <host path>

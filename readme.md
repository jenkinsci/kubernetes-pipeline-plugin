Kubernetes Workflow
-------------------

Jenkins plugin which allows building and testing inside Kubernetes Pods.

## Features
- Service Accounts
- Volumes
- Secrets


## Examples

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

### Using secrets

    kubernetes.pod('buildpod').withImage('maven').withSecret('gpg-key','/home/jenkins/.gnupg').inside {      
        git 'https://github.com/fabric8io/kubernetes-workflow.git'
        sh 'mvn clean install'
    }    

### Using host path mounts

    kubernetes.pod('buildpod').withImage('maven').withHostPathMount('/path/on/host', '/path/on/container').inside {      
        git 'https://github.com/fabric8io/kubernetes-workflow.git'
        sh 'mvn clean install'
    }    

### Using privileged containers

    kubernetes.pod('buildpod').withImage('maven').withPrivileged(true).inside {      
        git 'https://github.com/fabric8io/kubernetes-workflow.git'
        sh 'mvn clean install'
    }   

## Technical notes

Under the hood the plugin is using hostPath mounts. This requires two things

- A service account associated with a security context constraint that allows hostPath mounts.
- A host capable of hostPath mounts

An example security context constraint that configures *myserviceacccount* in the *default* namespace can be found [here](docs/scc-example.json)

In some linux distros in order to use hostPath mounts you may need to use the following command on the docker host:

    chcon -Rt svirt_sandbox_file_t <host path>

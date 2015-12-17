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
        
### Using secrets

    kubernetes.pod('buildpod').withImage('maven').withSecret('gpg-key','/home/jenkins/.gnupg').inside {      
        git 'https://github.com/fabric8io/kubernetes-workflow.git'
        sh 'mvn clean install'
    }    
    
    
### Using privileged containers

    kubernetes.pod('buildpod').withImage('maven').privileged().inside {      
        git 'https://github.com/fabric8io/kubernetes-workflow.git'
        sh 'mvn clean install'
    }    
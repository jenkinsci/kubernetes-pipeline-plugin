Kubernetes Steps
----------------

This plugin provides pipeline steps useful for working with kubernetes:

- [Working with Kubernetes Pods](#working-with-kubernetes-pods)
- [Working with Docker Images](#working-with-docker-images)

## Working with Kubernetes Pods

Quite often a build needs to run some steps using specific tools and environment configuration.
In kubernetes this translates to a container that uses a specific image *(that provides the tools)*, has access to volume mounts (persistent volumes, secrets, configmaps).

This plugin provides pipeline steps, that allow you to create a pod and run custom shell steps inside a container.

The overall syntax looks like this:

    kubernetes.pod('buildpod')
        .withServiceAccount(<service account>)
        .withSecret(<mount path>, <secret name>)
        .withEnvar(<gloabal key1>, <value1>)
        .withNewContainer().withName(<name1>).withImage(<image1>)
                           .withEnv(<key1>,<value1>)
                           .and()
        .withNewContainer().withName(<name2>).withImage(<image2>).inside {
            sh '<some shell commands that are going to be run inside name2>'
    }

The are are a lot of shortcuts that make the syntax less verbose, and sugar that makes it read better. Most of the options are explained below.
It's even possible to skip the DSL completely and just use the steps directly or with the help of the script builder.

When a snippet like the above is wrapped inside a `node` it will just create the build pod and run the specified shell commands inside it.
If there is no `none` it will delegate to [kubernetes plugin](#https://github.com/jenkinsci/kubernetes-plugin) and create a kubernetes slave pod, that will additionally
contain the specified container.

### Using a maven kubernetes pod

    kubernetes.pod('buildpod').withImage('maven').inside {
        //for a single container you can avoid the .withNewContainer() thing.
        git 'https://github.com/jenkinsci/kubernetes-pipeline.git'
        sh 'mvn clean install'
    }


### Using environment variables

    kubernetes.pod('buildpod').withImage('maven').withEnvar('DOCKER_CONFIG','/home/jenkins/.docker/').inside {
        git 'https://github.com/fabric8io/kubernetes-pipeline.git'
        sh 'mvn clean install'
    }

### Using Volumes

Currently the following volume types are supported:

- Secrets
- Host Path
- Empty Dir

#### Using secrets

    kubernetes.pod('buildpod').withImage('maven').withSecret('gpg-key','/home/jenkins/.gnupg').inside {
        git 'https://github.com/fabric8io/kubernetes-pipeline.git'
        sh 'mvn clean install'
    }

#### Using host path mounts

    kubernetes.pod('buildpod').withImage('maven').withHostPath('/path/on/container', '/path/on/host').inside {
        git 'https://github.com/fabric8io/kubernetes-pipeline.git'
        sh 'mvn clean install'
    }

#### Using empty Dir mounts

    kubernetes.pod('buildpod').withImage('maven').withEmptyDir('/path/on/container').inside {
        git 'https://github.com/fabric8io/kubernetes-pipeline.git'
        sh 'mvn clean install'
    }

This also supports specifying the medium (e.g. "Memory")


    kubernetes.pod('buildpod').withImage('maven').withEmptyDir('/path/on/container', 'Memory').inside {
        git 'https://github.com/fabric8io/kubernetes-pipeline.git'
        sh 'mvn clean install'
    }

#### Using volume claim mounts

    kubernetes.pod('buildpod').withImage('maven').withVolumeClaim('/path/on/container', 'volume-claim-name').inside {
        git 'https://github.com/fabric8io/kubernetes-pipeline.git'
        sh 'mvn clean install'
    }

### Using privileged containers

    kubernetes.pod('buildpod').withImage('maven').withPrivileged(true).inside {
        git 'https://github.com/fabric8io/kubernetes-pipeline.git'
        sh 'mvn clean install'
    }

### Workspace Requirements

The build pod needs to access the workspace of the project. This can be achieved with the use of volume mounts.
You can configure volume mounts manually in order to mount `/home/jenkins/workspace` or `/home/jenkins/workspace/<job name>`
or let the plugin configure them for you.

The plugin will search and reuse any volume mounts you've configured for these paths.

If none is found it will try to create them using Persistent Volume Claims or Host Path mounts.

#### Using Persistent Volume Claim

If there is a pvc named `jenkins-workspace` the plugin will automatically mount it to `/home/jenkins/workspace`.
Else if there is a pvc named `jenkins-workspace-<job name>` the plugin will automatically mount it to `/home/jenkins/workspace/<job name>`.

If none of the above is found it will fallback to hostPath volumes.

#### Using Host Path Volumes

As a last resort the plugin will try to mount the hostPath `/home/jenkins/workspace`.
In some case host path volumes are not allowed, so this is the last resort and should be avoided as much as possible.

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

## Technical notes

### Docker related notes

Under the hood the docker related steps are using hostPath mounts. This requires three things:

- A service account associated with a security context constraint that allows hostPath mounts.
- A host capable of hostPath mounts
- To ensure that Jenkins can write to the docker socket (e.g. run as root).

An example security context constraint that configures *myserviceacccount* in the *default* namespace can be found [here](docs/scc-example.json)

In some linux distros in order to use hostPath mounts you may need to use the following command on the docker host:

    chcon -Rt svirt_sandbox_file_t <host path>

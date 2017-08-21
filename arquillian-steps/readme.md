Arquillian Steps
----------------

This plugin provides pipeline steps that leverage [Arquillian Cube](https://github.com/arquillian/arquillian-cube)

- [Creating a namespace](#creating-a-namespace)
- [Installing Resources](#installing-resources)
 
## Creating a namespace

It's typical to run your tests in an isolated environment, perform some kind of setup before the tests actually run and do some cleanup in the end.
From the Kubernetes point of view this seems like a perfect use-case for using an namespace.

Arquillian manages the namespace for you. It can create an ephemeral namespace or use an existing. It can keep the namespace around, or it can delete in the end.

This plugin allows you to use this functionality using pipelines:
  
      arquillianCubeKubernetesNamespace(prefix: 'testns') {
          
          //Create a build to run a maven build
          kubernetes.pod('buildpod').withImage('maven').inside {      
                  git 'https://github.com/myorg/myproject.git'
                  sh 'mvn clean install'
              }
      }

In the example above we created a temporary namespace with the prefix `testns` and a random suffix. Within the generated namesacpe are creating a build pod.
Note, that the build pod is created using [kubernetes steps](kubernetes-steps/readme.md) but we could also use plain [kubernetes-plugin](https://github.com/jenkinsci/kubernetes-plugin) .
 
### Namespace creation parameters

- **name** A fixed name for a namespace.
- **prefix** The prefix of the namespace to generate.
- **labels** The labels to add to the namespace.
- **annotations** The annotations to add to namespace.
- **namespaceLazyCreateEnabled** Create the namespace if not exists *(in case of a fixed namespace)*.
- **namespaceDestroyEnabled** Destroy the namespace after the execution of the block.

### Namespace creation using a DSL

The plugin also provides a DSL like flavor of the `arquillianCubeKubernetesNamespace` via the `cube` keyword. The previous example could then be written like:
 
       cube.withPrefix('testns').inside {
           
           //Create a build to run a maven build
           kubernetes.pod('buildpod').withImage('maven').inside {      
                   git 'https://github.com/myorg/myproject.git'
                   sh 'mvn clean install'
               }
       }


## Installing resources

Once the namespace is created, we could do numerous things:

- perform builds (create docker images etc).
- install additional requirements.
- run the actual tests

This plugin also provides support for `locating` and `installing` the required resources in order to create the environment.
Again, it leverages [Arquillian Cube](https://github.com/arquillian/arquillian-cube) for that:


    arquillianCubeKubernetesCreateEnv(environmentConfigUrl: 'http://somehost/some/path/template.yml')
    
The call above will install the Kubernetes / Openshift resources that we specified. The resource can be **any*** resource supported by Kubernetes / Openshift *(e.g. Deployment, Pod, Service etc)*.
The resource doesn't need to be a single resource, yt may contain multiple resources *(e.g. multiple Pods)*, or it can be a collection of resources *(e.g. KubernetesList, or Template)* .
    
Additionally, we can specify additional URL to resources that act as dependencies, or we can even specify shell scripts that install the resources for us.

Last but not least, we can specify the amount of time to wait until those resource become ready.
     
### Installing resources parameters

- **environmentSetupScriptUrl** A shell script to use for installing resources 
- **environmentTeardownScriptUrl** A shell script to use for cleaning up resources (in the end)
- **environmentConfigUrl** A URL to a resource.
- **environmentDependencies** A space separated list of URLs to dependencies       
- **waitTimeout** The amount of time to wait until resource become ready.
- **waitForServiceList** A list of services to wait for.

The last option `waitForServiceList` may not be obvious. Why would anyone want to explicitly specify a list of service, when the plugin does that implicitly?
The short answer: `to support resources installed using shell scripts`
The longer answer is that we can't possibly know what was installed by the user provided shell scripts, when the installation of resources is `outsourced` 
we need to explicitly specify which are the services we need to `wait for`.
   
### Installation of resources using DSL
    
    cube.environemnt().withConfiUrl('http://somehost/some/path/template.yml').create()
    
    
## Putting it all together
    
Here's how a pipeline could work, that is putting everything together:

       cube.withPrefix('testns').inside {
           
           //Create a build to run a maven build
           kubernetes.pod('buildpod').withImage('maven').inside {      
            git 'https://github.com/myorg/myproject.git'                   
            
            //Lets build the docker image
            sh 'mvn clean install fabric8:build'
           }
           
           //Install everything we need    
           cube.environemnt().withConfiUrl('http://somehost/some/path/template.yml').create()  
           
           //Run the actuall tests (e.g. using yarn)
           kubernetes.pod('buildpod').withImage('yarn').inside {      
            git 'https://github.com/myorg/e2etests.git'
            sh 'yarn test'
           }               
       }
           
           
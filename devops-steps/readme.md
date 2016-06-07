DevOps Steps
-------------

## Applying Kubernetes Configuration

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

__NOTE__ By default [DeploymentEvent](https://github.com/fabric8io/kubernetes-pipeline/blob/master/src/main/java/io/fabric8/kubernetes/workflow/elasticsearch/DeploymentEvent.java) are sent to elasticsearch (if running in the same namespace) when a pod or replication controller is deployed.


## Working with Elasticsearch


### Create
You can create events in elasticsearch providing it is running in the current namespace with a Kubernetes service name of `elasticsearch`.  The fabric8 logging template does exactly this.


    node {
        def event = '{"user" : "rawlingsj","post_date" : "2016-01-30T13:29:36+00:00","project" : "my-cool-project"}'

        createEvent(json: event, index: 'myindex')
    }

__NOTE__ The default elasticsearch index used is `pipeline` if no index property is set.  For more information see https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-index_.html#docs-index_


### Approve Events
You can send and update approve events in elasticsearch


    node{
        def id = approveRequestedEvent(app: 'test app', environment: 'staging')

        try {
          input id: 'Proceed', message: 'Continue?'
        } catch (err) {

          approveReceivedEvent(id: id, approved: false)
          throw err
        }
        approveReceivedEvent(id: id, approved: true)

    }
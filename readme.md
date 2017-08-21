Kubernetes Pipeline
-------------------

Kubernetes Pipeline is Jenkins plugin which extends [Jenkins Pipeline](https://github.com/jenkinsci/pipeline-plugin) to allow building and testing inside Kubernetes Pods reusing kubernetes features like pods, build images, service accounts, volumes and secrets while providing an elastic slave pool (each build runs in new pods).

## Features

### Kubernetes Steps

- Build steps inside Kubernetes Pods
    - Service Accounts
    - Volumes
    - Secrets
- Building, Pushing and using Docker Images

### DevOps Steps
- Apply Kubernetes JSON
- Interaction with ElasticSearch

### Arquillian Steps
- Create temporary namespaces for testing
- Install everything you need for end to end testing

### Aggregator
An aggregator plugin for all the above.

### Further Reading
- [Kubernetes Steps](kubernetes-steps/readme.md)
- [DevOps Steps](devops-steps/readme.md)
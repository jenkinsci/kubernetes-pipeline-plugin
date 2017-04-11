kubernetes.pod('buildpod').withName('maven').withImage('maven:3.3.9').inside {
        sh 'mvn -version'
}
pipeline {
    agent none
    stages {
        stage('Prepare') {
            agent none
            steps {
                script {
                    def userInput = input message: "Please enter a test suite to run:",
                    parameters:[
                        string(name: 'indyUrl', defaultValue: 'http://indy-perf-nos-automation.cloud.paas.psi.redhat.com/api/content/maven/group/DA-temporary-builds', description: 'Indy group URL to test'),
                        string(name: 'artifacts', defaultValue: 'keycloak', description: 'keycloak(292), camel(634), etc. Or you can specify a build id (or ids separated by ,) or a full repour log url to fetch artifacts, e.g., 53917, http://orch.psi.redhat.com/pnc-rest/rest/build-records/53917/repour-log.')
                    ]
                    env.indyUrl = userInput.indyUrl
                    env.artifacts = userInput.artifacts
                }
            }
        }
        stage('Build') {
            agent { label 'maven' }
            steps {
                sh "mvn clean test -DindyUrl=${env.indyUrl} -Dartifacts=${env.artifacts}"
            }
        }
    }
}

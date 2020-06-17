pipeline {
    agent { label 'maven' }
    stages {
        stage('Prepare') {
            steps {
                sh 'printenv'
            }
        }
        stage('Enter Parameters'){
            steps {
                script {
                    def userInput = input message: "Please enter a test suite to run:",
                    parameters:[
                        string(name: 'indyUrl', defaultValue: 'http://indy-perf-nos-automation.cloud.paas.psi.redhat.com/api/content/maven/group/DA-temporary-builds', description: 'Indy group URL to test'),
                        string(name: 'artifacts', defaultValue: 'camel', description: 'artifacts(275), activemq-artemis(76), camel(634), etc. Or you can specify a repour log url to fetch artifacts from, e.g., http://orch.psi.redhat.com/pnc-rest/rest/build-records/53917/repour-log.'),
                        string(name: 'limit', defaultValue: '10', description: 'artifacts limit')
                    ]
                    env.indyUrl = userInput.indyUrl
                    env.artifacts = userInput.artifacts
                    env.limit = userInput.limit
                }
            }
        }
        stage('Build') {
            steps {
                sh "mvn clean test -DindyUrl=${env.indyUrl} -Dartifacts=${env.artifacts} -Dlimit=${env.limit}"
            }
        }
    }
}

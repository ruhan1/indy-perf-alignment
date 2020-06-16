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
                        string(name: 'artifacts', defaultValue: 'artifacts', description: 'artifacts(275), activemq-artemis(76), camel(634), etc'),
                        string(name: 'artifactsCount', defaultValue: '10', description: 'max artifacts count to retrieve')
                    ]
                    env.indyUrl = userInput.indyUrl
                    env.artifacts = userInput.artifacts
                    env.artifactsCount = userInput.artifactsCount
                }
            }
        }
        stage('Build') {
            steps {
                //sh "echo ${env.indyUrl}"
                sh "mvn clean test -DindyUrl=${env.indyUrl} -Dartifacts=${env.artifacts} -DartifactsCount=${env.artifactsCount}"
            }
        }
    }
}

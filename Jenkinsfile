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
                        string(name: 'indyUrl', defaultValue: 'http://indy-master-devel.psi.redhat.com/api/content/maven/group/DA-temporary-builds', description: 'Indy group URL to test'),
                        string(name: 'artifactsCount', defaultValue: '10', description: 'artifacts count (281 in total)')
                    ]
                    env.indyUrl = userInput.indyUrl
                    env.artifactsCount = userInput.artifactsCount
                }
            }
        }
        stage('Build') {
            steps {
                //sh "echo ${env.indyUrl}"
                sh "mvn clean test -DindyUrl=${env.indyUrl} -DartifactsCount=${env.artifactsCount}"
            }
        }
    }
}

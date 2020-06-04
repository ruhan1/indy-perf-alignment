def indyUrl = ''
def artifactsFile = ''

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
                        string(name: 'artifactsFile', defaultValue: 'artifacts', description: 'artifacts (trial) or artifacts-all (281 files)')
                    ]
                    indyUrl = userInput.indyUrl
                    artifactsFile = userInput.artifactsFile
                }
            }
        }
        stage('Build') {
            steps {
                sh 'mvn clean test -DindyUrl=${indyUrl} -DartifactsFile=${artifactsFile}'
            }
        }
    }
}

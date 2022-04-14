@Library(['utils@master']) _

import com.lmig.intl.cloud.jenkins.util.EnvConfigUtil

def deployCdk() {
    echo "Stack deployment starting..."
    sh "cdk deploy --require-approval=never"
    echo "Stack deployment finished!"
}

node('linux') {
    stage('Clone') {
        checkout scm
    }

    stage('Build ') {
        sh "mvn clean install"
    }
    
	stage ("deploy") {
        withAWS(
        credentials: getAWSCredentialID(environment: "dev"),
        region: getAWSRegion()) {
    		deployCdk()
    	}
	}
}
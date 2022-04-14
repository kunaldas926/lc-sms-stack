node('linux') {

	def deployCdk(currentEnv) {
	    echo "Stack deployment starting..."
	    sh "cdk deploy --require-approval=never"
	    echo "Stack deployment finished!"
	}

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
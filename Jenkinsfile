@Library(['utils@master']) _

import com.lmig.intl.cloud.jenkins.util.EnvConfigUtil

def deployCdk() {
    echo "Stack deployment starting..."
    // TODO: Mention version here
    sh "npm install -g aws-cdk@latest"
    sh "cdk synth --require-approval=never --app='java -jar ./target/sms-stack-0.0.1-SNAPSHOT.jar \
			-profile dev \
			-lm_troux_uid 7CFD56E9-332A-40F7-8A24-557EF0BFC796 \
			-program test-reg'"
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
@Library(['utils@master']) _

import com.lmig.intl.cloud.jenkins.util.EnvConfigUtil

properties([
    parameters([
        choice(
            defaultValue: "dev",
            description: 'Env dev|nonprod|prod - used to determine which environment to use for deployment',
            name: 'PROFILE',
            choices: ["dev", "nonprod", "prod"].join("\n")
        ),

        string(
            defaultValue: "reg",
            description: 'PROGRAM - used to determine which program to use for deployment',
            name: 'PROGRAM'          
        ),
        
        string(
            defaultValue: "7CFD56E9-332A-40F7-8A24-557EF0BFC796",
            description: 'lm_troux_uid',
            name: 'TROUX_UID'          
        )
    ])
])

def deployCdk() {
    echo "Stack deployment starting..."
    // TODO: Mention version here
    sh "npm install -g aws-cdk@latest"
    sh "cdk deploy --require-approval=never --app='java -jar ./target/sms-stack-0.0.1-SNAPSHOT.jar \
			-profile ${params.PROFILE} \
			-lm_troux_uid ${params.TROUX_UID} \
			-program ${params.PROGRAM}'"
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
	def env = params.PROFILE
        withAWS(
        credentials: getAWSCredentialID(environment: env),
        region: getAWSRegion()) {
    		deployCdk()
    	}
	}
}
@Library(['utils@master']) _

import com.lmig.intl.cloud.jenkins.util.EnvConfigUtil

properties([
    parameters([
        string(
            defaultValue: "reg",
            description: 'PROGRAM - used to determine which program to use for deployment',
            name: 'PROGRAM'          
        ),
        
        string(
            defaultValue: "7CFD56E9-332A-40F7-8A24-557EF0BFC796",
            description: 'lm_troux_uid',
            name: 'TROUX_UID'          
        ),
        
        string(
            defaultValue: "code/lc-sms-connector-lambda-0.0.1-SNAPSHOT.jar",
            description: 'bucket key for lambda jar',
            name: 'CONNECTOR_LAMBDA_S3_KEY'          
        ),
        
        string(
            defaultValue: "code/sms-processor-0.0.1-SNAPSHOT.jar",
            description: 'bucket key for lambda jar',
            name: 'PROCESSOR_LAMBDA_S3_KEY'          
        ),
        
        string(
            defaultValue: "code/lc-sms-dbconnector-lambda-1.0-SNAPSHOT.jar",
            description: 'bucket key for lambda jar',
            name: 'DB_CONNECTOR_LAMBDA_S3_KEY'
        ),
        
        string(
            description: 'vietguys credential',
            name: 'VIETGUYS_PASS'          
        ),
        
        string(
            description: 'dtac credential',
            name: 'DTAC_PASS'          
        )
    ])
])

static def getEnvFromBuildPath(jobPath) {
    def directories = jobPath.split('/')
    return directories[-3]
}

def deployCdk(currentEnv) {
    echo "Stack deployment starting..."
    // TODO: Mention version here
    sh "npm install -g aws-cdk@latest"
    sh "cdk deploy --require-approval=never --app='java -jar ./target/sms-stack-0.0.1-SNAPSHOT.jar \
			-profile ${currentEnv} \
			-lm_troux_uid ${params.TROUX_UID} \
			-program ${params.PROGRAM} \
			-connectorLambdaS3Key ${params.CONNECTOR_LAMBDA_S3_KEY} \
			-processorLambdaS3Key ${params.PROCESSOR_LAMBDA_S3_KEY} \
			-dbConnectorLambdaS3Key ${params.DB_CONNECTOR_LAMBDA_S3_KEY} \
			-vietguyPass ${params.VIETGUYS_PASS} \
			-dtacPass ${params.DTAC_PASS}'"
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
	def currentEnv = getEnvFromBuildPath(env.JOB_NAME)
        withAWS(
        credentials: getAWSCredentialID(environment: currentEnv),
        region: getAWSRegion()) {
    		deployCdk(currentEnv)
    	}
	}
}
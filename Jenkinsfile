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
            defaultValue: "7B962901-4D59-400C-B089-403B614DCDC3",
            description: 'lm_troux_uid',
            name: 'TROUX_UID'          
        ),
        
        string(
            defaultValue: "code/sms/sms-connector-lambda-0.0.1-SNAPSHOT.jar",
            description: 'bucket key for lambda jar',
            name: 'CONNECTOR_LAMBDA_S3_KEY'          
        ),
        
        string(
            defaultValue: "code/sms/sms-processor-lambda-0.0.1-SNAPSHOT.jar",
            description: 'bucket key for lambda jar',
            name: 'PROCESSOR_LAMBDA_S3_KEY'          
        ),
        
        string(
            defaultValue: "code/sms/sms-retry-lambda-0.0.1-SNAPSHOT.jar",
            description: 'bucket key for lambda jar',
            name: 'RETRY_LAMBDA_S3_KEY'          
        ),
        
        string(
            defaultValue: "code/sms/sms-dlq-lambda-0.0.1-SNAPSHOT.jar",
            description: 'bucket key for lambda jar',
            name: 'DLQ_LAMBDA_S3_KEY'
        ),
        
        string(
            defaultValue: "code/sms/sms-dbconnector-lambda-0.0.1-SNAPSHOT.jar",
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

def deployCdk(currentEnv, accountId, region) {
    echo "Stack deployment starting..."
    sh "cdk deploy --require-approval=never --app='java -jar ./target/sms-stack-0.0.1-SNAPSHOT.jar \
			-profile ${currentEnv} \
			-lm_troux_uid ${params.TROUX_UID} \
			-program ${params.PROGRAM} \
			-connectorLambdaS3Key ${params.CONNECTOR_LAMBDA_S3_KEY} \
			-processorLambdaS3Key ${params.PROCESSOR_LAMBDA_S3_KEY} \
			-dbConnectorLambdaS3Key ${params.DB_CONNECTOR_LAMBDA_S3_KEY} \
			-retryLambdaS3Key ${params.RETRY_LAMBDA_S3_KEY} \
			-dlqLambdaS3Key ${params.DLQ_LAMBDA_S3_KEY} \
			-vietguyPass ${params.VIETGUYS_PASS} \
			-dtacPass ${params.DTAC_PASS} \
			-accountId ${accountId} \
			-region ${region}'"
    echo "Stack deployment finished!"
}

node('linux') {
    stage('Clone') {
        checkout scm
    }

    stage('Build ') {
        sh "npm install -g aws-cdk@2.24.1"
    	sh "npm install -g n@8.2.0"
    	sh "n 16.15.1"
    	sh "mvn clean install"
    	sh "mvn clean install"
    }
    
	stage ("deploy") {
	def currentEnv = getEnvFromBuildPath(env.JOB_NAME)
	def accountId = getAwsAccountId()
	def region = getAWSRegion()
		
    withAWS(
    credentials: getAWSCredentialID(environment: currentEnv),
	    region: getAWSRegion()) {
			deployCdk(currentEnv, accountId, region)
		}
	}
}
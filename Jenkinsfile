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
            defaultValue: "code/sms/sms-connector-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'CONNECTOR_LAMBDA_S3_KEY'          
        ),
        
        string(
            defaultValue: "code/sms/sms-processor-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'PROCESSOR_LAMBDA_S3_KEY'          
        ),
        
        string(
            defaultValue: "code/sms/sms-mapper-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'MAPPER_LAMBDA_S3_KEY'
        ),
        
        string(
            defaultValue: "code/sms/sms-retry-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'RETRY_LAMBDA_S3_KEY'          
        ),
        
        string(
            defaultValue: "code/sms/sms-status-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'STATUS_LAMBDA_S3_KEY'
        ),
        
        string(
            defaultValue: "code/sms/sms-dlq-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'DLQ_LAMBDA_S3_KEY'
        ),
        
        string(
            defaultValue: "code/sms/sms-dbconnector-lambda-0.0.1-RELEASE.jar",
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
        ),
        
        booleanParam(
            defaultValue: false,
            description: 'select true if env is nonprod and you want to promote artifact',
            name: 'PROMOTE'       
        ),

        choice(
            choices: 'Yes\nNo',
            description: 'select yes if you want to deploy for first time',
            name: 'firstTimeDeploy'
        )
    ])
])

static def getEnvFromBuildPath(jobPath) {
    def directories = jobPath.split('/')
    return directories[1]
}

def deployCdk(currentEnv, accountId, region) {
    echo "Stack deployment starting..."
    sh "cdk deploy --require-approval=never --app='java -jar ./target/sms-stack-0.0.1-SNAPSHOT.jar \
			-profile ${currentEnv} \
			-lm_troux_uid ${params.TROUX_UID} \
			-program ${params.PROGRAM} \
			-connectorLambdaS3Key ${params.CONNECTOR_LAMBDA_S3_KEY} \
			-processorLambdaS3Key ${params.PROCESSOR_LAMBDA_S3_KEY} \
			-mapperLambdaS3Key ${params.MAPPER_LAMBDA_S3_KEY} \
			-dbConnectorLambdaS3Key ${params.DB_CONNECTOR_LAMBDA_S3_KEY} \
			-retryLambdaS3Key ${params.RETRY_LAMBDA_S3_KEY} \
			-smsStatusLambdaS3Key ${params.STATUS_LAMBDA_S3_KEY} \
			-dlqLambdaS3Key ${params.DLQ_LAMBDA_S3_KEY} \
			-vietguyPass ${params.VIETGUYS_PASS} \
			-dtacPass ${params.DTAC_PASS} \
			-accountId ${accountId} \
			-region ${region}'"
    echo "Stack deployment finished!"
}

def createCodeDeployResources(currentEnv, accountId, region) {
    sh "aws iam create-role --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --assume-role-policy-document file://./assume-role-policy.json --tags Key=lm_troux_uid,Value=${params.TROUX_UID} Key=aws_iam_permission_boundary_exempt,Value=true"
    sh "aws iam attach-role-policy --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --policy-arn arn:aws:iam::${accountId}:policy/intl-global-deny"
    sh "aws iam attach-role-policy --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --policy-arn arn:aws:iam::${accountId}:policy/cloud-services/cloud-services-global-deny"
    sh "aws iam attach-role-policy --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --policy-arn arn:aws:iam::${accountId}:policy/intl-cs-global-deny-services"
    sh "aws iam put-role-policy --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --policy-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-policy --policy-document file://./sms-bg-policy.json"
}

node('linux') {
    stage('Clone') {
        checkout scm
    }

    stage('Build ') {
        sh "npm install -g aws-cdk@2.24.1"
    	sh "npm install -g n@8.2.0"
    	sh "n 16.15.1"
//     	sh "mvn spotless:apply"
//     	sh "mvn clean install"
    }
    
	stage ("deploy") {
	def currentEnv = getEnvFromBuildPath(env.JOB_NAME)
	def accountId = getAwsAccountId()
	def region = getAWSRegion()
		
    withAWS(
    credentials: getAWSCredentialID(environment: currentEnv),
	    region: getAWSRegion()) {
			deployCdk(currentEnv, accountId, region)
            if (firstTimeDeploy == "Yes") {
                createCodeDeployResources(currentEnv, accountId, region)
            }
		}
	}
	
    stage('upload and promote artifact') {
		if(params.PROMOTE.toBoolean() == true && getEnvFromBuildPath(env.JOB_NAME) == 'nonprod') {
			timeout(time: 600, unit: 'SECONDS') {
		              input(id: 'userInput', message: 'push to artifact and promote ?', ok: 'Yes')
		    } 
	
	        def artifacts = ['prod_Jenkinsfile']
	        artifactoryUploadFiles files:artifacts
	
	        promoteToProd(approver:'Jose.Francis',
	            email:'Jose.Francis@libertymutual.com.hk',
	            version:env.BUILD_NUMBER){}
	   }
    }
}
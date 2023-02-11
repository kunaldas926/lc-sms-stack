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

def populateEnvVars() {
    def currentEnv = getEnvFromBuildPath(env.JOB_NAME)
    def accountId = getAwsAccountId()
    def region = getAWSRegion()
    def codeDeployAppSpecBucket = "intl-${currentEnv}-apacreg-${region}-code-deploy"
    def outputsMap = [:]
}

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

def populateCloudformationOutputs() {
    def outputs = sh(returnStdout: true, script: "aws cloudformation describe-stacks --stack-name ${params.PROGRAM}-${currentEnv}-sms-stack --no-paginate").trim()
    def outputsJson = readJSON text: outputs
    outputsMap = outputsJson.Stacks[0].Outputs.collectEntries { [it.OutputKey, it.OutputValue] }
    echo "Cloudformation outputs: ${outputsMap}"
    return outputsMap

}

def createCodeDeployResources(currentEnv, accountId, region) {
    def codeDeployIAMRole = sh(returnStdout: true, script: "aws iam create-role --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --assume-role-policy-document file://./assume-role-policy.json --tags Key=lm_troux_uid,Value=${params.TROUX_UID} Key=aws_iam_permission_boundary_exempt,Value=true").trim()
    sh "aws iam attach-role-policy --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --policy-arn arn:aws:iam::${accountId}:policy/intl-global-deny"
    sh "aws iam attach-role-policy --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --policy-arn arn:aws:iam::${accountId}:policy/cloud-services/cloud-services-global-deny"
    sh "aws iam attach-role-policy --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --policy-arn arn:aws:iam::${accountId}:policy/intl-cs-global-deny-services"
    sh "aws iam put-role-policy --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --policy-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-policy --policy-document file://./sms-bg-policy.json"
    def codeDeployIAMRoleArn = codeDeployIAMRole["Role"]["Arn"]
    echo "codeDeployIAMRoleArn: ${codeDeployIAMRoleArn}"
    def codeDeployIAMRoleID = codeDeployIAMRole["Role"]["RoleId"]
    echo "codeDeployIAMRoleID: ${codeDeployIAMRoleID}"
}

def updateKMSKeyPolicy(kmsKeyID) {
    def KMSKeyPloicy = readJson file: './kms-key-policy.json'
    KMSKeyPloicy["Statement"][0]["Principal"]["AWS"] = "arn:aws:iam::${accountId}:root"
    KMSKeyPloicy["Statement"][1]["Principal"]["AWS"] = "arn:aws:iam::${accountId}:root"
    KMSKeyPloicy["Statement"][2]["Principal"]["AWS"] = codeDeployIAMRoleArn
    writeJSON file: './kms-key-policy.json', json: KMSKeyPloicy
    sh "aws kms put-key-policy --key-id ${kmsKeyID} --policy-name default --policy file://./kms-key-policy.json"
}

def updateCodeDeployAppSpecBucketPolicy() {
    sh "aws s3api get-bucket-policy --bucket ${codeDeployAppSpecBucket} --query Policy --output text > policy.json"
    sh "jq <policy.json 'del(.Statement[0].Condition.StringNotLike.\"aws:userId\"[] | select(. ==\"${codeDeployIAMRoleID}:*\"))'> temp2.json && mv temp2.json policy.json"
    sh "jq <policy.json 'del(.Statement[1].Condition.StringNotLike.\"aws:userId\"[] | select(. ==\"${codeDeployIAMRoleID}:*\"))'> temp2.json && mv temp2.json policy.json"
    sh "jq <policy.json 'del(.Statement[2].Condition.StringNotLike.\"aws:userId\"[] | select(. ==\"${codeDeployIAMRoleID}:*\"))'> temp2.json && mv temp2.json policy.json"
    sh "jq <policy.json 'del(.Statement[3].Condition.StringNotLike.\"aws:userId\"[] | select(. ==\"${codeDeployIAMRoleID}:*\"))'> temp2.json && mv temp2.json policy.json"
    sh "cat policy.json"
    sh "jq <policy.json '.Statement[0].Condition.StringNotLike.\"aws:userId\" += [\"${codeDeployIAMRoleID}:*\"] | .Statement[1].Condition.StringNotLike.\"aws:userId\" += [\"${codeDeployIAMRoleID}:*\"] | .Statement[2].Condition.StringNotLike.\"aws:userId\" += [\"${codeDeployIAMRoleID}:*\"] | .Statement[3].Condition.StringNotLike.\"aws:userId\" += [\"${codeDeployIAMRoleID}:*\"]'> temp2.json && mv temp2.json policy.json"
    sh "cat policy.json"
    sh "aws s3api put-bucket-policy --bucket ${codeDeployAppSpecBucket} --policy file://policy.json"
}

def createCodeDeployApplication() {
    def codeDeployApplication = sh(returnStdout: true, script: "aws deploy create-application --application-name ${params.PROGRAM}-lc-sms-bg-app --compute-platform Lambda --tags Key=lm_troux_uid,Value=${params.TROUX_UID}").trim()
}

def createCodeDeployDeploymentConfig() {
    def codeDeployDeploymentConfig = sh(returnStdout: true, script: "aws deploy create-deployment-config --deployment-config-name ${params.PROGRAM}-lc-sms-bg-deployment-config --traffic-routing-config type=TimeBasedCanary,timeBasedCanary={canaryPercentage=10,canaryInterval=10} --compute-platform Lambda").trim()
}

def deleteCodeDeployResources() {
    sh "aws deploy delete-application --application-name ${params.PROGRAM}-lc-sms-bg-app"
    sh "aws deploy delete-deployment-config --deployment-config-name ${params.PROGRAM}-lc-sms-bg-deployment-config"
    sh "aws deploy delete-deployment-group --application-name ${params.PROGRAM}-lc-sms-bg-app --deployment-group-name ${params.PROGRAM}-lc-sms-bg-deployment-group"
}

node('linux') {

    stage('Populate env vars') {
         populateEnvVars()
    }

    stage('Clone') {
        checkout scm
    }

    stage('Build ') {
        sh 'curl --create-dirs "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "/temp-aws/awscliv2.zip"'
        sh 'unzip -u /temp-aws/awscliv2.zip -d /temp-aws'
        sh "/temp-aws/aws/install -b /usr/local/bin -i /usr/local/bin --update"
        sh 'rm -rf /temp-aws'
        sh "npm install -g aws-cdk@2.24.1"
    	sh "npm install -g n@8.2.0"
    	sh "n 16.15.1"
//     	sh "mvn spotless:apply"
//     	sh "mvn clean install"
    }
    
	stage ("deploy") {

		
    withAWS(
    credentials: getAWSCredentialID(environment: currentEnv),
	    region: getAWSRegion()) {
// 			deployCdk(currentEnv, accountId, region)
//          createCodeDeployResources(currentEnv, accountId, region)
            populateCloudformationOutputs()
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
@Library(['utils@master']) _
import com.lmig.intl.cloud.jenkins.util.EnvConfigUtil
import com.lmig.intl.cloud.jenkins.constants.EnvConstants;

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

        string(
            defaultValue: "code/sms/sms-processor-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'new-reg-dev-lc-sms-processor-lambda-s3-key'
        ),

        string(
            defaultValue: "code/sms/sms-mapper-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'new-reg-dev-lc-sms-mapper-lambda-s3-key'
        ),

        string(
            defaultValue: "code/sms/sms-retry-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'new-reg-dev-lc-sms-retry-lambda-s3-key'
        ),

        string(
            defaultValue: "code/sms/sms-status-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'new-reg-dev-lc-sms-status-lambda-s3-key'
        ),

        string(
            defaultValue: "code/sms/sms-dlq-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'new-reg-dev-lc-sms-dlq-lambda-s3-key'
        ),

        string(
            defaultValue: "code/sms/sms-dbconnector-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'new-reg-dev-lc-sms-connector-lambda-s3-key'
        ),

        booleanParam(
            defaultValue: false,
            description: 'select true if env is nonprod and you want to promote artifact',
            name: 'PROMOTE'       
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


node('linux') {
    deployTo(env: getCountryEnv()) {
        stage('Clone') {
            checkout scm
        }

        stage('Build ') {
            if ((sh(returnStdout: true, script: 'aws --version').trim()).startsWith("aws-cli/1.")) {
                sh 'curl --create-dirs "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "/temp-aws/awscliv2.zip"'
                sh 'unzip -u /temp-aws/awscliv2.zip -d /temp-aws'
                sh "/temp-aws/aws/install -b /usr/local/bin -i /usr/local/bin --update"
                sh 'rm -rf /temp-aws'
            }
//             sh "npm install -g aws-cdk@2.24.1"
//         	sh "npm install -g n@8.2.0"
//         	sh "n 16.15.1"
//         	sh "mvn spotless:apply"
//         	sh "mvn clean install"
        }

        stage ("deploy") {
        def currentEnv = getEnvFromBuildPath(env.JOB_NAME)
        def accountId = getAwsAccountId()
        def region = getAWSRegion()
        def codeDeployAppSpecBucket = "intl-${currentEnv}-apacreg-${region}-code-deploy"
        def outputsMap = [:]

        withAWS(
        credentials: getAWSCredentialID(environment: currentEnv),
            region: getAWSRegion()) {
//                 deployCdk(currentEnv, accountId, region)
                def outputs = sh(returnStdout: true, script: "aws cloudformation describe-stacks --stack-name ${params.PROGRAM}-${currentEnv}-lc-sms-stack --no-paginate").trim()
                def outputsJson = readJSON text: outputs
                outputsMap = outputsJson.Stacks[0].Outputs.collectEntries { ["${it.OutputKey}", "${it.OutputValue}"] }
                echo "outputsMap: ${outputsMap}"
                outputsMapJson = groovy.json.JsonOutput.toJson(outputsMap)
                echo "outputsMapJson: ${outputsMapJson}"
                try {
                    def codeDeployIAMRole = sh(returnStdout: true, script: "aws iam create-role --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --assume-role-policy-document file://./assume-role-policy.json --tags Key=lm_troux_uid,Value=${params.TROUX_UID} Key=aws_iam_permission_boundary_exempt,Value=true").trim()
                    echo "codeDeployIAMRole: ${codeDeployIAMRole}"
                    sh "aws iam attach-role-policy --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --policy-arn arn:aws:iam::${accountId}:policy/intl-global-deny"
                    sh "aws iam attach-role-policy --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --policy-arn arn:aws:iam::${accountId}:policy/cloud-services/cloud-services-global-deny"
                    sh "aws iam attach-role-policy --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --policy-arn arn:aws:iam::${accountId}:policy/intl-cs-global-deny-services"
                    sh "aws iam put-role-policy --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --policy-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-policy --policy-document file://./sms-bg-policy.json"
                    def codeDeployIAMRoleArn = codeDeployIAMRole["Role"]["Arn"]
                    def codeDeployIAMRoleID = codeDeployIAMRole["Role"]["RoleId"]
                    sh "aws s3api get-bucket-policy --bucket ${codeDeployAppSpecBucket} --query Policy --output text > policy.json"
                    sh "cat policy.json"
                    sh "jq <policy.json 'del(.Statement[0].Condition.StringNotLike.\"aws:userId\"[] | select(. ==\"${codeDeployIAMRoleID}:*\"))'> temp2.json && mv temp2.json policy.json"
                    sh "jq <policy.json 'del(.Statement[1].Condition.StringNotLike.\"aws:userId\"[] | select(. ==\"${codeDeployIAMRoleID}:*\"))'> temp2.json && mv temp2.json policy.json"
                    sh "jq <policy.json 'del(.Statement[2].Condition.StringNotLike.\"aws:userId\"[] | select(. ==\"${codeDeployIAMRoleID}:*\"))'> temp2.json && mv temp2.json policy.json"
                    sh "jq <policy.json 'del(.Statement[3].Condition.StringNotLike.\"aws:userId\"[] | select(. ==\"${codeDeployIAMRoleID}:*\"))'> temp2.json && mv temp2.json policy.json"
                    sh "cat policy.json"
                    sh "jq <policy.json '.Statement[0].Condition.StringNotLike.\"aws:userId\" += [\"${codeDeployIAMRoleID}:*\"] | .Statement[1].Condition.StringNotLike.\"aws:userId\" += [\"${codeDeployIAMRoleID}:*\"] | .Statement[2].Condition.StringNotLike.\"aws:userId\" += [\"${codeDeployIAMRoleID}:*\"] | .Statement[3].Condition.StringNotLike.\"aws:userId\" += [\"${codeDeployIAMRoleID}:*\"]'> temp2.json && mv temp2.json policy.json"
                    sh "cat policy.json"
                    sh "aws s3api put-bucket-policy --bucket ${codeDeployAppSpecBucket} --policy file://policy.json"
                    def kmsKekmsKeyID = outputsMapJson.findFirst { it.key.contains("kms") }
                    def snsTopicArn = outputsMapJson.findFirst { it.key.contains("sns") }
                    echo "kmsKeyID: ${kmsKeyID}"
                    echo "snsTopicArn: ${snsTopicArn}"
                } catch (Exception e) {
                    echo "Exception: ${e}"
//                     kmsKekmsKeyID = outputsMapJson.findFirst { it.key.contains("kms") }
//                     snsTopicArn = outputsMapJson.findFirst { it.key.contains("sns") }
//                     echo "kmsKeyID: ${kmsKeyID}"
//                     echo "snsTopicArn: ${snsTopicArn}"
//                     codeDeployIAMRoleArn = null
//                     codeDeployIAMRoleID = null
                }
//                 if (codeDeployIAMRoleArn == null || codeDeployIAMRoleID == null || codeDeployIAMRoleArn.isEmpty() || codeDeployIAMRoleID.isEmpty()) {
//                     def codeDeployIAMRole = sh(returnStdout: true, script: "aws iam create-role --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --assume-role-policy-document file://./assume-role-policy.json --tags Key=lm_troux_uid,Value=${params.TROUX_UID} Key=aws_iam_permission_boundary_exempt,Value=true").trim()
//                     echo "codeDeployIAMRole: ${codeDeployIAMRole}"
//                     sh "aws iam attach-role-policy --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --policy-arn arn:aws:iam::${accountId}:policy/intl-global-deny"
//                     sh "aws iam attach-role-policy --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --policy-arn arn:aws:iam::${accountId}:policy/cloud-services/cloud-services-global-deny"
//                     sh "aws iam attach-role-policy --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --policy-arn arn:aws:iam::${accountId}:policy/intl-cs-global-deny-services"
//                     sh "aws iam put-role-policy --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --policy-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-policy --policy-document file://./sms-bg-policy.json"
//                     codeDeployIAMRoleArn = codeDeployIAMRole["Role"]["Arn"]
//                     codeDeployIAMRoleID = codeDeployIAMRole["Role"]["RoleId"]
//                     sh "aws s3api get-bucket-policy --bucket ${codeDeployAppSpecBucket} --query Policy --output text > policy.json"
//                     sh "cat policy.json"
//                     sh "jq <policy.json 'del(.Statement[0].Condition.StringNotLike.\"aws:userId\"[] | select(. ==\"${codeDeployIAMRoleID}:*\"))'> temp2.json && mv temp2.json policy.json"
//                     sh "jq <policy.json 'del(.Statement[1].Condition.StringNotLike.\"aws:userId\"[] | select(. ==\"${codeDeployIAMRoleID}:*\"))'> temp2.json && mv temp2.json policy.json"
//                     sh "jq <policy.json 'del(.Statement[2].Condition.StringNotLike.\"aws:userId\"[] | select(. ==\"${codeDeployIAMRoleID}:*\"))'> temp2.json && mv temp2.json policy.json"
//                     sh "jq <policy.json 'del(.Statement[3].Condition.StringNotLike.\"aws:userId\"[] | select(. ==\"${codeDeployIAMRoleID}:*\"))'> temp2.json && mv temp2.json policy.json"
//                     sh "cat policy.json"
//                     sh "jq <policy.json '.Statement[0].Condition.StringNotLike.\"aws:userId\" += [\"${codeDeployIAMRoleID}:*\"] | .Statement[1].Condition.StringNotLike.\"aws:userId\" += [\"${codeDeployIAMRoleID}:*\"] | .Statement[2].Condition.StringNotLike.\"aws:userId\" += [\"${codeDeployIAMRoleID}:*\"] | .Statement[3].Condition.StringNotLike.\"aws:userId\" += [\"${codeDeployIAMRoleID}:*\"]'> temp2.json && mv temp2.json policy.json"
//                     sh "cat policy.json"
//                     sh "aws s3api put-bucket-policy --bucket ${codeDeployAppSpecBucket} --policy file://policy.json"
//                     kmsKeyID = outputsMapJson.toString().split(',').findAll { it.contains("kms") }.collect { it.split(':')[1].replaceAll('"', '') }
//                     echo "kmsKeyID: ${kmsKeyID}"
//                     snsTopicArn = outputsMapJson.toString().split(',').findAll { it.contains("sns") }.collect { it.split(':')[1].replaceAll('"', '') }
//                     echo "snsTopicArn: ${snsTopicArn}"
//                     def KMSKeyPloicy = readJson file: './kms-key-policy.json'
//                     KMSKeyPloicy["Statement"][0]["Principal"]["AWS"] = "arn:aws:iam::${accountId}:root"
//                     KMSKeyPloicy["Statement"][1]["Principal"]["AWS"] = "arn:aws:iam::${accountId}:root"
//                     KMSKeyPloicy["Statement"][2]["Principal"]["AWS"] = codeDeployIAMRoleArn
//                     writeJSON file: './kms-key-policy.json', json: KMSKeyPloicy
//                     sh "aws kms put-key-policy --key-id ${kmsKeyID} --policy-name default --policy file://./kms-key-policy.json"
//                 } else {
//                     codeDeployIAMRoleArn = sh(returnStdout: true, script: "aws iam get-role --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role").trim()["Role"]["Arn"]
//                     echo "codeDeployIAMRoleArn: ${codeDeployIAMRoleArn}"
//                     codeDeployIAMRoleID = sh(returnStdout: true, script: "aws iam get-role --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role").trim()["Role"]["RoleId"]
//                     echo "codeDeployIAMRoleID: ${codeDeployIAMRoleID}"
//                     kmsKeyID = outputsMapJson.toString().split(',').findAll { it.contains("kms") }.collect { it.split(':')[1].replaceAll('"', '') }
//                     echo "kmsKeyID: ${kmsKeyID}"
//                     snsTopicArn = outputsMapJson.toString().split(',').findAll { it.contains("sns") }.collect { it.split(':')[1].replaceAll('"', '') }
//                     echo "snsTopicArn: ${snsTopicArn}"
//                 }
                try {
                    def smsLambdaList = outputsMapJson.toString().split(',').findAll { it.contains("lambdaoutput") }.collect { it.split(':')[1].replaceAll('"', '') }
                    def apppecconflist = []
                    def lambdaversionobject = [:]
                    for (lambda in smsLambdaList) {
                        echo "lambda: ${lambda}"
                        lambdaversionobject['lambdaname'] = lambda
                        try {
                            def currentlyPublishedLambdaVersion = sh(returnStdout: true, script: "aws lambda list-versions-by-function --function-name ${lambda} --no-paginate --query \"max_by(Versions, &to_number(to_number(Version) || \'0\'))\"").trim()
                            echo "currentlyPublishedLambdaVersion: ${currentlyPublishedLambdaVersion}"
                            currentlyPublishedLambdaVersion = currentlyPublishedLambdaVersion["Version"]
                        } catch (Exception e) {
                            echo "No published lambda version found"
                            currentlyPublishedLambdaVersion = 0
                        }
                        lambdaversionobject['firstversion'] = currentlyPublishedLambdaVersion
                        def updateFunctionCode = sh(returnStdout: true, script: "aws lambda update-function-code --function-name ${lambda} --s3-bucket ${codeDeployAppSpecBucket} --s3-key ${params.new-${lambda}-s3-key}").trim()
                        echo "updateFunctionCode: ${updateFunctionCode}"
                        sleep time: 5, unit: 'SECONDS'
                        try {
                            def lastPublishedLambdaVersionOutput = sh(returnStdout: true, script: "aws lambda publish-version --function-name ${lambda}").trim()
                            echo "lastPublishedLambdaVersionOutput: ${lastPublishedLambdaVersionOutput}"
                            lastPublishedLambdaVersion = lastPublishedLambdaVersionOutput["Version"]
                            echo "lastPublishedLambdaVersion: ${lastPublishedLambdaVersion}"
                        } catch (Exception e) {
                            echo "No published lambda version found"
                            lastPublishedLambdaVersionOutput = null
                            lastPublishedLambdaVersion = 0
                        }
                        lambdaversionobject['secondversion'] = lastPublishedLambdaVersion
                        if (lastPublishedLambdaVersionOutput != null) {
                            apppecconflist.add(lambdaversionobject)
                            try {
                                def alias = sh(returnStdout: true, script: "aws lambda get-alias --function-name ${lambda} --name deployalias --query \"AliasArn\"").trim()
                                echo "alias: ${alias}"
                            } catch (Exception e) {
                                echo "No alias found"
                                alias = null
                            }
                            if (alias != null) {
                                sh "aws lambda update-alias --function-name ${lambda} --name deployalias --function-version ${currentlyPublishedLambdaVersion}"
                            } else {
                                sh "aws lambda create-alias --function-name ${lambda} --name deployalias --function-version ${currentlyPublishedLambdaVersion}"
                            }
                        }
                    }
                } catch (Exception e) {
                    echo "No lambda found"
                }
                for (i = 0; i < apppecconflist.size(); i++) {
                    echo "apppecconf: ${apppecconf}"
                    def lambdaname = apppecconflist[i]['lambdaname']
                    def firstversion = apppecconflist[i]['firstversion']
                    def secondversion = apppecconflist[i]['secondversion']
                    def keybucketAppspec = "${params.PROGRAM}-${currentEnv}-lc-sms/${lambdaname}/AppSpec.json"
                    def appspecfile = "{\"version\":0.0,   \"resources\":[ { \"${lambdaname}\":{     \"type\":\"AWS::Lambda::Function\",      \"properties\":{  \"name\":\"${lambdaname}\",  \"alias\":\"deployalias\",         \"currentversion\":\"${firstversion}\", \"targetversion\":\"${secondversion}\"            }         }      }   ]}"
                    writeFile file: 'appspec.json', text: appspecfile
                    sh "cat appspec.json"
                    sh "mv appspec.json AppSpecSend${ii}.json"
                    sh "aws s3 cp AppSpecSend${ii}.json s3://${codeDeployAppSpecBucket}/${keybucketAppspec} --sse AES256"
                    apppecconflist[i]['keybucketappspec']=keybucketAppspec
                }
                if (deleteCodeDeployResources == "Yes") {
                    sh "aws deploy delete-application --application-name ${params.PROGRAM}-lc-sms-bg-app"
                    sh "aws deploy delete-deployment-config --deployment-config-name ${params.PROGRAM}-lc-sms-bg-deployment-config"
                    sh "aws deploy delete-deployment-group --application-name ${params.PROGRAM}-lc-sms-bg-app --deployment-group-name ${params.PROGRAM}-lc-sms-bg-deployment-group"
                }
                try {
                    def appCreated = sh(returnStdout: true, script: "aws deploy get-application --application-name ${params.PROGRAM}-lc-sms-bg-app").trim()
                    echo "appCreated: ${appCreated}"
                } catch (Exception e) {
                    echo "No app found"
                    appCreated = null
                }
                if (appCreated == null || appCreated.isEmpty()) {
                    def createAppOutput = sh(returnStdout: true, script: "aws deploy create-application --application-name ${params.PROGRAM}-lc-sms-bg-app --compute-platform Lambda --tags Key=lm_troux_uid,Value=${params.TROUX_UID}").trim()
                    echo "createAppOutput: ${createAppOutput}"
                }
                try {
                    def deploymentConfigCreated = sh(returnStdout: true, script: "aws deploy get-deployment-config --deployment-config-name ${params.PROGRAM}-lc-sms-bg-deployment-config").trim()
                    echo "deploymentConfigCreated: ${deploymentConfigCreated}"
                } catch (Exception e) {
                    echo "No deployment config found"
                    deploymentConfigCreated = null
                }
                if (deploymentConfigCreated == null || deploymentConfigCreated.isEmpty()) {
                    def createDeploymentConfigOutput = sh(returnStdout: true, script: "aws deploy create-deployment-config --deployment-config-name ${params.PROGRAM}-lc-sms-bg-deployment-config --traffic-routing-config type=TimeBasedCanary,timeBasedCanary={canaryPercentage=10,canaryInterval=10} --compute-platform Lambda").trim()
                    echo "createDeploymentConfigOutput: ${createDeploymentConfigOutput}"
                }
                for (i = 0; i < apppecconflist.size(); i++) {
                    echo "apppecconf: ${apppecconf}"
                    def keybucketAppspec = apppecconflist[i]['keybucketappspec']
                    def lambdaname = apppecconflist[i]['lambdaname']
                    def groupname = "${lambdaname}depgroup"
                    try {
                        def deploymentGroupCreated = sh(returnStdout: true, script: "aws deploy get-deployment-group --application-name ${params.PROGRAM}-lc-sms-bg-app --deployment-group-name ${groupname}").trim()
                        echo "deploymentGroupCreated: ${deploymentGroupCreated}"
                    } catch (Exception e) {
                        echo "No deployment group found"
                        deploymentGroupCreated = null
                    }
                    if (deploymentGroupCreated == null || deploymentGroupCreated.isEmpty()) {
                        def createDeploymentGroupOutput = sh(returnStdout: true, script: "aws deploy create-deployment-group --application-name ${params.PROGRAM}-lc-sms-bg-app --deployment-group-name ${groupname} --deployment-config-name ${params.PROGRAM}-lc-sms-bg-deployment-config --service-role-arn ${codeDeployIAMRoleArn} --trigger-configurations triggerName=${PARAMS.PROGRAM}-lc-sms-bg-trigger,triggerTargetArn=${snsTopicArn},triggerEvents=DeploymentStart,DeploymentSuccess,DeploymentFailure,DeploymentStop,DeploymentRollback,DeploymentReady --auto-rollback-configuration enabled=true,events=DEPLOYMENT_FAILURE --deployment-style deploymentType=BLUE_GREEN,deploymentOption=WITH_TRAFFIC_CONTROL --tags Key=lm_troux_uid,Value=${params.TROUX_UID}").trim()
                        echo "createDeploymentGroupOutput: ${createDeploymentGroupOutput}"
                    }
                    def registerApplicationRevisionOutput = sh(returnStdout: true, script: "aws deploy register-application-revision --application-name ${params.PROGRAM}-lc-sms-bg-app --description ${params.PROGRAM}-deploymentdesc --revision revisionType=S3,s3Location={bucket=${codeDeployAppSpecBucket},bundleType=json,key=${keybucketAppspec}").trim()
                    echo "registerApplicationRevisionOutput: ${registerApplicationRevisionOutput}"
                    def createDeploymentOutput = sh(returnStdout: true, script: "aws deploy create-deployment --application-name ${params.PROGRAM}-lc-sms-bg-app --deployment-config-name ${params.PROGRAM}-lc-sms-bg-deployment-config --deployment-group-name ${groupname} --s3-location bucket=${codeDeployAppSpecBucket},bundleType=json,key=${keybucketAppspec} --description ${params.PROGRAM}-deploymentdesc --auto-rollback-configuration enabled=true,events=DEPLOYMENT_FAILURE").trim()
                    echo "createDeploymentOutput: ${createDeploymentOutput}"
                    def deploymentId = createDeploymentOutput.split('deploymentId:')[1].split(',')[0].trim()
                    echo "deploymentId: ${deploymentId}"
                    sleep time: 10, unit: 'SECONDS'
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
    }
}
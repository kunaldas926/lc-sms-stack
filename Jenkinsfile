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
            name: 'connector-lambda-s3-key'
        ),

        string(
            defaultValue: "code/sms/sms-processor-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'processor-lambda-s3-key'
        ),

        string(
            defaultValue: "code/sms/sms-mapper-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'mapper-lambda-s3-key'
        ),

        string(
            defaultValue: "code/sms/sms-retry-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'retry-lambda-s3-key'
        ),

        string(
            defaultValue: "code/sms/sms-status-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'status-lambda-s3-key'
        ),

        string(
            defaultValue: "code/sms/sms-dlq-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'dlq-lambda-s3-key'
        ),

        string(
            defaultValue: "code/sms/sms-dbconnector-lambda-0.0.1-RELEASE.jar",
            description: 'bucket key for lambda jar',
            name: 'db-connector-lambda-s3-key'
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
			-connectorLambdaS3Key ${params.sms-connector-lambda-s3-key} \
			-processorLambdaS3Key ${params.processor-lambda-s3-key} \
			-mapperLambdaS3Key ${params.mapper-lambda-s3-key} \
			-dbConnectorLambdaS3Key ${params.db-connector-lambda-s3-key} \
			-retryLambdaS3Key ${params.retry-lambda-s3-key} \
			-smsStatusLambdaS3Key ${params.status-lambda-s3-key} \
			-dlqLambdaS3Key ${params.dlq-lambda-s3-key} \
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
            sh "npm install -g aws-cdk@2.24.1"
        	sh "npm install -g n@8.2.0"
        	sh "n 16.15.1"
        	sh "mvn spotless:apply"
        	sh "mvn clean install"
        }

        stage ("deploy") {
            def currentEnv = getEnvFromBuildPath(env.JOB_NAME)
            def accountId = getAwsAccountId()
            def region = getAWSRegion()
            def codeDeployAppSpecBucket = "intl-${currentEnv}-apacreg-${region}-code-deploy"
            def outputsMap = [:]

            withAWS(credentials: getAWSCredentialID(environment: currentEnv), region: getAWSRegion()) {
                deployCdk(currentEnv, accountId, region)
            }
        }

        stage ("Populate CDK Outputs") {
            def outputs = sh(returnStdout: true, script: "aws cloudformation describe-stacks --stack-name ${params.PROGRAM}-${currentEnv}-lc-sms-stack --no-paginate").trim()
            def outputsJson = readJSON text: outputs
            outputsMap = outputsJson.Stacks[0].Outputs.collectEntries { ["${it.OutputKey}", "${it.OutputValue}"] }
            echo "outputsMap: ${outputsMap}"
            outputsMapJson = groovy.json.JsonOutput.toJson(outputsMap)
            echo "outputsMapJson: ${outputsMapJson}"
            def kmsKekmsKeyID = outputsMapJson.findFirst { it.key.contains("kms") }
            echo "kmsKekmsKeyID: ${kmsKekmsKeyID}"
            def snsTopicArn = outputsMapJson.findFirst { it.key.contains("sns") }
            echo "snsTopicArn: ${snsTopicArn}"
            def smsLambdaList = outputsMapJson.toString().split(',').findAll { it.contains("lambdaoutput") }.collect { it.split(':')[1].replaceAll('"', '') }
            echo "smsLambdaList: ${smsLambdaList}"
            def apppecconflist = []
            def lambdaversionobject = [:]
            def lambdaS3KeyList = []

        }

        stage ("Update CodeDeploy Service Role and KMS Policy") {
            def codeDeployIAMRoleArn = null
            def codeDeployIAMRoleID = null
            try {
                def getRoleOutput = sh(returnStdout: true, script: "aws iam get-role --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role").trim()
                echo "getRoleOutput: ${getRoleOutput}"
                codeDeployIAMRoleArn = getRoleOutput["Role"]["Arn"]
                echo "codeDeployIAMRoleArn: ${codeDeployIAMRoleArn}"
                codeDeployIAMRoleID = getRoleOutput["Role"]["RoleId"]
                echo "codeDeployIAMRoleID: ${codeDeployIAMRoleID}"
            } catch (Exception e) {
                echo "Role ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role does not exist"
                echo "caught exception: ${e}"
                def createRoleOutput = sh(returnStdout: true, script: "aws iam create-role --role-name ${params.PROGRAM}-${currentEnv}-lc-sms-bg-role --assume-role-policy-document file://./assume-role-policy.json --tags Key=lm_troux_uid,Value=${params.TROUX_UID} Key=aws_iam_permission_boundary_exempt,Value=true").trim()
                echo "createRoleOutput: ${createRoleOutput}"
                codeDeployIAMRoleArn = createRoleOutput["Role"]["Arn"]
                echo "codeDeployIAMRoleArn: ${codeDeployIAMRoleArn}"
                codeDeployIAMRoleID = createRoleOutput["Role"]["RoleId"]
                echo "codeDeployIAMRoleID: ${codeDeployIAMRoleID}"
            }
            try {
                def kMSKeyPloicy = readJson file: './kms-key-policy.json'
                kMSKeyPloicy["Statement"][0]["Principal"]["AWS"] = "arn:aws:iam::${accountId}:root"
                kMSKeyPloicy["Statement"][1]["Principal"]["AWS"] = "arn:aws:iam::${accountId}:root"
                kMSKeyPloicy["Statement"][2]["Principal"]["AWS"] = codeDeployIAMRoleArn
                writeJSON file: './kms-key-policy.json', json: kMSKeyPloicy
                sh "aws kms put-key-policy --key-id ${kmsKeyID} --policy-name default --policy file://./kms-key-policy.json"
            } catch (Exception e) {
                echo "caught exception: ${e}"
            }
        }
        stage ("Update S3 Bucket Policy") {
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
            sh "rm -rf policy.json"
        }
        stage ("Update Lambda Versions") {
            for (lambda in smsLambdaList) {
                echo "lambda: ${lambda}"
                lambdaversionobject['lambdaname'] = lambda
                try {
                    def currentlyPublishedLambdaVersionOutput = sh(returnStdout: true, script: "aws lambda list-versions-by-function --function-name ${lambda} --no-paginate --query \"max_by(Versions, &to_number(to_number(Version) || \'0\'))\"").trim()
                    echo "currentlyPublishedLambdaVersion: ${currentlyPublishedLambdaVersion}"
                    def currentlyPublishedLambdaVersionOutputJson = readJSON(text: currentlyPublishedLambdaVersionOutput)
                    echo "currentlyPublishedLambdaVersionOutputJson: ${currentlyPublishedLambdaVersionOutputJson}"
                    def currentlyPublishedLambdaVersion = currentlyPublishedLambdaVersion["Version"]
                    echo "currentlyPublishedLambdaVersion: ${currentlyPublishedLambdaVersion}"
                } catch (Exception e) {
                    echo "No published lambda version found"
                    currentlyPublishedLambdaVersion = 0
                }
                lambdaversionobject['firstversion'] = currentlyPublishedLambdaVersion

                // lambda = reg-dev-sms-connector-lambda
                // key = connector-lambda-s3-key

                // params = connector-lambda-s3-key

                // ${} =  code/sms/sms-connector-lambda-0.0.1-RELEASE.jar

                def s3Key = "${lambda}".split("reg-${currentEnv}")[1]
                s3Key = "params.${s3Key}"
                s3Key = "${s3Key}"
                def updateFunctionCode = sh(returnStdout: true, script: "aws lambda update-function-code --function-name ${lambda} --s3-bucket ${codeDeployAppSpecBucket} --s3-key ${s3Key}").trim()
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
        }
        stage ("Update CodeDeploy AppSpec") {
            for (ii = 0; ii < apppecconflist.size(); ii++){
                def lambdanamecompl= apppecconflist[ii]['lambdaname']
                def firstversionn= apppecconflist[ii]['firstversion']
                def secondversionn= apppecconflist[ii]['secondversion']
                /*def secondversionn= firstversionn.toInteger()+1;*/
                def keybucketAppspec = "${appname}/${lambdanamecompl}/AppSpec.json"

                appspecfile = "{\"version\":0.0,   \"resources\":[ { \"${lambdanamecompl}\":{     \"type\":\"AWS::Lambda::Function\",      \"properties\":{  \"name\":\"${lambdanamecompl}\",  \"alias\":\"deployalias\",         \"currentversion\":\"${firstversionn}\", \"targetversion\":\"${secondversionn}\"            }         }      }   ]}"

                writeFile file: 'AppSpec1.json', text: appspecfile
                sh "cat AppSpec1.json"

                sh "mv AppSpec1.json AppSpecSend${ii}.json"

                sh "aws s3 cp AppSpecSend${ii}.json s3://${s3bucketName}/${keybucketAppspec} --sse AES256"
                apppecconflist[ii]['keybucketappspec']=keybucketAppspec
            }
        }
        stage ("Update CodeDeploy Specific Resources") {
            try {
                def appCreated = sh(returnStdout: true, script: "aws deploy get-application --application-name ${params.PROGRAM}-lc-sms-bg-app").trim()
                echo "appCreated: ${appCreated}"
            } catch (Exception e) {
                echo "caught exception: ${e}"
                def createAppOutput = sh(returnStdout: true, script: "aws deploy create-application --application-name ${params.PROGRAM}-lc-sms-bg-app --compute-platform Lambda --tags Key=lm_troux_uid,Value=${params.TROUX_UID}").trim()
                echo "createAppOutput: ${createAppOutput}"
            }
            try {
                def deploymentConfigCreated = sh(returnStdout: true, script: "aws deploy get-deployment-config --deployment-config-name ${params.PROGRAM}-lc-sms-bg-deployment-config").trim()
                echo "deploymentConfigCreated: ${deploymentConfigCreated}"
            } catch (Exception e) {
                echo "caught exception: ${e}"
                def createDeploymentConfigOutput = sh(returnStdout: true, script: "aws deploy create-deployment-config --deployment-config-name ${params.PROGRAM}-lc-sms-bg-deployment-config --traffic-routing-config type=TimeBasedCanary,timeBasedCanary={canaryPercentage=10,canaryInterval=10} --compute-platform Lambda").trim()
                echo "createDeploymentConfigOutput: ${createDeploymentConfigOutput}"
            }
        }
        stage ("Create Deployment") {
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

package com.lmig.libertyconnect.sms.stack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;

import com.lmig.libertyconnect.sms.stack.LcSmsStackApp.Args;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.apigateway.EndpointConfiguration;
import software.amazon.awscdk.services.apigateway.EndpointType;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.Resource;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.apigateway.StageOptions;
import software.amazon.awscdk.services.ec2.LaunchTemplate;
import software.amazon.awscdk.services.ec2.MachineImage;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcLookupOptions;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyDocument;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.kms.Key;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.IEventSource;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.eventsources.SqsEventSource;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.sqs.QueueEncryption;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.amazon.awscdk.services.stepfunctions.JsonPath;
import software.amazon.awscdk.services.stepfunctions.Parallel;
import software.amazon.awscdk.services.stepfunctions.StateMachine;
import software.amazon.awscdk.services.stepfunctions.TaskInput;
import software.amazon.awscdk.services.stepfunctions.tasks.LambdaInvoke;
import software.amazon.awscdk.services.stepfunctions.tasks.SnsPublish;

public class LcSmsStack extends Stack {

	public LcSmsStack(final Construct parent, final String id, final StackProps props, final Args ARGS) {
		super(parent, id, props);

		// create kms key
        final Key smsStackKey =
                Key.Builder.create(this, ARGS.getPrefixedName("lc-sms-key"))
                        .enableKeyRotation(true)
                        .alias("alias/lc-sms-key")
                        .policy(getPolicyDocument())
                        .build();
        
        // create security group
        /* final LaunchTemplate template = LaunchTemplate.Builder.create(this, "LaunchTemplate")
                .machineImage(MachineImage.latestAmazonLinux())
                .securityGroup(SecurityGroup.Builder.create(this, ARGS.getPrefixedName("lc-sms-sg"))
                		.securityGroupName(ARGS.getPrefixedName("lc-sms-sg"))
                		.allowAllOutbound(true)
                        .vpc(Vpc.fromLookup(this, id, VpcLookupOptions.builder().isDefault(false).build()))
                        .build())
                .build(); */
        final SecurityGroup sg = SecurityGroup.Builder.create(this, ARGS.getPrefixedName("lc-sms-sg"))
				.securityGroupName(ARGS.getPrefixedName("lc-sms-sg"))
				.allowAllOutbound(false)
		        .vpc(Vpc.fromLookup(this, id, VpcLookupOptions.builder().isDefault(false).build()))
		        .build();
        
		// create queue
		final String queueName = ARGS.getPrefixedName("lc-sms-queue.fifo");
		final Queue queue = Queue.Builder.create(this, queueName).queueName(queueName)
				.retentionPeriod(Duration.days(14))
				.fifo(true)
				.encryption(QueueEncryption.KMS_MANAGED)
				.visibilityTimeout(Duration.minutes(6))
				.build();

		// create lambda, roles and permissions
		final PolicyStatement statement1 = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("sqs:ListQueues", "sqs:SendMessage", "sqs:ReceiveMessage", "sqs:DeleteMessage",
								"sqs:GetQueueAttributes", "sqs:ChangeMessageVisibility", "sqs:GetQueueUrl"))
				.resources(Arrays.asList("*")).build();

		final PolicyStatement statement2 = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays
						.asList("logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents" ))
				.resources(Arrays.asList("arn:aws:logs:*:*:*" )).build();

		final PolicyStatement statement3 = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("kms:Decrypt",
						"kms:GenerateDataKey",
						"secretsmanager:GetSecretValue",
						"ec2:DescribeNetworkInterfaces",
				        "ec2:CreateNetworkInterface",
				        "ec2:DeleteNetworkInterface",
				        "ec2:DescribeInstances",
				        "ec2:AttachNetworkInterface",
				        "states:StartExecution"
				)).resources(Arrays.asList( "*" ))
				.build();

		final PolicyDocument policyDocument = PolicyDocument.Builder.create()
				.statements(Arrays.asList(statement1, statement2, statement3)).build();

		final Role lambdaRole = Role.Builder.create(this, ARGS.getPrefixedName("lc-sms-lambda-role"))
				.roleName(ARGS.getPrefixedName("lc-sms-lambda-role"))
				.inlinePolicies(Collections.singletonMap(ARGS.getPrefixedName("lc-sqsS3-policy"), policyDocument)).path("/")
				.assumedBy(new ServicePrincipal("lambda.amazonaws.com")).build();

		final List<IEventSource> eventSources = new ArrayList<>();
		eventSources.add(SqsEventSource.Builder.create(queue).batchSize(1).enabled(true).build());

		final Map<String, String> envsMap = new HashMap<>();
		envsMap.put("PROGRAM", ARGS.program);
		envsMap.put("ENV", ARGS.getProfile());
		
		final Function smsProcessorLambda = Function.Builder.create(this, ARGS.getPrefixedName("lc-sms-processor-lambda"))
				.functionName(ARGS.getPrefixedName("lc-sms-processor-lambda"))
				.code(Code.fromBucket(Bucket.fromBucketName(this, "sms-processor", ARGS.getPrefixedName("lc-sms")),
						ARGS.getProcessorLambdaS3Key()))
				.environment(envsMap)
				.handler("com.lmig.libertyconnect.sms.processor.handler.LambdaHandler").role(lambdaRole)
				.runtime(Runtime.JAVA_11).memorySize(1024).timeout(Duration.minutes(5)).events(eventSources).build();
		
	
		final Function smsConnectorLambda = Function.Builder.create(this, ARGS.getPrefixedName("lc-sms-connector-lambda"))
				.code(Code.fromBucket(Bucket.fromBucketName(this, "sms-connector", ARGS.getPrefixedName("lc-sms")),
						ARGS.getConnectorLambdaS3Key()))
				.environment(envsMap)
				.vpc(Vpc.fromLookup(this, ARGS.getPrefixedName("lc-sms-connector-vpc"), VpcLookupOptions.builder().isDefault(false).build()))
				.securityGroups(Arrays.asList(sg))
				.functionName(ARGS.getPrefixedName("lc-sms-connector-lambda"))
				.handler("com.lmig.libertyconnect.sms.connector.handler.SMSConnectorHandler").role(lambdaRole)
				.runtime(Runtime.JAVA_11).memorySize(1024).timeout(Duration.minutes(5)).build();
	
		 envsMap.put("db_host", "intl-sg-apac-liberty-connect-rds-mysql-dev-dbproxy.proxy-cvluefal1end.ap-southeast-1.rds.amazonaws.com");
		 envsMap.put("port", "3306");
		 envsMap.put("secret_id", "apac-liberty-connect-rds-stack/mysql/intl-sg-apac-liberty-connect-rds-mysql-dev/libcdbuser/libconnnectdb22");
		 envsMap.put("vpc_endpoint_url_ssm", "intl-cs-sm-vpc-endpoint-url");
		 envsMap.put("db_name", "libertyconnect");
		 final Function smsDbConnectorLmbda = Function.Builder.create(this, ARGS.getPrefixedName("lc-sms-db-connector-lambda"))
				.code(Code.fromBucket(Bucket.fromBucketName(this, "sms-db-connector", ARGS.getPrefixedName("lc-sms")),
						ARGS.getDbConnectorLambdaS3Key()))
				.environment(envsMap)
				.vpc(Vpc.fromLookup(this, ARGS.getPrefixedName("lc-sms-db-connector-vpc"), VpcLookupOptions.builder().isDefault(false).build()))
				//.securityGroups(Arrays.asList(SecurityGroup.fromSecurityGroupId(this, ARGS.getPrefixedName("lc-sms-db-connector-sg"), "sg-0aab289f68432c664")))
				.securityGroups(Arrays.asList(sg))
				.functionName(ARGS.getPrefixedName("lc-sms-db-connector-lambda"))
				.handler("com.lmig.libertyconnect.sms.updatedb.handler.SMSDBConnectorHandler::handleRequest")
				.role(lambdaRole)
				.runtime(Runtime.JAVA_11).memorySize(1024).timeout(Duration.minutes(15)).build();
		
		// Create Topic
		final Topic responseTopic =
                 Topic.Builder.create(this, ARGS.getPrefixedName("lc-sms-response-topic"))
                         .topicName(ARGS.getPrefixedName("lc-sms-response-topic"))
                         .masterKey(smsStackKey)
                         .build();
		
		// Create step function to invoke dbConnector Lambda and send response to sns		
		final PolicyStatement kmsStatement = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("kms:Decrypt",
						"kms:GenerateDataKey"))
				.resources(Arrays.asList(smsStackKey.getKeyArn()))
				.build();

		final PolicyDocument stateMachinePolicyDocument = PolicyDocument.Builder.create()
				.statements(Arrays.asList(kmsStatement)).build();

		final Role stateMachineRole = Role.Builder.create(this, ARGS.getPrefixedName("lc-sms-statemachine-role"))
				.roleName(ARGS.getPrefixedName("lc-sms-statemachine-role"))
				.inlinePolicies(Collections.singletonMap(ARGS.getPrefixedName("lc-kms-policy"), stateMachinePolicyDocument)).path("/")
				.assumedBy(new ServicePrincipal("lambda.amazonaws.com")).build();
		final Map<String, String> snsMsgFieldsMap = new HashMap<>();
		snsMsgFieldsMap.put("client_reference_number", JsonPath.stringAt("$.client_reference_number"));
		snsMsgFieldsMap.put("uuid", JsonPath.stringAt("$.uuid"));
		snsMsgFieldsMap.put("response", JsonPath.stringAt("$.response"));
		final Parallel parallelStates = new Parallel(this, ARGS.getPrefixedName("lc-sms-parallel"))
        		.branch(LambdaInvoke.Builder.create(this, ARGS.getPrefixedName("lc-sms-db-connector-lambda-task"))
    		            .lambdaFunction(smsDbConnectorLmbda)	            
    		            .build())
        		.branch(SnsPublish.Builder.create(this, ARGS.getPrefixedName("lc-sms-publish-task"))
        		         .topic(responseTopic)
        		         .message(TaskInput.fromObject(snsMsgFieldsMap))
        		         .build());

		final StateMachine stateMachine = StateMachine.Builder.create(this, ARGS.getPrefixedName("lc-sms-statemachine"))
				.stateMachineName(ARGS.getPrefixedName("lc-sms-statemachine"))
		        .definition(parallelStates)
		        .role(stateMachineRole)
		        .build();
		
		// Create SSM parameter for vietguys
		StringParameter vietGuysparam = StringParameter.Builder.create(this, ARGS.getPrefixedName("lc-sms-vietguys-ssm"))
				 .parameterName(ARGS.getPrefixedName("lc-sms-vietguys-cred"))
		         .stringValue(new String(Base64.encodeBase64(ARGS.getVietguyPass().getBytes())))
		         .build();
		vietGuysparam.grantRead(lambdaRole);

		// Create SSM parameter for dtac
		StringParameter dtacParam = StringParameter.Builder.create(this, ARGS.getPrefixedName("lc-sms-dtac-ssm"))
				 .parameterName(ARGS.getPrefixedName("lc-sms-dtac-cred"))
		         .stringValue(new String(Base64.encodeBase64(ARGS.getDtacPass().getBytes())))
		         .build();
		dtacParam.grantRead(lambdaRole);
		 
		// Create Rest API Gateway
		final PolicyStatement apiStatement = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("execute-api:Invoke"))
				.resources(Arrays.asList( "*" ))
				.build();
		apiStatement.addAnyPrincipal();

		final PolicyDocument apiPolicyDocument = PolicyDocument.Builder.create()
				.statements(Arrays.asList(apiStatement)).build();

		final RestApi api =
		        RestApi.Builder.create(this, ARGS.getPrefixedName("lc-sms-gateway"))	        
		        .restApiName(ARGS.getPrefixedName("lc-sms-api"))
		        .endpointConfiguration(EndpointConfiguration.builder()
		                 .types(Arrays.asList(EndpointType.PRIVATE))		                 
		                 .build())	
		        .policy(apiPolicyDocument)		        
		        .deployOptions(StageOptions.builder().stageName(ARGS.getProfile()).build())
		        .cloudWatchRole(false)
		        
		        .build();
		final Resource smsResource = api.getRoot().addResource("sms");
	    final LambdaIntegration getWidgetIntegration =
	        LambdaIntegration.Builder.create(smsConnectorLambda)
	        .build();

	    smsResource.addMethod("POST", getWidgetIntegration);
		
	}
	

    private PolicyDocument getPolicyDocument() {

        final PolicyDocument policyDocument = new PolicyDocument();
        policyDocument.addStatements(getSnsStatement(), getIamPolicyStatement());
        return policyDocument;
    }


    private PolicyStatement getSnsStatement() {

        final PolicyStatement policyStatement = new PolicyStatement();
        policyStatement.addActions("kms:GenerateDataKey*", "kms:Decrypt");
        policyStatement.addServicePrincipal("sns.amazonaws.com");
        policyStatement.addAllResources();
        return policyStatement;
    }


    private PolicyStatement getIamPolicyStatement() {

        final PolicyStatement iamUserPermission = new PolicyStatement();
        iamUserPermission.addActions("kms:*");
        iamUserPermission.addAccountRootPrincipal();
        iamUserPermission.addAllResources();
        return iamUserPermission;
    }

}

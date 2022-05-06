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
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcAttributes;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyDocument;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.IEventSource;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.eventsources.SqsEventSource;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.sqs.QueueEncryption;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.amazon.awscdk.services.stepfunctions.StateMachine;
import software.amazon.awscdk.services.stepfunctions.tasks.LambdaInvoke;

public class LcSmsStack extends Stack {

	public LcSmsStack(final Construct parent, final String id, final StackProps props, final Args ARGS) {
		super(parent, id, props);

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
				.vpc(Vpc.fromVpcAttributes(this, ARGS.getPrefixedName("lc-sms-connector-vpc"), VpcAttributes.builder()
						.vpcId("vpc-6d3d8b0a")
						.availabilityZones(Arrays.asList("ap-southeast-1a", "ap-southeast-1b"))
						.privateSubnetIds(Arrays.asList("subnet-3a076f73", "subnet-1a641c7d"))
						.build()))
				.securityGroups(Arrays.asList(SecurityGroup.fromSecurityGroupId(this, ARGS.getPrefixedName("lc-sms-connector-sg"), "sg-018a679bf5214b799")))
				.functionName(ARGS.getPrefixedName("lc-sms-connector-lambda"))
				.handler("com.lmig.libertyconnect.sms.connector.handler.SMSConnectorHandler").role(lambdaRole)
				.runtime(Runtime.JAVA_11).memorySize(1024).timeout(Duration.minutes(5)).build();
	
		 final Function smsDbConnectorLmbda = Function.Builder.create(this, ARGS.getPrefixedName("lc-sms-db-connector-lambda"))
				.code(Code.fromBucket(Bucket.fromBucketName(this, "sms-db-connector", ARGS.getPrefixedName("lc-sms")),
						ARGS.getDbConnectorLambdaS3Key()))
				.environment(envsMap)
				.vpc(Vpc.fromVpcAttributes(this, ARGS.getPrefixedName("lc-sms-db-connector-vpc"), VpcAttributes.builder()
						.vpcId("vpc-6d3d8b0a")
						.availabilityZones(Arrays.asList("ap-southeast-1a", "ap-southeast-1b"))
						.privateSubnetIds(Arrays.asList("subnet-3a076f73", "subnet-1a641c7d"))
						.build()))
				.securityGroups(Arrays.asList(SecurityGroup.fromSecurityGroupId(this, ARGS.getPrefixedName("lc-sms-db-connector-sg"), "sg-018a679bf5214b799")))
				.functionName(ARGS.getPrefixedName("lc-sms-db-connector-lambda"))
				.handler("com.lmig.libertyconnect.sms.updatedb.handler.SMSDBConnectorHandler::handleRequest").role(lambdaRole)
				.runtime(Runtime.JAVA_11).memorySize(1024).timeout(Duration.minutes(15)).build();
		
		// Create step function to invoke dbConnector Lambda
		final StateMachine stateMachine = StateMachine.Builder.create(this, ARGS.getPrefixedName("lc-sms-statemachine"))
				.stateMachineName(ARGS.getPrefixedName("lc-sms-statemachine"))
		        .definition(LambdaInvoke.Builder.create(this, ARGS.getPrefixedName("lc-sms-db-connector-lambda-task"))
		            .lambdaFunction(smsDbConnectorLmbda)
		            .build())
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

}

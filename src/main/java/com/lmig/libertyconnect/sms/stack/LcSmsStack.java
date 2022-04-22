package com.lmig.libertyconnect.sms.stack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

public class LcSmsStack extends Stack {

	public LcSmsStack(final Construct parent, final String id, final StackProps props, final Args ARGS) {
		super(parent, id, props);
		/*
		 * final Key stackKey = Key.Builder.create(parent,
		 * getPrefixedName("lc-sms-key")) .enableKeyRotation(true)
		 * .policy(getPolicyDocument()) .build();
		 */

		// stackKey.addAlias(getPrefixedName("lc-sms-key"));
		final String queueName = ARGS.getPrefixedName("lc-sms-queue.fifo");
		final Queue queue = Queue.Builder.create(this, queueName).queueName(queueName)
				.retentionPeriod(Duration.days(14))
				.fifo(true)
				.encryption(QueueEncryption.KMS_MANAGED)
				.visibilityTimeout(Duration.minutes(6))
				.build();

		// create lambda, roles and permissions
		PolicyStatement statement1 = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("sqs:ListQueues", "sqs:SendMessage", "sqs:ReceiveMessage", "sqs:DeleteMessage",
								"sqs:GetQueueAttributes", "sqs:ChangeMessageVisibility", "sqs:GetQueueUrl"))
				.resources(Arrays.asList( "*" )).build();

		PolicyStatement statement2 = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays
						.asList("logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents" ))
				.resources(Arrays.asList("arn:aws:logs:*:*:*" )).build();

		PolicyStatement statement3 = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("kms:Decrypt",
						"kms:GenerateDataKey",
						"ec2:DescribeNetworkInterfaces",
				        "ec2:CreateNetworkInterface",
				        "ec2:DeleteNetworkInterface",
				        "ec2:DescribeInstances",
				        "ec2:AttachNetworkInterface"
				)).resources(Arrays.asList( "*" ))
				.build();

		PolicyDocument policyDocument = PolicyDocument.Builder.create()
				.statements(Arrays.asList(new PolicyStatement[] { statement1, statement2, statement3 })).build();

		Role lambdaRole = Role.Builder.create(this, ARGS.getPrefixedName("lc-lambda-role"))
				.roleName(ARGS.getPrefixedName("lc-lambda-role"))
				.inlinePolicies(Collections.singletonMap(ARGS.getPrefixedName("lc-sqsS3-policy"), policyDocument)).path("/")
				.assumedBy(new ServicePrincipal("lambda.amazonaws.com")).build();

		List<IEventSource> eventSources = new ArrayList<>();
		eventSources.add(SqsEventSource.Builder.create(queue).batchSize(1).enabled(true).build());

		final Function smsProcessorLambda = Function.Builder.create(this, ARGS.getPrefixedName("lc-sms-processor-lambda"))
				.functionName(ARGS.getPrefixedName("lc-sms-processor-lambda"))
				.code(Code.fromBucket(Bucket.fromBucketName(this, "sms-processor", ARGS.getPrefixedName("lc-sms")),
						ARGS.getProcessorLambdaS3Key()))
				.handler("com.lmig.libertyconnect.sms.processor.handler.LambdaHandler").role(lambdaRole)
				.runtime(Runtime.JAVA_11).memorySize(1024).timeout(Duration.minutes(5)).events(eventSources).build();
		
	
		final Function smsConnectorLambda = Function.Builder.create(this, ARGS.getPrefixedName("lc-sms-connector-lambda"))
				.code(Code.fromBucket(Bucket.fromBucketName(this, "sms-connector", ARGS.getPrefixedName("lc-sms")),
						ARGS.getConnectorLambdaS3Key()))
				.vpc(Vpc.fromVpcAttributes(this, ARGS.getPrefixedName("lc-sms-vps"), VpcAttributes.builder()
						.vpcId("vpc-6d3d8b0a")
						.availabilityZones(Arrays.asList("ap-southeast-1a", "ap-southeast-1b"))
						.privateSubnetIds(Arrays.asList("subnet-3a076f73", "subnet-1a641c7d"))
						.build()))
				.securityGroups(Arrays.asList(SecurityGroup.fromSecurityGroupId(this, ARGS.getPrefixedName("lc-sms-sg"), "sg-018a679bf5214b799")))
				.functionName(ARGS.getPrefixedName("lc-sms-connector-lambda"))
				.handler("com.lmig.libertyconnect.sms.connector.handler.SMSConnectorHandler").role(lambdaRole)
				.runtime(Runtime.JAVA_11).memorySize(1024).timeout(Duration.minutes(5)).build();

		
		// Defines an API Gateway REST API resource backed by our "connector" function
		
		/*
		 * LambdaRestApi api = LambdaRestApi.Builder.create(this,
		 * getPrefixedName("lc-sms-gateway"))
		 * .restApiName(getPrefixedName("lc-sms-api")) .cloudWatchRole(false)
		 * .endpointTypes(Arrays.asList(EndpointType.PRIVATE)) .defaultIntegration(
		 * 
		 * .type(IntegrationType.AWS_PROXY) .build()) .handler(smsConnectorLambda)
		 * .build();
		 */
		 
	
		PolicyStatement apiStatement = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList(new String[] { "execute-api:Invoke" }))
				.resources(Arrays.asList(new String[] { "*" }))
				.build();
		apiStatement.addAnyPrincipal();

		PolicyDocument apiPolicyDocument = PolicyDocument.Builder.create()
				.statements(Arrays.asList(new PolicyStatement[] { apiStatement })).build();

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

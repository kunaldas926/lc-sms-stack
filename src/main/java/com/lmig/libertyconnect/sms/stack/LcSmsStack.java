package com.lmig.libertyconnect.sms.stack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;

import com.lmig.libertyconnect.sms.stack.LcSmsStackApp.Args;
import com.lmig.libertyconnect.sms.stack.utils.Constants;
import com.lmig.libertyconnect.sms.stack.utils.UtilMethods;

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
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetSelection;
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
		final Key smsStackKey = Key.Builder.create(this, ARGS.getPrefixedName("key")).enableKeyRotation(true)
				.alias(ARGS.getPrefixedName("alias/key")).policy(getPolicyDocument()).build();

		// create security group
		final SecurityGroup sg = SecurityGroup.Builder.create(this, ARGS.getPrefixedName("sg"))
				.securityGroupName(ARGS.getPrefixedName("sg")).allowAllOutbound(true)
				.vpc(Vpc.fromLookup(this, id, VpcLookupOptions.builder().isDefault(false).build())).build();

		// create queue
		final String queueName = ARGS.getPrefixedName("queue.fifo");
		final Queue queue = Queue.Builder.create(this, queueName).queueName(queueName)
				.retentionPeriod(Duration.days(14)).fifo(true).encryption(QueueEncryption.KMS_MANAGED)
				.visibilityTimeout(Duration.minutes(6)).build();

		// create lambda, roles and permissions
		final PolicyStatement sqsStatement = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("sqs:ListQueues", "sqs:SendMessage", "sqs:ReceiveMessage", "sqs:DeleteMessage",
						"sqs:GetQueueAttributes", "sqs:ChangeMessageVisibility", "sqs:GetQueueUrl"))
				.resources(Arrays.asList(queue.getQueueArn())).build();

		final PolicyStatement logStatement = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"))
				.resources(Arrays.asList("arn:aws:logs:*:*:*")).build();

		final PolicyStatement policyStatement = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("kms:Decrypt", "kms:GenerateDataKey", "secretsmanager:GetResourcePolicy",
						"secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret",
						"secretsmanager:ListSecretVersionIds", "secretsmanager:ListSecrets",
						"ec2:DescribeNetworkInterfaces", "ec2:CreateNetworkInterface", "ec2:DeleteNetworkInterface",
						"ec2:DescribeInstances", "ec2:AttachNetworkInterface", "states:StartExecution"))
				.resources(Arrays.asList("*")).build();

		final PolicyDocument processorPolicyDocument = PolicyDocument.Builder.create()
				.statements(Arrays.asList(sqsStatement, policyStatement, logStatement)).build();

		final Role processorLambdaRole = Role.Builder.create(this, ARGS.getPrefixedName("processor-lambda-role"))
				.roleName(ARGS.getPrefixedName("processor-lambda-role"))
				.inlinePolicies(Collections.singletonMap(ARGS.getPrefixedName("processor-lambda-policy"),
						processorPolicyDocument))
				.path("/").assumedBy(new ServicePrincipal("lambda.amazonaws.com")).build();

		final PolicyDocument connectorPolicyDocument = PolicyDocument.Builder.create()
				.statements(Arrays.asList(sqsStatement, policyStatement, logStatement)).build();

		final Role connectorLambdaRole = Role.Builder.create(this, ARGS.getPrefixedName("connector-lambda-role"))
				.roleName(ARGS.getPrefixedName("connector-lambda-role"))
				.inlinePolicies(Collections.singletonMap(ARGS.getPrefixedName("connector-lambda-policy"),
						connectorPolicyDocument))
				.path("/").assumedBy(new ServicePrincipal("lambda.amazonaws.com")).build();

		final PolicyDocument dbconnectorPolicyDocument = PolicyDocument.Builder.create()
				.statements(Arrays.asList(policyStatement, logStatement)).build();

		final Role dbconnectorLambdaRole = Role.Builder.create(this, ARGS.getPrefixedName("dbconnector-lambda-role"))
				.roleName(ARGS.getPrefixedName("dbconnector-lambda-role"))
				.inlinePolicies(Collections.singletonMap(ARGS.getPrefixedName("dbconnector-lambda-policy"),
						dbconnectorPolicyDocument))
				.path("/").assumedBy(new ServicePrincipal("lambda.amazonaws.com")).build();

		final IVpc vpc = Vpc.fromLookup(this, ARGS.getPrefixedName("vpc"),
				VpcLookupOptions.builder().isDefault(false).build());

		final List<IEventSource> eventSources = new ArrayList<>();
		eventSources.add(SqsEventSource.Builder.create(queue).batchSize(1).enabled(true).build());

		final Map<String, String> envsMap = new HashMap<>();
		envsMap.put("PROGRAM", ARGS.program);
		envsMap.put("ENV", ARGS.getProfile());
		envsMap.put("ACCOUNT_ID", ARGS.getAccountId());
		envsMap.put("REGION", ARGS.getRegion());

		final Function smsProcessorLambda = Function.Builder.create(this, ARGS.getPrefixedName("processor-lambda"))
				.functionName(ARGS.getPrefixedName("processor-lambda"))
				.code(Code.fromBucket(
						Bucket.fromBucketName(this, "sms-processor", UtilMethods.getCodeBucket(ARGS.getProfile())),
						ARGS.getProcessorLambdaS3Key()))
				.environment(envsMap).handler("com.lmig.libertyconnect.sms.processor.handler.LambdaHandler")
				.role(processorLambdaRole).runtime(Runtime.JAVA_11).memorySize(1024).timeout(Duration.minutes(5))
				.events(eventSources).build();

		envsMap.put("openl_url",
				"https://dev-east-openl-asiamcm.lmig.com/openl-api/REST/LibertyConnect/LibertyConnect/SMSConfig");
		final Function smsConnectorLambda = Function.Builder.create(this, ARGS.getPrefixedName("connector-lambda"))
				.code(Code.fromBucket(
						Bucket.fromBucketName(this, "sms-connector", UtilMethods.getCodeBucket(ARGS.getProfile())),
						ARGS.getConnectorLambdaS3Key()))
				.environment(envsMap).vpc(vpc)
				// .vpc(Vpc.fromLookup(this, ARGS.getPrefixedName("connector-vpc"),
				// VpcLookupOptions.builder().isDefault(false).build()))
				.vpcSubnets(SubnetSelection.builder().onePerAz(true).build()).securityGroups(Arrays.asList(sg))
				.functionName(ARGS.getPrefixedName("connector-lambda"))
				.handler("com.lmig.libertyconnect.sms.connector.handler.SMSConnectorHandler").role(connectorLambdaRole)
				.runtime(Runtime.JAVA_11).memorySize(1024).timeout(Duration.minutes(5)).build();

		envsMap.remove("openl_url");
		envsMap.put("db_host", "intl-sg-apac-liberty-connect-rds-mysql-" + ARGS.getProfile()
				+ "-dbproxy.proxy-cvluefal1end.ap-southeast-1.rds.amazonaws.com");
		envsMap.put("port", "3306");
		envsMap.put("secret_id", "apac-liberty-connect-rds-stack/mysql/intl-sg-apac-liberty-connect-rds-mysql-"
				+ ARGS.getProfile() + "/libcdbuser/libconnnectdb22");
		envsMap.put("vpc_endpoint_url_ssm", "intl-cs-sm-vpc-endpoint-url");
		envsMap.put("db_name", "libertyconnect");
		final Function smsDbConnectorLambda = Function.Builder.create(this, ARGS.getPrefixedName("dbconnector-lambda"))
				.code(Code.fromBucket(
						Bucket.fromBucketName(this, "sms-dbconnector", UtilMethods.getCodeBucket(ARGS.getProfile())),
						ARGS.getDbConnectorLambdaS3Key()))
				.environment(envsMap).vpc(vpc).vpcSubnets(SubnetSelection.builder().onePerAz(true).build())
				// .securityGroups(
				// Arrays.asList(SecurityGroup.fromLookupByName(this,
				// ARGS.getPrefixedName("dbconnector-sg"),
				// "intl-sg-apac-liberty-connect-Lambda-" + ARGS.getProfile(), vpc)))
				.securityGroups(Arrays.asList(sg)).functionName(ARGS.getPrefixedName("dbconnector-lambda"))
				.handler("com.lmig.libertyconnect.sms.updatedb.handler.SMSDBConnectorHandler::handleRequest")
				// .role(Role.fromRoleName(this,
				// ARGS.getPrefixedName("dbconnector-lambda-role"),
				// "apac-liberty-connect-role"))
				.role(dbconnectorLambdaRole).runtime(Runtime.JAVA_11).memorySize(1024).timeout(Duration.minutes(15))
				.build();

		// Create Topic
		final Topic responseTopic = Topic.Builder.create(this, ARGS.getPrefixedName("response-topic"))
				.topicName(ARGS.getPrefixedName("response-topic")).masterKey(smsStackKey).build();

		// Create step function to invoke dbConnector Lambda and send response to sns
		final PolicyStatement sfnStatement = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("kms:Decrypt", "kms:GenerateDataKey", "sts:AssumeRole"))
				.resources(Arrays.asList("*")).build();

		final PolicyDocument stateMachinePolicyDocument = PolicyDocument.Builder.create()
				.statements(Arrays.asList(sfnStatement)).build();

		final Role stateMachineRole = Role.Builder.create(this, ARGS.getPrefixedName("statemachine-role"))
				.roleName(ARGS.getPrefixedName("statemachine-role"))
				.inlinePolicies(
						Collections.singletonMap(ARGS.getPrefixedName("lc-sfn-policy"), stateMachinePolicyDocument))
				.path("/").assumedBy(new ServicePrincipal("states.amazonaws.com")).build();

		final Map<String, String> snsMsgFieldsMap = new HashMap<>();
		snsMsgFieldsMap.put("client_reference_number", JsonPath.stringAt("$.client_reference_number"));
		snsMsgFieldsMap.put("uuid", JsonPath.stringAt("$.uuid"));
		snsMsgFieldsMap.put("response", JsonPath.stringAt("$.response"));
		final Parallel parallelStates = new Parallel(this, ARGS.getPrefixedName("parallel"))
				.branch(LambdaInvoke.Builder.create(this, ARGS.getPrefixedName("dbconnector-lambda-task"))
						.lambdaFunction(smsDbConnectorLambda).build())
				.branch(SnsPublish.Builder.create(this, ARGS.getPrefixedName("publish-task")).topic(responseTopic)
						.message(TaskInput.fromObject(snsMsgFieldsMap)).build());

		final StateMachine stateMachine = StateMachine.Builder.create(this, ARGS.getPrefixedName("statemachine"))
				.stateMachineName(ARGS.getPrefixedName("statemachine")).definition(parallelStates)
				.role(stateMachineRole).build();

		// Create SSM parameter for vietguys
		StringParameter vietGuysparam = StringParameter.Builder.create(this, ARGS.getPrefixedName("viet_guys-ssm"))
				.parameterName(ARGS.getPrefixedName("viet_guys-cred"))
				.stringValue(new String(Base64.encodeBase64(ARGS.getVietguyPass().getBytes()))).build();
		vietGuysparam.grantRead(smsProcessorLambda);

		// Create SSM parameter for dtac
		StringParameter dtacParam = StringParameter.Builder.create(this, ARGS.getPrefixedName("dtac-ssm"))
				.parameterName(ARGS.getPrefixedName("dtac-cred"))
				.stringValue(new String(Base64.encodeBase64(ARGS.getDtacPass().getBytes()))).build();
		dtacParam.grantRead(smsProcessorLambda);

		// Create Rest API Gateway
		final PolicyStatement apiStatement = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("execute-api:Invoke")).resources(Arrays.asList("*")).build();
		apiStatement.addAnyPrincipal();

		final PolicyDocument apiPolicyDocument = PolicyDocument.Builder.create().statements(Arrays.asList(apiStatement))
				.build();

		final RestApi api = RestApi.Builder.create(this, ARGS.getPrefixedName("gateway"))
				.restApiName(ARGS.getPrefixedName("api"))
				.endpointConfiguration(
						EndpointConfiguration.builder().types(Arrays.asList(EndpointType.PRIVATE)).build())
				.policy(apiPolicyDocument).deployOptions(StageOptions.builder().stageName(ARGS.getProfile()).build())
				.cloudWatchRole(false)

				.build();
		final Resource smsResource = api.getRoot().addResource(Constants.SERVICE_NAME);
		final LambdaIntegration getWidgetIntegration = LambdaIntegration.Builder.create(smsConnectorLambda).build();

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

package com.lmig.libertyconnect.sms.stack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;

import com.amazonaws.util.StringUtils;
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
import software.amazon.awscdk.services.ec2.GatewayVpcEndpoint;
import software.amazon.awscdk.services.ec2.IGatewayVpcEndpoint;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.Subnet;
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
	
	private Args args;
	private SecurityGroup sg;
	private IVpc vpc;
	private SubnetSelection subnetSelection;

	public LcSmsStack(final Construct parent, final String id, final StackProps props, final Args args) {
		super(parent, id, props);
		this.args = args;
		
		// create kms key
		final Key smsStackKey = Key.Builder.create(this, args.getPrefixedName("key"))
				.enableKeyRotation(true)
				.alias(args.getPrefixedName("alias/key"))
				.policy(getPolicyDocument())
				.build();

		// create security group
		sg = SecurityGroup.Builder.create(this, args.getPrefixedName("sg"))
				.securityGroupName(args.getPrefixedName("sg"))
				.allowAllOutbound(true)
				.vpc(Vpc.fromLookup(this, id, 
						VpcLookupOptions.builder().isDefault(false).build())).build();

		// create vpc and subnet selection
		vpc = Vpc.fromLookup(this, args.getPrefixedName("vpc"),
				VpcLookupOptions.builder().isDefault(false).build());

		subnetSelection = getSubnetSelection();
		
		// create queue
		final String queueName = args.getPrefixedName("queue.fifo");
		final Queue queue = Queue.Builder.create(this, queueName).queueName(queueName)
				.retentionPeriod(Duration.days(14)).fifo(true).encryption(QueueEncryption.KMS_MANAGED)
				.visibilityTimeout(Duration.minutes(6)).build();

		// create roles
		final PolicyStatement sqsStatement = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("sqs:ListQueues", "sqs:SendMessage", "sqs:ReceiveMessage", "sqs:DeleteMessage",
						"sqs:GetQueueAttributes", "sqs:ChangeMessageVisibility", "sqs:GetQueueUrl"))
				.resources(Arrays.asList(queue.getQueueArn()))
				.build();

		final PolicyStatement logStatement = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"))
				.resources(Arrays.asList("arn:aws:logs:*:*:*"))
				.build();

		final PolicyStatement kmsStatement = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("kms:Decrypt", "kms:GenerateDataKey"))
				.resources(Arrays.asList("*"))
				.build();
		
		final PolicyStatement secretManagerStatement = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("secretsmanager:GetResourcePolicy",
						"secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret",
						"secretsmanager:ListSecretVersionIds", "secretsmanager:ListSecrets"))
				.resources(Arrays.asList("*"))
				.build();

		final PolicyStatement statesStatement = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("states:StartExecution"))
				.resources(Arrays.asList("*"))
				.build();
		
		final PolicyStatement networkStatement = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("ec2:DescribeNetworkInterfaces", "ec2:CreateNetworkInterface", "ec2:DeleteNetworkInterface",
						"ec2:DescribeInstances", "ec2:AttachNetworkInterface"))
				.resources(Arrays.asList("*"))
				.build();


		final PolicyDocument connectorPolicyDocument = PolicyDocument.Builder.create()
				.statements(Arrays.asList(sqsStatement, logStatement, networkStatement)).build();
		
		final PolicyDocument processorPolicyDocument = PolicyDocument.Builder.create()
				.statements(Arrays.asList(sqsStatement, statesStatement, logStatement)).build();
		
		final PolicyDocument dbconnectorPolicyDocument = PolicyDocument.Builder.create()
				.statements(Arrays.asList(kmsStatement, secretManagerStatement, logStatement, networkStatement)).build();
		
		final Role connectorLambdaRole = Role.Builder.create(this, args.getPrefixedName("connector-lambda-role"))
				.roleName(args.getPrefixedName("connector-lambda-role"))
				.inlinePolicies(Collections.singletonMap(args.getPrefixedName("connector-lambda-policy"),
						connectorPolicyDocument))
				.path("/").assumedBy(new ServicePrincipal("lambda.amazonaws.com")).build();

		final Role processorLambdaRole = Role.Builder.create(this, args.getPrefixedName("processor-lambda-role"))
				.roleName(args.getPrefixedName("processor-lambda-role"))
				.inlinePolicies(Collections.singletonMap(args.getPrefixedName("processor-lambda-policy"),
						processorPolicyDocument))
				.path("/").assumedBy(new ServicePrincipal("lambda.amazonaws.com")).build();

		final Role dbconnectorLambdaRole = Role.Builder.create(this, args.getPrefixedName("dbconnector-lambda-role"))
				.roleName(args.getPrefixedName("dbconnector-lambda-role"))
				.inlinePolicies(Collections.singletonMap(args.getPrefixedName("dbconnector-lambda-policy"),
						dbconnectorPolicyDocument))
				.path("/").assumedBy(new ServicePrincipal("lambda.amazonaws.com")).build();

		// create event Source for sqs
		final List<IEventSource> eventSources = new ArrayList<>();
		eventSources.add(SqsEventSource.Builder.create(queue)
				.batchSize(1)
				.enabled(true)
				.build());

		// create Lambda functions
		final Map<String, String> envsMap = new HashMap<>();
		envsMap.put("PROGRAM", args.program);
		envsMap.put("ENV", args.getProfile());
		envsMap.put("ACCOUNT_ID", args.getAccountId());
		envsMap.put("REGION", args.getRegion());
		
		envsMap.put("openl_url",
				"https://dev-east-openl-asiamcm.lmig.com/openl-api/REST/LibertyConnect/LibertyConnect/SMSConfig");
		final Function smsConnectorLambda = createLambdaWithVpc(args.getPrefixedName("connector-lambda"),
				"com.lmig.libertyconnect.sms.connector.handler.SMSConnectorHandler",
				connectorLambdaRole, args.getConnectorLambdaS3Key(), envsMap, null);
		envsMap.remove("openl_url");
		
		final Function smsProcessorLambda = createNonVpcLambda(args.getPrefixedName("processor-lambda"),
				"com.lmig.libertyconnect.sms.processor.handler.LambdaHandler",
				processorLambdaRole, args.getProcessorLambdaS3Key(), envsMap, eventSources);
		
		envsMap.put("db_host", "intl-sg-apac-liberty-connect-rds-mysql-" + args.getProfile()
		+ "-dbproxy.proxy-cvluefal1end.ap-southeast-1.rds.amazonaws.com");
		envsMap.put("port", "3306");
		envsMap.put("secret_id", "apac-liberty-connect-rds-stack/mysql/intl-sg-apac-liberty-connect-rds-mysql-"
				+ args.getProfile() + "/libcdbuser/libconnnectdb22");
		envsMap.put("vpc_endpoint_url_ssm", "intl-cs-sm-vpc-endpoint-url");
		envsMap.put("db_name", "libertyconnect");
		final Function smsDbConnectorLambda = createLambdaWithVpc(args.getPrefixedName("dbconnector-lambda"),
				"com.lmig.libertyconnect.sms.updatedb.handler.SMSDBConnectorHandler::handleRequest",
				dbconnectorLambdaRole, args.getDbConnectorLambdaS3Key(), envsMap, null);
		
		// Create SSM parameter for vietguys
		createSSM("viet_guys-ssm","viet_guys-cred", args.getVietguyPass(), smsProcessorLambda);

		// Create SSM parameter for dtac
		createSSM("dtac-ssm", "dtac-cred", args.getDtacPass(), smsProcessorLambda);

		// Create Topic
		final Topic responseTopic = createTopic(args.getPrefixedName("response-topic"), smsStackKey);

		// Create step function to invoke dbConnector Lambda and send response to sns
		final PolicyStatement sfnStatement = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList("sts:AssumeRole"))
				.resources(Arrays.asList("*")).build();

		final PolicyDocument stateMachinePolicyDocument = PolicyDocument.Builder.create()
				.statements(Arrays.asList(sfnStatement, kmsStatement)).build();

		final Role stateMachineRole = Role.Builder.create(this, args.getPrefixedName("statemachine-role"))
				.roleName(args.getPrefixedName("statemachine-role"))
				.inlinePolicies(
						Collections.singletonMap(args.getPrefixedName("lc-sfn-policy"), stateMachinePolicyDocument))
				.path("/").assumedBy(new ServicePrincipal("states.amazonaws.com")).build();

		final Map<String, String> snsMsgFieldsMap = new HashMap<>();
		snsMsgFieldsMap.put("client_reference_number", JsonPath.stringAt("$.client_reference_number"));
		snsMsgFieldsMap.put("uuid", JsonPath.stringAt("$.uuid"));
		snsMsgFieldsMap.put("response", JsonPath.stringAt("$.response"));
		final Parallel parallelStates = new Parallel(this, args.getPrefixedName("parallel"))
				.branch(LambdaInvoke.Builder.create(this, args.getPrefixedName("dbconnector-lambda-task"))
						.lambdaFunction(smsDbConnectorLambda).build())
				.branch(SnsPublish.Builder.create(this, args.getPrefixedName("publish-task")).topic(responseTopic)
						.message(TaskInput.fromObject(snsMsgFieldsMap)).build());

		final StateMachine stateMachine = StateMachine.Builder.create(this, args.getPrefixedName("statemachine"))
				.stateMachineName(args.getPrefixedName("statemachine")).definition(parallelStates)
				.role(stateMachineRole).build();
		

		// Create Rest API Gateway
		createSMSApiGateway(smsDbConnectorLambda);

	}
	
	public Function createNonVpcLambda(final String name, final String handler, final Role role,
			final String codeBucketKey, final Map<String, String> envsMap,
			final List<IEventSource> eventSources) {
		Function.Builder builder = Function.Builder.create(this, name)
				.code(Code.fromBucket(
						Bucket.fromBucketName(this, name + "-bucket", UtilMethods.getCodeBucket(args.getProfile())),
					codeBucketKey))
				.environment(envsMap)
				.functionName(name)
				.handler(handler)
				.role(role)
				.runtime(Runtime.JAVA_11)
				.memorySize(1024)
				.timeout(Duration.minutes(5));
		if (eventSources!= null && !eventSources.isEmpty()) {
			builder.events(eventSources);
		}
		return builder.build();
	}
	
	public Function createLambdaWithVpc(final String name, final String handler,
			final Role role, final String codeBucketKey, final Map<String, String> envsMap, 
			final List<IEventSource> eventSources) {
		
		Function.Builder builder = Function.Builder.create(this, name)
					.code(Code.fromBucket(
							Bucket.fromBucketName(this, name + "-bucket", UtilMethods.getCodeBucket(args.getProfile())),
						codeBucketKey))
					.environment(envsMap)
					.vpc(vpc)
					.vpcSubnets(subnetSelection)
					.securityGroups(Arrays.asList(sg))
					.functionName(name)
					.handler(handler)
					.role(role)
					.runtime(Runtime.JAVA_11)
					.memorySize(1024)
					.timeout(Duration.minutes(5));
		if (eventSources!= null && !eventSources.isEmpty()) {
			builder.events(eventSources);
		}
		return builder.build();
					
	}
	
	public Topic createTopic(final String name, final Key key) {
		return Topic.Builder
				.create(this, name)
				.topicName(name)
				.masterKey(key)
				.build();
	}
	
	public StringParameter createSSM(final String id, 
			final String parameterName, final String originalValue, final Function lambda) {
		StringParameter stringParameter = StringParameter.Builder.create(this, args.getPrefixedName(id))
				.parameterName(args.getPrefixedName(parameterName))
				.stringValue(new String(Base64.encodeBase64(originalValue.getBytes()))).build();
		stringParameter.grantRead(lambda);
		return stringParameter;
	}
	
	public void createSMSApiGateway(final Function lambda) {
		final PolicyStatement apiStatement = PolicyStatement.Builder.create()
				.effect(Effect.ALLOW)
				.actions(Arrays.asList("execute-api:Invoke"))
				.resources(Arrays.asList("*")).build();
		apiStatement.addAnyPrincipal();
		
		String vpcEndpointId = null;
		EndpointConfiguration endpointConfiguration;
		
		if ("dev".equals(args.getProfile())) {
			vpcEndpointId = "vpce-0e92b0a49754e7f59";
		} else if ("nonprod".equals(args.getProfile())) {
			vpcEndpointId = "vpce-0ad8d2b2c5e1e404f";
		} else if ("prod".equals(args.getProfile())) {
			vpcEndpointId = "vpce-06a18f15c9b645f6e";
		}		
		
		if (!StringUtils.isNullOrEmpty(vpcEndpointId)) {
			final List<IGatewayVpcEndpoint> endpointList = Arrays.asList(GatewayVpcEndpoint.fromGatewayVpcEndpointId(this, "connector-endpoint-1", vpcEndpointId));
					
					/* InterfaceVpcEndpoint
					.fromInterfaceVpcEndpointAttributes(this, "connector-vpc-1", 
							InterfaceVpcEndpointAttributes.builder()
							.vpcEndpointId(vpcEndpointId)
							.port(80)
							.build())); */
			
			endpointConfiguration = EndpointConfiguration.builder()
					.types(Arrays.asList(EndpointType.PRIVATE))
					.vpcEndpoints(endpointList)
					.build();
		} else {
			endpointConfiguration = EndpointConfiguration.builder()
				.types(Arrays.asList(EndpointType.PRIVATE))
				.build();
		}
		
		final PolicyDocument apiPolicyDocument = PolicyDocument.Builder.create()
				.statements(Arrays.asList(apiStatement))
				.build();

		final RestApi api = RestApi.Builder.create(this, args.getPrefixedName("gateway"))
				.restApiName(args.getPrefixedName("api"))
				.endpointConfiguration(endpointConfiguration)	
				.policy(apiPolicyDocument).deployOptions(StageOptions.builder()
						.stageName(args.getProfile() + "/" + Constants.SMS_CONNECTOR_API_VERSION)
						.build())
				.cloudWatchRole(false)
				.build();
		
		final Resource smsResource = api.getRoot().addResource(Constants.SERVICE_NAME);
		final LambdaIntegration getWidgetIntegration = LambdaIntegration.Builder.create(lambda).build();

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
	
	private SubnetSelection getSubnetSelection() {
		if ("dev".equals(args.getProfile())) {
			subnetSelection = SubnetSelection.builder()
					.subnets(Arrays.asList(Subnet
							.fromSubnetId(this, "subnet-1", "subnet-03253fd03bbc9fd46"),
							Subnet.fromSubnetId(this, "subnet-2", "subnet-bd056df4")))
					.onePerAz(true)
					.build();
		} else if ("nonprod".equals(args.getProfile())) {
			subnetSelection = SubnetSelection.builder()
					.subnets(Arrays.asList(Subnet
							.fromSubnetId(this, "subnet-1", "subnet-0f78eac9f959cce02"),
							Subnet.fromSubnetId(this, "subnet-2", "subnet-0e45442c0143a2494")))
					.onePerAz(true)
					.build();
		} else {
			subnetSelection = SubnetSelection.builder()
					.onePerAz(true)
					.build();
		}
		return subnetSelection;
	}

}

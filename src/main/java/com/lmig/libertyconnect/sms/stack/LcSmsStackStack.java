package com.lmig.libertyconnect.sms.stack; // sms.stack lib.connect?

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
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

public class LcSmsStackStack extends Stack {
	private final String program = "test-reg";
	private final String env = "dev";

	public LcSmsStackStack(final Construct parent, final String id) {
		this(parent, id, null);
	}

	public LcSmsStackStack(final Construct parent, final String id, final StackProps props) {
		super(parent, id, props);
		final String queueName = getPrefixedName("lc-sms-queue.fifo");
		final Queue queue = Queue.Builder.create(this, queueName).queueName(queueName)
				.retentionPeriod(Duration.days(14)).fifo(true).build();
		// create lambda, roles and permissions
		PolicyStatement statement1 = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays.asList(
						new String[] { "sqs:ListQueues", "sqs:SendMessage", "sqs:ReceiveMessage", "sqs:DeleteMessage",
								"sqs:GetQueueAttributes", "sqs:ChangeMessageVisibility", "sqs:GetQueueUrl" }))
				.resources(Arrays.asList(new String[] { "*" })).build();

		PolicyStatement statement2 = PolicyStatement.Builder.create().effect(Effect.ALLOW)
				.actions(Arrays
						.asList(new String[] { "logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents" }))
				.resources(Arrays.asList(new String[] { "arn:aws:logs:*:*:*" })).build();

		PolicyDocument policyDocument = PolicyDocument.Builder.create()
				.statements(Arrays.asList(new PolicyStatement[] { statement1, statement2 })).build();

		Role lambdaRole = Role.Builder.create(this, getPrefixedName("lc-lambda-role"))
				.roleName(getPrefixedName("lc-lambda-role"))
				.inlinePolicies(Collections.singletonMap(getPrefixedName("lc-sqsS3-policy"), policyDocument)).path("/")
				.assumedBy(new ServicePrincipal("lambda.amazonaws.com")).build();

		List<IEventSource> eventSources = new ArrayList<>();
		eventSources.add(SqsEventSource.Builder.create(queue).batchSize(1).enabled(true).build());

		final Function smsProcessorLambda = Function.Builder.create(this, getPrefixedName("lc-sms-processor-lambda"))
				.code(Code.fromBucket(Bucket.fromBucketName(this, "sms-processor", "test-dev-reg-lc-sms-lambda"),
						"sms-processor-0.0.1-SNAPSHOT.jar"))
				.handler("com.lmig.libertyconnect.sms.processor.handler.LambdaHandler").role(lambdaRole)
				.runtime(Runtime.PYTHON_3_8).memorySize(1024).timeout(Duration.minutes(15)).events(eventSources)
				.build();
		// Defines an API Gateway REST API resource backed by our "connector" function
		// LambdaRestApi.Builder.create(this, "reg-dev-lc-sms-gateway")
		// .handler(smsProcessorLambda)
		// .build();

		final Function smsConnectorLambda = Function.Builder.create(this, getPrefixedName("lc-sms-connector-lambda"))
				.code(Code.fromBucket(Bucket.fromBucketName(this, "sms-connector", "test-dev-reg-lc-sms-lambda"),
						"sms-processor-0.0.1-SNAPSHOT.jar"))
				.handler("com.lmig.libertyconnect.sms.connector.handler.SMSConnectorHandler").role(lambdaRole)
				.runtime(Runtime.PYTHON_3_8).memorySize(1024).timeout(Duration.minutes(15)).build();
	}

	String getPrefixedName(final String name) {
		return String.format("%s%s%s%s%s", program, "-", env, "-", name);
	}
}

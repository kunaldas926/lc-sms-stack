package com.lmig.libertyconnect.sms.stack;

import com.amazonaws.util.StringUtils;
import com.lmig.libertyconnect.sms.stack.LcSmsStackApp.Args;
import com.lmig.libertyconnect.sms.stack.utils.Constants;
import com.lmig.libertyconnect.sms.stack.utils.UtilMethods;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.apigateway.ApiKeyOptions;
import software.amazon.awscdk.services.apigateway.EndpointConfiguration;
import software.amazon.awscdk.services.apigateway.EndpointType;
import software.amazon.awscdk.services.apigateway.IApiKey;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.MethodOptions;
import software.amazon.awscdk.services.apigateway.Period;
import software.amazon.awscdk.services.apigateway.QuotaSettings;
import software.amazon.awscdk.services.apigateway.Resource;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.apigateway.StageOptions;
import software.amazon.awscdk.services.apigateway.ThrottleSettings;
import software.amazon.awscdk.services.apigateway.UsagePlan;
import software.amazon.awscdk.services.apigateway.UsagePlanPerApiStage;
import software.amazon.awscdk.services.apigateway.UsagePlanProps;
import software.amazon.awscdk.services.cloudwatch.Alarm;
import software.amazon.awscdk.services.cloudwatch.Metric;
import software.amazon.awscdk.services.cloudwatch.actions.SnsAction;
import software.amazon.awscdk.services.ec2.GatewayVpcEndpoint;
import software.amazon.awscdk.services.ec2.IGatewayVpcEndpoint;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.Subnet;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcLookupOptions;
import software.amazon.awscdk.services.events.CronOptions;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.events.Schedule;
import software.amazon.awscdk.services.events.targets.LambdaFunction;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.iam.ManagedPolicy;
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
import software.amazon.awscdk.services.logs.IFilterPattern;
import software.amazon.awscdk.services.logs.MetricFilter;
import software.amazon.awscdk.services.logs.MetricFilterOptions;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.subscriptions.EmailSubscription;
import software.amazon.awscdk.services.sqs.DeadLetterQueue;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.sqs.QueueEncryption;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.amazon.awscdk.services.stepfunctions.JsonPath;
import software.amazon.awscdk.services.stepfunctions.Parallel;
import software.amazon.awscdk.services.stepfunctions.RetryProps;
import software.amazon.awscdk.services.stepfunctions.StateMachine;
import software.amazon.awscdk.services.stepfunctions.TaskInput;
import software.amazon.awscdk.services.stepfunctions.tasks.LambdaInvoke;
import software.amazon.awscdk.services.stepfunctions.tasks.SnsPublish;

public class LcSmsStack extends Stack {

    private Args args;
    private SecurityGroup sg;
    private IVpc vpc;
    private SubnetSelection subnetSelection;

    public LcSmsStack(
            final Construct parent, final String id, final StackProps props, final Args args) {
        super(parent, id, props);
        this.args = args;

        // create kms key
        final Key smsStackKey =
                Key.Builder.create(this, args.getPrefixedName("key"))
                        .enableKeyRotation(true)
                        .alias(args.getPrefixedName("alias/key"))
                        .policy(getPolicyDocument())
                        .build();

        // create security group
        sg =
                SecurityGroup.Builder.create(this, args.getPrefixedName("sg"))
                        .securityGroupName(args.getPrefixedName("sg"))
                        .allowAllOutbound(true)
                        .vpc(
                                Vpc.fromLookup(
                                        this,
                                        id,
                                        VpcLookupOptions.builder().isDefault(false).build()))
                        .build();

        // create vpc and subnet selection
        vpc =
                Vpc.fromLookup(
                        this,
                        args.getPrefixedName("vpc"),
                        VpcLookupOptions.builder().isDefault(false).build());

        subnetSelection = getSubnetSelection();

        // Create Topic
        final Topic responseTopic =
                createTopic(args.getPrefixedName("response-topic"), smsStackKey);
        final Topic alarmTopic = createTopic(args.getPrefixedName("alarm-topic"), smsStackKey);
        alarmTopic.addSubscription(
                EmailSubscription.Builder.create("Shubham.Srivastava02@libertyinsurance.com.sg")
                        .build());
        alarmTopic.addSubscription(
                EmailSubscription.Builder.create("onkar.kandalgaonkar@libertymutual.com").build());
        //queueAlarmTopic.addSubscription(EmailSubscription.Builder.create("jose.francis@libertymutual.com.hk").build());
        alarmTopic.addSubscription(
                EmailSubscription.Builder.create("soundarapandian.nandhinidevi@libertymutual.com")
                        .build());
        alarmTopic.addSubscription(
                EmailSubscription.Builder.create("rimpa.deysarkar@libertymutual.com.hk").build());

        //Create DLQ
        final Queue dlq =
                Queue.Builder.create(this, args.getPrefixedName("dlq.fifo"))
                        .queueName(args.getPrefixedName("dlq.fifo"))
                        .fifo(true)
                        .encryption(QueueEncryption.KMS_MANAGED)
                        .visibilityTimeout(Duration.seconds(150))
                        .build();

        // Add cloudwatch Alarm for DLQ
        final Metric dlqAlarmMetric = dlq.metricApproximateNumberOfMessagesNotVisible();

        final Alarm dlqAlarm =
                Alarm.Builder.create(this, args.getPrefixedName("dlq-msg-notvisible-alarm"))
                        .alarmName(args.getPrefixedName("dlq-msg-notvisible-alarm"))
                        .metric(dlqAlarmMetric)
                        .threshold(1000)
                        .evaluationPeriods(1)
                        .build();
        dlqAlarm.addAlarmAction(new SnsAction(alarmTopic));

        // create queue
        final String queueName = args.getPrefixedName("queue.fifo");
        final Queue queue =
                Queue.Builder.create(this, queueName)
                        .queueName(queueName)
                        .retentionPeriod(Duration.days(7))
                        .fifo(true)
                        .deadLetterQueue(
                                DeadLetterQueue.builder().maxReceiveCount(3).queue(dlq).build())
                        .encryption(QueueEncryption.KMS_MANAGED)
                        .visibilityTimeout(Duration.minutes(11))
                        .build();
        queue.addToResourcePolicy(getQueueResourcePolicy());

        // Add cloudwatch alarm to fifo
        final Metric msgNotVisibleMetric = queue.metricApproximateNumberOfMessagesNotVisible();
        final Alarm sqsMsgNotVisibleAlarm =
                Alarm.Builder.create(this, args.getPrefixedName("queue-msg-notvisible-alarm"))
                        .alarmName(args.getPrefixedName("queue-msg-notvisible-alarm"))
                        .metric(msgNotVisibleMetric)
                        .threshold(5000)
                        .evaluationPeriods(1)
                        .build();
        sqsMsgNotVisibleAlarm.addAlarmAction(new SnsAction(alarmTopic));

        final Map<String, String> envsMap = new HashMap<>();
        envsMap.put("PROGRAM", args.program);
        envsMap.put("ENV", args.getProfile());
        envsMap.put("ACCOUNT_ID", args.getAccountId());
        envsMap.put("REGION", args.getRegion());

        // create connector lambda
        final PolicyDocument connectorPolicyDocument =
                PolicyDocument.Builder.create()
                        .statements(
                                Arrays.asList(
                                        getSqsStatement(queue.getQueueArn()),
                                        getLogStatement(),
                                        getNetworkStatement()))
                        .build();
        final Role connectorLambdaRole =
                Role.Builder.create(this, args.getPrefixedName("connector-lambda-role"))
                        .roleName(args.getPrefixedName("connector-lambda-role"))
                        .inlinePolicies(
                                Collections.singletonMap(
                                        args.getPrefixedName("connector-lambda-policy"),
                                        connectorPolicyDocument))
                        .path("/")
                        .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                        .build();
        envsMap.put("openl_url", UtilMethods.getOpenUrl(args.getProfile()));
        final Function smsConnectorLambda =
                createLambdaWithVpc(
                        args.getPrefixedName("connector-lambda"),
                        "com.lmig.libertyconnect.sms.connector.handler.SMSConnectorHandler",
                        connectorLambdaRole,
                        args.getConnectorLambdaS3Key(),
                        28,
                        envsMap,
                        null);
        createLambdaErrorMetricAlarm(
                args.getPrefixedName("connector-lambda-error-alarm"),
                smsConnectorLambda,
                alarmTopic);
        createLambdaMetricFilterAlarm(
                args.getPrefixedName("connector-lambda-metric-filter"),
                args.getPrefixedName("connector-lambda-metric-filter-alarm"),
                smsConnectorLambda,
                alarmTopic);
        envsMap.remove("openl_url");

        // create DB Connector Lambda
        final PolicyDocument dbconnectorPolicyDocument =
                PolicyDocument.Builder.create()
                        .statements(
                                Arrays.asList(
                                        getKmsStatement(),
                                        getSecretManagerStatement(),
                                        getLogStatement(),
                                        getNetworkStatement()))
                        .build();
        final Role dbconnectorLambdaRole =
                Role.Builder.create(this, args.getPrefixedName("dbconnector-lambda-role"))
                        .roleName(args.getPrefixedName("dbconnector-lambda-role"))
                        .managedPolicies(
                                Arrays.asList(
                                        ManagedPolicy.fromManagedPolicyArn(
                                                this,
                                                args.getPrefixedName("sm-read-write-policy"),
                                                "arn:aws:iam::aws:policy/SecretsManagerReadWrite")))
                        .inlinePolicies(
                                Collections.singletonMap(
                                        args.getPrefixedName("dbconnector-lambda-policy"),
                                        dbconnectorPolicyDocument))
                        .path("/")
                        .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                        .build();
        envsMap.putAll(UtilMethods.getDBEnvVars(args.getProfile()));
        final Function smsDbConnectorLambda =
                createLambdaWithVpc(
                        args.getPrefixedName("dbconnector-lambda"),
                        "com.lmig.libertyconnect.sms.dbconnector.handler.SMSDBConnectorHandler",
                        Role.fromRoleName(
                                this,
                                args.getPrefixedName("db-liberty-connect-role"),
                                "apac-liberty-connect-role"),
                        args.getDbConnectorLambdaS3Key(),
                        180,
                        envsMap,
                        null);
        createLambdaErrorMetricAlarm(
                args.getPrefixedName("dbconnector-lambda-error-alarm"),
                smsDbConnectorLambda,
                alarmTopic);
        createLambdaMetricFilterAlarm(
                args.getPrefixedName("dbconnector-lambda-metric-filter"),
                args.getPrefixedName("dbconnector-lambda-metric-filter-alarm"),
                smsDbConnectorLambda,
                alarmTopic);

        // create retry Lambda
        final PolicyDocument retryPolicyDocument =
                PolicyDocument.Builder.create()
                        .statements(
                                Arrays.asList(
                                        getKmsStatement(),
                                        getSecretManagerStatement(),
                                        getLogStatement(),
                                        getNetworkStatement()))
                        .build();
        final Role retryLambdaRole =
                Role.Builder.create(this, args.getPrefixedName("retry-lambda-role"))
                        .roleName(args.getPrefixedName("retry-lambda-role"))
                        .inlinePolicies(
                                Collections.singletonMap(
                                        args.getPrefixedName("retry-lambda-policy"),
                                        retryPolicyDocument))
                        .path("/")
                        .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                        .build();
        final Function smsRetryLambda =
                createLambdaWithVpc(
                        args.getPrefixedName("retry-lambda"),
                        "com.lmig.libertyconnect.sms.retry.handler.LambdaHandler",
                        Role.fromRoleName(
                                this,
                                args.getPrefixedName("retry-liberty-connect-role"),
                                "apac-liberty-connect-role"),
                        args.getRetryLambdaS3Key(),
                        900,
                        envsMap,
                        null);
        createLambdaErrorMetricAlarm(
                args.getPrefixedName("retry-lambda-error-alarm"), smsRetryLambda, alarmTopic);
        createLambdaMetricFilterAlarm(
                args.getPrefixedName("retry-lambda-metric-filter"),
                args.getPrefixedName("retry-lambda-metric-filter-alarm"),
                smsRetryLambda,
                alarmTopic);

        envsMap.remove("db_host");
        envsMap.remove("port");
        envsMap.remove("secret_id");
        envsMap.remove("vpc_endpoint_url_ssm");
        envsMap.remove("db_name");

        // create mapper Lambda
        final PolicyDocument mapperPolicyDocument =
                PolicyDocument.Builder.create()
                        .statements(Arrays.asList(getLogStatement()))
                        .build();
        final Role mapperLambdaRole =
                Role.Builder.create(this, args.getPrefixedName("mapper-lambda-role"))
                        .roleName(args.getPrefixedName("mapper-lambda-role"))
                        .inlinePolicies(
                                Collections.singletonMap(
                                        args.getPrefixedName("mapper-lambda-policy"),
                                        mapperPolicyDocument))
                        .path("/")
                        .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                        .build();
        final Function smsMapperLambda =
                createNonVpcLambda(
                        args.getPrefixedName("mapper-lambda"),
                        "com.lmig.libertyconnect.sms.mapper.handler.LambdaHandler",
                        mapperLambdaRole,
                        args.getMapperLambdaS3Key(),
                        60,
                        envsMap,
                        null);
        createLambdaErrorMetricAlarm(
                args.getPrefixedName("mapper-lambda-error-alarm"), smsMapperLambda, alarmTopic);
        createLambdaMetricFilterAlarm(
                args.getPrefixedName("mapper-lambda-metric-filter"),
                args.getPrefixedName("mapper-lambda-metric-filter-alarm"),
                smsMapperLambda,
                alarmTopic);

        // Create step function to invoke dbConnector Lambda and send response to sns
        final StateMachine stateMachine =
                createStateMachine(responseTopic, smsMapperLambda, smsDbConnectorLambda);

        // create processor Lambda
        final PolicyDocument processorPolicyDocument =
                PolicyDocument.Builder.create()
                        .statements(
                                Arrays.asList(
                                        getSqsStatement(queue.getQueueArn()),
                                        getStateStatement(stateMachine.getStateMachineArn()),
                                        getLogStatement()))
                        .build();
        final Role processorLambdaRole =
                Role.Builder.create(this, args.getPrefixedName("processor-lambda-role"))
                        .roleName(args.getPrefixedName("processor-lambda-role"))
                        .inlinePolicies(
                                Collections.singletonMap(
                                        args.getPrefixedName("processor-lambda-policy"),
                                        processorPolicyDocument))
                        .path("/")
                        .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                        .build();
        // create event Source for sqs
        final List<IEventSource> queueEventSources = new ArrayList<>();
        queueEventSources.add(
                SqsEventSource.Builder.create(queue).batchSize(1).enabled(true).build());
        final Function smsProcessorLambda =
                createNonVpcLambda(
                        args.getPrefixedName("processor-lambda"),
                        "com.lmig.libertyconnect.sms.processor.handler.LambdaHandler",
                        processorLambdaRole,
                        args.getProcessorLambdaS3Key(),
                        600,
                        envsMap,
                        queueEventSources);
        createLambdaErrorMetricAlarm(
                args.getPrefixedName("processor-lambda-error-alarm"),
                smsProcessorLambda,
                alarmTopic);
        createLambdaMetricFilterAlarm(
                args.getPrefixedName("processor-lambda-metric-filter"),
                args.getPrefixedName("processor-lambda-metric-filter-alarm"),
                smsProcessorLambda,
                alarmTopic);

        // create dlq Lambda
        final PolicyDocument dlqLambdaPolicyDocument =
                PolicyDocument.Builder.create()
                        .statements(
                                Arrays.asList(
                                        getSqsStatement(dlq.getQueueArn()),
                                        getStateStatement(stateMachine.getStateMachineArn()),
                                        getLogStatement()))
                        .build();
        final Role dlqLambdaRole =
                Role.Builder.create(this, args.getPrefixedName("dlq-lambda-role"))
                        .roleName(args.getPrefixedName("dlq-lambda-role"))
                        .inlinePolicies(
                                Collections.singletonMap(
                                        args.getPrefixedName("dlq-lambda-policy"),
                                        dlqLambdaPolicyDocument))
                        .path("/")
                        .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                        .build();
        // create event Source for dlq
        final List<IEventSource> dlqEventSources = new ArrayList<>();
        dlqEventSources.add(SqsEventSource.Builder.create(dlq).batchSize(1).enabled(true).build());
        final Function dlqLambda =
                createNonVpcLambda(
                        args.getPrefixedName("dlq-lambda"),
                        "com.lmig.libertyconnect.sms.dlq.handler.SMSDLQHandler",
                        dlqLambdaRole,
                        args.getDlqLambdaS3Key(),
                        120,
                        envsMap,
                        dlqEventSources);
        createLambdaErrorMetricAlarm(
                args.getPrefixedName("dlq-lambda-error-alarm"), dlqLambda, alarmTopic);
        createLambdaMetricFilterAlarm(
                args.getPrefixedName("dlq-lambda-metric-filter"),
                args.getPrefixedName("dlq-lambda-metric-filter-alarm"),
                dlqLambda,
                alarmTopic);

        // create scheduler for retry lambda
        createLambdaScheduler(args.getPrefixedName("retry-lambda-cron-rule"), smsRetryLambda);

        // Create SSM parameter for vietguys
        createSSM(
                args.getPrefixedName("viet_guys-ssm"),
                args.getPrefixedName("viet_guys-cred"),
                args.getVietguyPass(),
                smsProcessorLambda);

        // Create SSM parameter for dtac
        createSSM(
                args.getPrefixedName("dtac-ssm"),
                args.getPrefixedName("dtac-cred"),
                args.getDtacPass(),
                smsProcessorLambda);

        // Create Rest API Gateway
        createSMSApiGateway(smsConnectorLambda);
    }

    private void createLambdaMetricFilterAlarm(
            final String metricFilterName,
            final String alarmName,
            final Function lambda,
            final Topic alarmTopic) {
        final IFilterPattern filterPattern = () -> Constants.ERROR_PREFIX;

        MetricFilter filter =
                lambda.getLogGroup()
                        .addMetricFilter(
                                metricFilterName,
                                MetricFilterOptions.builder()
                                        .metricName(metricFilterName)
                                        .metricNamespace(args.getPrefixedName("lc/lambda/error"))
                                        .filterPattern(filterPattern)
                                        .metricValue("1")
                                        .build());

        final Alarm metricFilterAlarm =
                Alarm.Builder.create(this, alarmName)
                        .alarmName(alarmName)
                        .metric(filter.metric())
                        .threshold(1)
                        .evaluationPeriods(1)
                        .build();
        metricFilterAlarm.addAlarmAction(new SnsAction(alarmTopic));
    }

    public Function createNonVpcLambda(
            final String name,
            final String handler,
            final Role role,
            final String codeBucketKey,
            final int timeout,
            final Map<String, String> envsMap,
            final List<IEventSource> eventSources) {
        Function.Builder builder =
                Function.Builder.create(this, name)
                        .code(
                                Code.fromBucket(
                                        Bucket.fromBucketName(
                                                this,
                                                name + "-bucket",
                                                UtilMethods.getCodeBucket(args.getProfile())),
                                        codeBucketKey))
                        .environment(envsMap)
                        .functionName(name)
                        .handler(handler)
                        .role(role)
                        .runtime(Runtime.JAVA_11)
                        .memorySize(1024)
                        .timeout(Duration.seconds(timeout));
        if (eventSources != null && !eventSources.isEmpty()) {
            builder.events(eventSources);
        }
        return builder.build();
    }

    public Function createLambdaWithVpc(
            final String name,
            final String handler,
            final IRole role,
            final String codeBucketKey,
            final int timeout,
            final Map<String, String> envsMap,
            final List<IEventSource> eventSources) {

        Function.Builder builder =
                Function.Builder.create(this, name)
                        .code(
                                Code.fromBucket(
                                        Bucket.fromBucketName(
                                                this,
                                                name + "-bucket",
                                                UtilMethods.getCodeBucket(args.getProfile())),
                                        codeBucketKey))
                        .environment(envsMap)
                        .vpc(vpc)
                        .vpcSubnets(subnetSelection)
                        .functionName(name)
                        .handler(handler)
                        .role(role)
                        .runtime(Runtime.JAVA_11)
                        .memorySize(1024)
                        .timeout(Duration.seconds(timeout));
        if (eventSources != null && !eventSources.isEmpty()) {
            builder.events(eventSources);
        }

        if (args.getPrefixedName("dbconnector-lambda").equals(name)) {
            builder.securityGroups(
                    Arrays.asList(
                            SecurityGroup.fromLookupByName(
                                    this,
                                    args.getPrefixedName("dbconnector-sg"),
                                    "intl-sg-" + args.getProfile() + "-apac-liberty-connect-Lambda",
                                    vpc)));
        } else {
            builder.securityGroups(Arrays.asList(sg));
        }
        return builder.build();
    }

    public Alarm createLambdaErrorMetricAlarm(
            final String name, final Function lambda, final Topic topic) {
        final Metric errorMetric = lambda.metricErrors();
        final Alarm lambdaAlarm =
                Alarm.Builder.create(this, name)
                        .alarmName(name)
                        .metric(errorMetric)
                        .threshold(5)
                        .evaluationPeriods(1)
                        .build();
        lambdaAlarm.addAlarmAction(new SnsAction(topic));
        return lambdaAlarm;
    }

    public Rule createLambdaScheduler(final String name, final Function lambda) {
        return Rule.Builder.create(this, name)
                .ruleName(name)
                .description("Run retry lambda")
                .schedule(Schedule.cron(CronOptions.builder().minute("0/360").build()))
                .targets(Arrays.asList(LambdaFunction.Builder.create(lambda).build()))
                .build();
    }

    public StateMachine createStateMachine(
            final Topic topic, final Function mapperLambda, final Function dbConnectorLambda) {
        final PolicyStatement sfnStatement =
                PolicyStatement.Builder.create()
                        .effect(Effect.ALLOW)
                        .actions(Arrays.asList("sts:AssumeRole"))
                        .resources(Arrays.asList("*"))
                        .build();

        final PolicyDocument stateMachinePolicyDocument =
                PolicyDocument.Builder.create()
                        .statements(Arrays.asList(sfnStatement, getKmsStatement()))
                        .build();

        final Role stateMachineRole =
                Role.Builder.create(this, args.getPrefixedName("statemachine-role"))
                        .roleName(args.getPrefixedName("statemachine-role"))
                        .inlinePolicies(
                                Collections.singletonMap(
                                        args.getPrefixedName("lc-sfn-policy"),
                                        stateMachinePolicyDocument))
                        .path("/")
                        .assumedBy(new ServicePrincipal("states.amazonaws.com"))
                        .build();

        final Map<String, String> snsMsgFieldsMap = new HashMap<>();
        snsMsgFieldsMap.put("clientReferenceNumber", JsonPath.stringAt("$.clientReferenceNumber"));
        snsMsgFieldsMap.put("uuid", JsonPath.stringAt("$.uuid"));
        snsMsgFieldsMap.put("appName", JsonPath.stringAt("$.appName"));
        snsMsgFieldsMap.put("response", JsonPath.stringAt("$.response"));
        final RetryProps retryProps =
                RetryProps.builder()
                        .errors(
                                Arrays.asList(
                                        "Lambda.ServiceException",
                                        "Lambda.AWSLambdaException",
                                        "Lambda.SdkClientException"))
                        .backoffRate(2)
                        .maxAttempts(3)
                        .interval(Duration.seconds(2))
                        .build();
        final LambdaInvoke mapperLambdaInvokeTask =
                LambdaInvoke.Builder.create(this, args.getPrefixedName("mapper-lambda-task"))
                        .lambdaFunction(mapperLambda)
                        .outputPath("$.Payload")
                        .retryOnServiceExceptions(false)
                        .build();
        mapperLambdaInvokeTask.addRetry(retryProps);
        final LambdaInvoke dbConnectorLambdaInvokeTask =
                LambdaInvoke.Builder.create(this, args.getPrefixedName("dbconnector-lambda-task"))
                        .lambdaFunction(dbConnectorLambda)
                        .retryOnServiceExceptions(false)
                        .build();
        dbConnectorLambdaInvokeTask.addRetry(retryProps);

        final Parallel parallelStates =
                new Parallel(this, args.getPrefixedName("parallel"))
                        .branch(dbConnectorLambdaInvokeTask)
                        .branch(
                                SnsPublish.Builder.create(
                                                this, args.getPrefixedName("publish-task"))
                                        .topic(topic)
                                        .message(TaskInput.fromObject(snsMsgFieldsMap))
                                        .build());

        return StateMachine.Builder.create(this, args.getPrefixedName("statemachine"))
                .stateMachineName(args.getPrefixedName("statemachine"))
                .definition(mapperLambdaInvokeTask.next(parallelStates))
                .role(stateMachineRole)
                .build();
    }

    public Topic createTopic(final String name, final Key key) {
        return Topic.Builder.create(this, name).topicName(name).masterKey(key).build();
    }

    public StringParameter createSSM(
            final String id,
            final String parameterName,
            final String originalValue,
            final Function lambda) {
        StringParameter stringParameter =
                StringParameter.Builder.create(this, id)
                        .parameterName(parameterName)
                        .stringValue(new String(Base64.encodeBase64(originalValue.getBytes())))
                        .build();
        stringParameter.grantRead(lambda);
        return stringParameter;
    }

    public void createSMSApiGateway(final Function lambda) {
        final PolicyStatement apiStatement =
                PolicyStatement.Builder.create()
                        .effect(Effect.ALLOW)
                        .actions(Arrays.asList("execute-api:Invoke"))
                        .resources(Arrays.asList("*"))
                        .build();
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
            final List<IGatewayVpcEndpoint> endpointList =
                    Arrays.asList(
                            GatewayVpcEndpoint.fromGatewayVpcEndpointId(
                                    this, "connector-endpoint-1", vpcEndpointId));
            endpointConfiguration =
                    EndpointConfiguration.builder()
                            .types(Arrays.asList(EndpointType.PRIVATE))
                            .vpcEndpoints(endpointList)
                            .build();
        } else {
            endpointConfiguration =
                    EndpointConfiguration.builder()
                            .types(Arrays.asList(EndpointType.PRIVATE))
                            .build();
        }

        final PolicyDocument apiPolicyDocument =
                PolicyDocument.Builder.create().statements(Arrays.asList(apiStatement)).build();

        final RestApi api =
                RestApi.Builder.create(this, args.getPrefixedName("gateway"))
                        .restApiName(args.getPrefixedAPIName())
                        .endpointConfiguration(endpointConfiguration)
                        .policy(apiPolicyDocument)
                        .deployOptions(
                                StageOptions.builder()
                                        .stageName(Constants.SMS_CONNECTOR_API_VERSION)
                                        .build())
                        .cloudWatchRole(false)
                        .build();

        final IApiKey key =
                api.addApiKey(
                        args.getPrefixedName("api-key"),
                        ApiKeyOptions.builder()
                                .apiKeyName(args.getPrefixedName("api-key"))
                                .build());
        final UsagePlan plan =
                api.addUsagePlan(
                        "usage-plan",
                        UsagePlanProps.builder()
                                .name("usage-plan")
                                .throttle(
                                        ThrottleSettings.builder()
                                                .rateLimit(100)
                                                .burstLimit(200)
                                                .build())
                                .quota(
                                        QuotaSettings.builder()
                                                .limit(5000)
                                                .period(Period.MONTH)
                                                .build())
                                .build());
        plan.addApiKey(key);
        plan.addApiStage(
                UsagePlanPerApiStage.builder().api(api).stage(api.getDeploymentStage()).build());

        final Resource smsResource = api.getRoot().addResource(Constants.SERVICE_NAME);
        final LambdaIntegration getWidgetIntegration =
                LambdaIntegration.Builder.create(lambda).build();

        smsResource.addMethod(
                "POST", getWidgetIntegration, MethodOptions.builder().apiKeyRequired(true).build());
    }

    private PolicyDocument getPolicyDocument() {

        final PolicyDocument policyDocument = new PolicyDocument();
        policyDocument.addStatements(
                getCloudwatchStatement(), getSnsStatement(), getIamPolicyStatement());
        return policyDocument;
    }

    private PolicyStatement getSnsStatement() {

        final PolicyStatement policyStatement = new PolicyStatement();
        policyStatement.addActions("kms:GenerateDataKey*", "kms:Decrypt");
        policyStatement.addServicePrincipal("sns.amazonaws.com");
        policyStatement.addAllResources();
        return policyStatement;
    }

    private PolicyStatement getCloudwatchStatement() {

        final PolicyStatement policyStatement = new PolicyStatement();
        policyStatement.addActions("kms:GenerateDataKey*", "kms:Decrypt");
        policyStatement.addServicePrincipal("cloudwatch.amazonaws.com");
        policyStatement.addAllResources();
        return policyStatement;
    }

    private PolicyStatement getSqsStatement(final String arn) {
        return PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(
                        Arrays.asList(
                                "sqs:ListQueues",
                                "sqs:SendMessage",
                                "sqs:ReceiveMessage",
                                "sqs:DeleteMessage",
                                "sqs:GetQueueAttributes",
                                "sqs:ChangeMessageVisibility",
                                "sqs:GetQueueUrl"))
                .resources(Arrays.asList(arn))
                .build();
    }

    private PolicyStatement getLogStatement() {

        return PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(
                        Arrays.asList(
                                "logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"))
                .resources(Arrays.asList("arn:aws:logs:*:*:*"))
                .build();
    }

    private PolicyStatement getSecretManagerStatement() {
        return PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(
                        Arrays.asList(
                                "secretsmanager:GetResourcePolicy",
                                "secretsmanager:GetSecretValue",
                                "secretsmanager:DescribeSecret",
                                "secretsmanager:ListSecretVersionIds",
                                "secretsmanager:ListSecrets"))
                .resources(Arrays.asList("*"))
                .build();
    }

    private PolicyStatement getStateStatement(final String arn) {

        return PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList("states:StartExecution"))
                .resources(Arrays.asList(arn))
                .build();
    }

    private PolicyStatement getNetworkStatement() {

        return PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(
                        Arrays.asList(
                                "ec2:DescribeNetworkInterfaces",
                                "ec2:CreateNetworkInterface",
                                "ec2:DeleteNetworkInterface",
                                "ec2:DescribeInstances",
                                "ec2:AttachNetworkInterface"))
                .resources(Arrays.asList("*"))
                .build();
    }

    private PolicyStatement getKmsStatement() {
        return PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList("kms:Decrypt", "kms:GenerateDataKey"))
                .resources(Arrays.asList("*"))
                .build();
    }

    public PolicyStatement getQueueResourcePolicy() {

        final PolicyStatement policyStatement = new PolicyStatement();
        policyStatement.addActions("sqs:SendMessage");
        policyStatement.addAnyPrincipal();
        policyStatement.addCondition(
                "StringLike",
                Collections.singletonMap(
                        "aws:PrincipalArn",
                        "arn:aws:lambda:"
                                + args.getRegion()
                                + ":"
                                + args.getAccountId()
                                + ":function:"
                                + args.getProgram()
                                + "-"
                                + args.getProfile()
                                + "-"
                                + Constants.PROJECT_NAME
                                + "-"
                                + Constants.SERVICE_NAME
                                + "-"
                                + "*"));
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
            subnetSelection =
                    SubnetSelection.builder()
                            .subnets(
                                    Arrays.asList(
                                            Subnet.fromSubnetId(
                                                    this, "subnet-1", "subnet-ea5a228d"),
                                            Subnet.fromSubnetId(
                                                    this, "subnet-2", "subnet-bd056df4")))
                            .onePerAz(true)
                            .build();
        } else if ("nonprod".equals(args.getProfile())) {
            subnetSelection =
                    SubnetSelection.builder()
                            .subnets(
                                    Arrays.asList(
                                            Subnet.fromSubnetId(
                                                    this, "subnet-1", "subnet-0f78eac9f959cce02"),
                                            Subnet.fromSubnetId(
                                                    this, "subnet-2", "subnet-0e45442c0143a2494")))
                            .onePerAz(true)
                            .build();
        } else if ("prod".equals(args.getProfile())) {
            subnetSelection =
                    SubnetSelection.builder()
                            .subnets(
                                    Arrays.asList(
                                            Subnet.fromSubnetId(
                                                    this, "subnet-1", "subnet-02d2ab4afb22575b7"),
                                            Subnet.fromSubnetId(
                                                    this, "subnet-2", "subnet-0e4a245c2053adbbb")))
                            .onePerAz(true)
                            .build();
        } else {
            subnetSelection = SubnetSelection.builder().onePerAz(true).build();
        }
        return subnetSelection;
    }
}

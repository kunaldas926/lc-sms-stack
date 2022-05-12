package com.lmig.libertyconnect.sms.stack;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lmig.libertyconnect.sms.stack.LcSmsStackApp.Args;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;

public class LcSmsStackTest {
	private final static ObjectMapper JSON = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);

	@Test
	public void testStack() throws IOException {
		Args ARGS = Args.builder().program("test").profile("local")
				.processorLambdaS3Key("code/sms-processor-0.0.1-SNAPSHOT.jar")
				.connectorLambdaS3Key("code/sms-connector-0.0.1-SNAPSHOT.jar")
				.dbConnectorLambdaS3Key("code/sms-db-connector-0.0.1-SNAPSHOT.jar")
				.dtacPass("dummy")
				.vietguyPass("dummy")
				.accountId("01234567891011")
				.region("ap-southeast-1")
				.build();

		App app = new App();
		LcSmsStack stack = new LcSmsStack(app, ARGS.getPrefixedName("lc-sms"), StackProps.builder()
                .env(Environment.builder()
                		.account(ARGS.getAccountId())
                        .region(ARGS.getRegion())
                        .build()).build(), ARGS);

		JsonNode actual = JSON.valueToTree(app.synth().getStackArtifact(stack.getArtifactId()).getTemplate());
		System.out.println(actual);
		assertTrue(actual.toString().contains("AWS::SQS::Queue"));
		assertTrue(actual.toString().contains("AWS::Lambda::Function"));
		assertTrue(actual.toString().contains("AWS::ApiGateway::RestApi"));
		assertTrue(actual.toString().contains("AWS::SSM::Parameter"));
		assertTrue(actual.toString().contains("AWS::SNS::Topic"));
		assertTrue(actual.toString().contains("AWS::StepFunctions::StateMachine"));

	}
}

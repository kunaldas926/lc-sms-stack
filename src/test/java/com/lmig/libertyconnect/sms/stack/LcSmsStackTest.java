package com.lmig.libertyconnect.sms.stack;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lmig.libertyconnect.sms.stack.LcSmsStackApp.Args;

import software.amazon.awscdk.core.App;

class LcSmsStackTest {
	private final static ObjectMapper JSON = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);

	@Test
	void testStack() throws IOException {
		Args ARGS = Args.builder().program("test").profile("local").build();

		App app = new App();
		LcSmsStack stack = new LcSmsStack(app, ARGS.getPrefixedName("lc-sms"), null, ARGS);

		JsonNode actual = JSON.valueToTree(app.synth().getStackArtifact(stack.getArtifactId()).getTemplate());

		assertTrue(actual.toString().contains("AWS::SQS::Queue"));
		assertTrue(actual.toString().contains("AWS::Lambda::Function"));

	}
}

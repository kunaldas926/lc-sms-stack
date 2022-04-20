package com.lmig.libertyconnect.sms.stack;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import software.amazon.awscdk.core.App;

class LcSmsStackTest {
	private final static ObjectMapper JSON = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);

	@Test
	void testStack() throws IOException {
		App app = new App();
		LcSmsStack stack = new LcSmsStack(app, "test");

		JsonNode actual = JSON.valueToTree(app.synth().getStackArtifact(stack.getArtifactId()).getTemplate());

		assertTrue(actual.toString().contains("AWS::SQS::Queue"));
		assertTrue(actual.toString().contains("AWS::Lambda::Function"));

	}
}

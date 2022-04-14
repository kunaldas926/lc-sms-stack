package com.lmig.libertyconnect.sms.stack;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Tags;

public final class LcSmsStackApp {
	public static void main(final String[] args) {
		App app = new App();
		final String stackName = "test-reg-dev-lc-sms-stack";
		addTags(app, stackName);
		new LcSmsStackStack(app, stackName);

		app.synth();
	}

	public static void addTags(App app, final String stackName) {
		Tags.of(app).add("lm_troux_uid", "7CFD56E9-332A-40F7-8A24-557EF0BFC796");
	}
}

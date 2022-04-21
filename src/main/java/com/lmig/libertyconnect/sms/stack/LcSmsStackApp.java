package com.lmig.libertyconnect.sms.stack;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import lombok.Data;
import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Tags;

public final class LcSmsStackApp {
	
	private static final Args ARGS = new Args();
	
	public static void main(final String[] args) {
		JCommander.newBuilder().addObject(ARGS).build().parse(args);
		App app = new App();
		final String stackName = "test-reg-dev-lc-sms-stack";
		addTags(app, stackName);
		new LcSmsStack(app, stackName);

		app.synth();
	}

	public static void addTags(App app, final String stackName) {
		// 7CFD56E9-332A-40F7-8A24-557EF0BFC796
		Tags.of(app).add("lm_troux_uid", ARGS.getLmTrouxUid());
	}
	
    @Data
    public static class Args {

        @Parameter(
            names = {"-program"},
            description = "Required: Profile to run",
            required = true
        )
        public String program;
        
        @Parameter(
            names = {"-profile", "-p"},
            description = "Required: Profile to run",
            required = true
        )
        private String profile;

        @Parameter(
            names = {"-lm_troux_uid"},
            description = "Required: tag lm_troux_uid",
            required = false          
        )
        private String lmTrouxUid;       
    }
}

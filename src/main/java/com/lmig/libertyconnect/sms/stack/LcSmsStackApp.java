package com.lmig.libertyconnect.sms.stack;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.lmig.libertyconnect.sms.stack.LcSmsStackApp.Args;
import com.lmig.libertyconnect.sms.stack.utils.Constants;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.core.Tags;

public final class LcSmsStackApp {
	
	private static final Args ARGS = new Args();
	
	public static void main(final String[] args) {
		JCommander.newBuilder().addObject(ARGS).build().parse(args);
		App app = new App();
		final String stackName = ARGS.getPrefixedName("stack");
		addTags(app, stackName);
		new LcSmsStack(app, stackName, StackProps.builder()
                .env(Environment.builder()
                        .account(ARGS.getAccountId())
                        .region(ARGS.getRegion())
                        .build()).build(), ARGS);

		app.synth();
	}

	public static void addTags(App app, final String stackName) {
		Tags.of(app).add("lm_troux_uid", ARGS.getLmTrouxUid());
	}
		
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Args {

        @Parameter(
            names = {"-program"},
            description = "Required: Program",
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
        	names = {"-accountId"},
            description = "Optional: AWS AccountId",
            required = false
        )
        public String accountId;
            
        @Parameter(
            names = {"-region"},
            description = "Optional: AWS region",
            required = true
        )
        private String region;

        @Parameter(
            names = {"-lm_troux_uid"},
            description = "Required: tag lm_troux_uid",
            required = true          
        )
        private String lmTrouxUid;  
        
        @Parameter(
                names = {"-connectorLambdaS3Key"},
                description = "Required: bucket key for connector Lambda",
                required = true          
        )
        private String connectorLambdaS3Key;
        
        @Parameter(
                names = {"-processorLambdaS3Key"},
                description = "Required: bucket key for processor Lambda",
                required = true
        )
        private String processorLambdaS3Key;
        
        @Parameter(
                names = {"-dbConnectorLambdaS3Key"},
                description = "Required: bucket key for processor Lambda",
                required = true          
        )
        private String dbConnectorLambdaS3Key;
        
        @Parameter(
                names = {"-vietguyPass"},
                description = "Required: vietguys credential",
                required = true          
        )
        private String vietguyPass; 
        
        @Parameter(
                names = {"-dtacPass"},
                description = "Required: dtac credential",
                required = true          
        )
        private String dtacPass;
        
    	public String getPrefixedName(final String name) {
    		return String.format("%s%s%s%s%s%s%s%s%s", this.program, "-", this.profile, "-", Constants.PROJECT_NAME, "-", Constants.SERVICE_NAME, "-", name);
    	}
    }
}

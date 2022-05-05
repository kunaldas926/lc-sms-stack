package com.lmig.libertyconnect.sms.stack;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.lmig.libertyconnect.sms.stack.LcSmsStackApp.Args;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Tags;

public final class LcSmsStackApp {
	
	private static final Args ARGS = new Args();
	
	public static void main(final String[] args) {
		JCommander.newBuilder().addObject(ARGS).build().parse(args);
		App app = new App();
		final String stackName = ARGS.getPrefixedName("lc-sms-stack");
		addTags(app, stackName);
		new LcSmsStack(app, stackName, null, ARGS);

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
    		return String.format("%s%s%s%s%s", this.program, "-", this.profile, "-", name);
    	}
    }
}

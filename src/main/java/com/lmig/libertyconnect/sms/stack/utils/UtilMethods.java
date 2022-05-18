package com.lmig.libertyconnect.sms.stack.utils;

public class UtilMethods {
	
	private UtilMethods () {}
	public static String getCodeBucket(final String env) {
		return Constants.S3_BUCKET_PREFIX + "-" + env;
	}
}

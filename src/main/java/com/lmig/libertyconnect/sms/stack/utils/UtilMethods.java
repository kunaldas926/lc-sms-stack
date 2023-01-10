package com.lmig.libertyconnect.sms.stack.utils;

import java.util.HashMap;
import java.util.Map;

public class UtilMethods {

    private UtilMethods() {}

    public static String getCodeBucket(final String env) {
        if (Constants.DEV_ENV.equals(env)) {
            return Constants.DEV_S3_BUCKET;
        } else if (Constants.NONPROD_ENV.equals(env)) {
            return Constants.NONPROD_S3_BUCKET;
        } else if (Constants.PROD_ENV.equals(env)) {
            return Constants.PROD_S3_BUCKET;
        } else {
            return "dummy-s3-bucket";
        }
    }

    public static String getOpenUrl(final String env) {

        if (Constants.DEV_ENV.equals(env)) {
            return Constants.DEV_OPENL_URL;
        } else if (Constants.NONPROD_ENV.equals(env)) {
            return Constants.NONPROD_OPENL_URL;
        } else if (Constants.PROD_ENV.equals(env)) {
            return Constants.PROD_OPENL_URL;
        } else if (Constants.LOCAL_ENV.equals(env)) {
            return "dummy-openl-url";
        }

        return null;
    }

    public static Map<String, String> getDBEnvVars(final String env) {
        final String dbHost = "db_host";
        final String secretId = "secret_id";
        final Map<String, String> envsMap = new HashMap<>();
        envsMap.put("port", "3306");
        envsMap.put("vpc_endpoint_url_ssm", "intl-cs-sm-vpc-endpoint-url");
        envsMap.put("db_name", "libertyconnect");

        if (Constants.DEV_ENV.equals(env)) {
            envsMap.put(dbHost, Constants.DEV_DB_HOST);
            envsMap.put(secretId, Constants.DEV_SECRET_ID);
        } else if (Constants.NONPROD_ENV.equals(env)) {
            envsMap.put(dbHost, Constants.NONPROD_DB_HOST);
            envsMap.put(secretId, Constants.NONPROD_SECRET_ID);
        } else if (Constants.PROD_ENV.equals(env)) {
            envsMap.put(dbHost, Constants.PROD_DB_HOST);
            envsMap.put(secretId, Constants.PROD_SECRET_ID);
        } else if (Constants.LOCAL_ENV.equals(env)) {
            envsMap.put(dbHost, "dummy_host");
            envsMap.put(secretId, "dummy_id");
        }

        return envsMap;
    }
}

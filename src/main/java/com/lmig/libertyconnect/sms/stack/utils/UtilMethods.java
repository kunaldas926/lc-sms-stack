package com.lmig.libertyconnect.sms.stack.utils;

import java.util.HashMap;
import java.util.Map;

public class UtilMethods {

    private UtilMethods() {}

    public static String getCodeBucket(final String env) {
        return Constants.S3_BUCKET_PREFIX + "-" + env;
    }

    public static String getOpenUrl(final String env) {

        if ("dev".equals(env)) {
            return Constants.DEV_OPENL_URL;
        } else if ("nonprod".equals(env)) {
            return Constants.NONPROD_OPENL_URL;
        } else if ("prod".equals(env)) {
            return Constants.PROD_OPENL_URL;
        } else if ("local".equals(env)) {
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

        if ("dev".equals(env)) {
            envsMap.put(dbHost, Constants.DEV_DB_HOST);
            envsMap.put(secretId, Constants.DEV_SECRET_ID);
        } else if ("nonprod".equals(env)) {
            envsMap.put(dbHost, Constants.NONPROD_DB_HOST);
            envsMap.put(secretId, Constants.NONPROD_SECRET_ID);
        } else if ("prod".equals(env)) {
            envsMap.put(dbHost, Constants.PROD_DB_HOST);
            envsMap.put(secretId, Constants.PROD_SECRET_ID);
        } else if ("local".equals(env)) {
            envsMap.put(dbHost, "dummy_host");
            envsMap.put(secretId, "dummy_id");
        }

        return envsMap;
    }
}

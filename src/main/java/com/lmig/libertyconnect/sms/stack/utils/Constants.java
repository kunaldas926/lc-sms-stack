package com.lmig.libertyconnect.sms.stack.utils;

public class Constants {

    private Constants() {}

    public static final String PROJECT_NAME = "lc";
    public static final String SERVICE_NAME = "sms";
    public static final String S3_BUCKET_PREFIX = "intl-reg-libconnect-attachemnts-s3";
    public static final String SMS_CONNECTOR_API_VERSION = "v1";

    // OpenL constants
    public static final String DEV_OPENL_URL =
            "https://dev-east-openl-asiamcm.lmig.com/openl-api/LibertyConnect/LibertyConnect/SMSConfig";
    public static final String NONPROD_OPENL_URL =
            "https://dev-east-openl-asiamcm.lmig.com/openl-api/LibertyConnect/LibertyConnect/SMSConfig";
    public static final String PROD_OPENL_URL =
            "https://dev-east-openl-asiamcm.lmig.com/openl-api/LibertyConnect/LibertyConnect/SMSConfig";

    // DB related constants
    // TODO : Move to prop file
    public static final String DEV_DB_HOST =
            "intl-sg-apac-liberty-connect-rds-mysql-dev-dbproxy.proxy-cvluefal1end.ap-southeast-1.rds.amazonaws.com";
    public static final String DEV_SECRET_ID =
            "intl-reg-apac-liberty-connect-rds1-dev/mysql/intl-sg-apac-liberty-connect-rds-mysql-dev/lcdevrdsmstrusr/libertyconnectdevrds";
    public static final String NONPROD_DB_HOST =
            "intl-sg-apac-liberty-connect-rds-mysql-nonprod-dbproxy.proxy-ch6anygktrim.ap-southeast-1.rds.amazonaws.com";
    public static final String NONPROD_SECRET_ID =
            "intl-reg-apac-liberty-connect-rds1-nonprod/mysql/intl-sg-apac-liberty-connect-rds-mysql-nonprod/libconrdsmstrusr/libertyconnectnonprodrds";
    // TODO: Add correct values after rds stack is deployed to prod
    public static final String PROD_DB_HOST = "";
    public static final String PROD_SECRET_ID = "";

    // Used by alarm metric
    public static final String ERROR_PREFIX = "LCException";
}

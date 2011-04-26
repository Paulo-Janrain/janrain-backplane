package com.janrain.backplane.server.config;

import com.amazonaws.auth.AWSCredentials;
import org.apache.log4j.Logger;

/**
 * Extracts and provides Amazon Web Service credentials (key id and key secret)
 * from the AWS_ACCESS_KEY_ID and AWS_SECRET_KEY system variables.
 *
 * @author Johnny Bufu
 */
public class SystemEnvAWSCredentials implements AWSCredentials {

    // - PUBLIC

    @Override
    public String getAWSAccessKeyId() {
        return awsKeyId;
    }

    @Override
    public String getAWSSecretKey() {
        return awsSecretKey;
    }

    // - PRIVATE                                                                                                                                    

    private SystemEnvAWSCredentials() {
        this.awsKeyId = BackplaneConfig.getAwsEnv(AWS_ACCESS_KEY_ID);
        this.awsSecretKey = BackplaneConfig.getAwsEnv(AWS_SECRET_KEY);
        logger.info("AWS credentials loaded from system environment for Key ID: " + awsKeyId);
    }

    private final String awsKeyId;
    private final String awsSecretKey;

    private static final Logger logger = Logger.getLogger(SystemEnvAWSCredentials.class);

    private static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    private static final String AWS_SECRET_KEY = "AWS_SECRET_KEY";
}

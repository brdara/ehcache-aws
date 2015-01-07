package com.xarixa.ehcache.aws;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts the AWS access and secret key using either:
 * <ol>
 * <li>A property file named through the system property {@value #AWS_IDENTIFY_FILE} which contains properties for
 * the access key as either {@value #AWS_ACCESS_KEY_PROP} or {@value #AWS_ACCESS_KEY_PROP_2}, and the
 * secret key as either {@value #AWS_SECRET_KEY_PROP} or {@value #AWS_SECRET_KEY_PROP_2}.
 * <li>System properties which have been set for {@value #AWS_ACCESS_KEY_SYSTEM_PROP} and
 * {@value #AWS_SECRET_KEY_SYSTEM_PROP}.
 * </ol>
 * 
 * @author Fabric WorldWide
 */
public final class AwsSecurityUtils {
	private static final Logger LOG = LoggerFactory.getLogger(AwsSecurityUtils.class.getName());
    private static final String AWS_ACCESS_KEY_PROP = "accessKey";
    private static final String AWS_ACCESS_KEY_PROP_2 = "access_key";
    private static final String AWS_ACCESS_KEY_SYSTEM_PROP = "com.xarixa.ehcache.aws." + AWS_ACCESS_KEY_PROP;
    private static final String AWS_SECRET_KEY_PROP = "secretKey";
    private static final String AWS_SECRET_KEY_PROP_2 = "secret_key";
    private static final String AWS_SECRET_KEY_SYSTEM_PROP = "com.xarixa.ehcache.aws." + AWS_SECRET_KEY_PROP;
    private static final AwsSecurityUtils instance = new AwsSecurityUtils();
    public static final String AWS_IDENTIFY_FILE = "com.xarixa.ehcache.aws.AwsIdentifyFile";
	private Properties props;

	private AwsSecurityUtils() {
		props = new Properties();
		String awsIdentifyFile = System.getProperty(AWS_IDENTIFY_FILE);

		// Attempt to load from the file
		if (StringUtils.isNotBlank(awsIdentifyFile)) {
			LOG.debug("Reading AWS security info from '" + awsIdentifyFile + "'");
			try {
				props.load(new BufferedReader(new FileReader(awsIdentifyFile)));
			} catch (IOException e) {
				LOG.warn("Could not load AWS identify file '{}'", awsIdentifyFile, e);
			}
		} else {
			// Fallback to using system properties
			props.put(AWS_ACCESS_KEY_PROP, System.getProperty(AWS_ACCESS_KEY_SYSTEM_PROP));
			props.put(AWS_SECRET_KEY_PROP, System.getProperty(AWS_SECRET_KEY_SYSTEM_PROP));
		}
	}

	String getAwsAccessKey() {
		return StringUtils.defaultString((String)props.get(AWS_ACCESS_KEY_PROP), (String)props.get(AWS_ACCESS_KEY_PROP_2));
	}

	String getAwsSecretKey() {
		return StringUtils.defaultString((String)props.get(AWS_SECRET_KEY_PROP), (String)props.get(AWS_SECRET_KEY_PROP_2));
	}
	
	protected static AwsSecurityUtils getInstance() {
		return instance;
	}

}
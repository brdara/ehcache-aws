package com.xarixa.ehcache.aws;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.SecurityGroup;
import org.jclouds.compute.extensions.SecurityGroupExtension;
import org.jclouds.compute.predicates.NodePredicates;
import org.jclouds.domain.Location;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.junit.Assert;

import com.google.common.collect.ImmutableSet;
import com.xarixa.ehcache.aws.AwsSecurityUtils;

public abstract class AbstractAwsIntegrationTest {
	private static SecurityGroup securityGroup;
	private static ComputeService computeService;
	private static String awsAccessKey;
	private static String awsSecretKey;
	private static String securityGroupId;
	public static final String AWS_EHCACHE_TEST_MARKER_GROUP = "awsehcachetestmarkergroup";
	public static final String EU_WEST_1 = "eu-west-1";

	public static void createTestSecurityGroup() {
		awsAccessKey = AwsSecurityUtils.getInstance().getAwsAccessKey();
		awsSecretKey = AwsSecurityUtils.getInstance().getAwsSecretKey();
		
		// Get an EC2 connection using JClouds
		computeService = ContextBuilder.newBuilder("aws-ec2")
                .credentials(awsAccessKey, awsSecretKey)
                .modules(ImmutableSet.of(new SLF4JLoggingModule(), new SshjSshClientModule()))
                .buildView(ComputeServiceContext.class)
                .getComputeService();
		
		createSecurityGroup();
	}

	private static void createSecurityGroup() {
		Location location = getDefaultLocation();
		SecurityGroupExtension securityGroupExtension = computeService.getSecurityGroupExtension().get();
		securityGroup = securityGroupExtension.createSecurityGroup(AWS_EHCACHE_TEST_MARKER_GROUP, location);
		Assert.assertNotNull(securityGroup);

		// JClouds returns a unique group identifier with the region as the first part, parse that out to
		// get the actual identifier
		securityGroupId = StringUtils.substringAfter(securityGroup.getId(), "/");
//		IpPermission.builder().fromPort(fromPort)
//		securityGroupExtension.addIpPermission(ipPermission, securityGroup);
	}
	
	private static Location getDefaultLocation() {
		Set<? extends Location> locations = computeService.listAssignableLocations();
		for (Location location : locations) {
			if (location.getId().equals(EU_WEST_1)) {
				return location;
			}
		}

		throw new RuntimeException("Could not find location " + EU_WEST_1);
	}
	
	public static void destroyTestSecurityGroup() {
		if (securityGroup != null) {
			// Destroy all of the nodes
			computeService.destroyNodesMatching(NodePredicates.inGroup(securityGroup.getName()));
			
			// Finally destroy the security group
			SecurityGroupExtension securityGroupExtension = computeService.getSecurityGroupExtension().get();
			securityGroupExtension.removeSecurityGroup(securityGroup.getId());
		}
	}

	public static SecurityGroup getSecurityGroup() {
		return securityGroup;
	}

	public static ComputeService getComputeService() {
		return computeService;
	}

	public static String getAwsAccessKey() {
		return awsAccessKey;
	}

	public static String getAwsSecretKey() {
		return awsSecretKey;
	}

	public static String getSecurityGroupId() {
		return securityGroupId;
	}

}

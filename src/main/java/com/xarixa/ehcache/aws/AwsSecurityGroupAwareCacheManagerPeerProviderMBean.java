package com.xarixa.ehcache.aws;

import java.util.Set;

import com.xarixa.ehcache.aws.discovery.DiscoveryServiceConfig;

public interface AwsSecurityGroupAwareCacheManagerPeerProviderMBean {

	/**
	 * Gets the discovery service config
	 * @return
	 */
	DiscoveryServiceConfig getDiscoveryServiceConfig();
	
	/**
	 * Gets the set of peer URL's
	 * @return
	 */
	Set<String> getPeerUrls();
	
}
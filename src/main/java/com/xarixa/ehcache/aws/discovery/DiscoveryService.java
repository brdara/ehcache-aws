package com.xarixa.ehcache.aws.discovery;


public interface DiscoveryService {

	/**
	 * Initialises the discovery service.
	 */
	void startDiscoveryService();

	/**
	 * Stop the discovery service. This should stop the discovery service thread also.
	 */
	void stopDiscoveryService();

}

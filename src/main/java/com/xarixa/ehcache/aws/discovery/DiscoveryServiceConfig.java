package com.xarixa.ehcache.aws.discovery;


public interface DiscoveryServiceConfig {

	/**
	 * Ths configured RMI listener port for the cache, assumes this is shared across all instances
	 * @return
	 */
	Integer getRmiListenerPort();

	/**
	 * Validates the configuration and throws an exception if invalid
	 */
	void validate() throws IllegalArgumentException;

}

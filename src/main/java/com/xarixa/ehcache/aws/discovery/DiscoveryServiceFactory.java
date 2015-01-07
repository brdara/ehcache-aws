package com.xarixa.ehcache.aws.discovery;

import com.xarixa.ehcache.aws.UpdateableHostBasedCacheManagerPeerProvider;

/**
 * A discovery service factory creates {@link DiscoveryService} instance(s) for the
 * {@link UpdateableHostBasedCacheManagerPeerProvider} to use to update.
 * 
 * @author Fabric WorldWide
 */
public interface DiscoveryServiceFactory {

	DiscoveryService createDiscoveryService(UpdateableHostBasedCacheManagerPeerProvider updateableCacheManagerPeerProvider,
			DiscoveryServiceConfig discoveryServiceConfig);

}

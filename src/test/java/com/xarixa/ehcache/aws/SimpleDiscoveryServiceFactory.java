package com.xarixa.ehcache.aws;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.Assert;
import net.sf.ehcache.CacheException;

import com.xarixa.ehcache.aws.CachePeerHost;
import com.xarixa.ehcache.aws.UpdateableHostBasedCacheManagerPeerProvider;
import com.xarixa.ehcache.aws.discovery.DiscoveryService;
import com.xarixa.ehcache.aws.discovery.DiscoveryServiceConfig;
import com.xarixa.ehcache.aws.discovery.DiscoveryServiceFactory;

/**
 * This class is configured in the test resources to create a dummy discovery service
 * {@link SimpleDiscoveryService} which we can kick off to discover each other
 * in the tests, allowing multiple EhCache's in the same VM on different ports to discover
 * each other. It keeps a single instance of the discovery service.
 */
class SimpleDiscoveryServiceFactory implements DiscoveryServiceFactory {
	static SimpleDiscoveryService simpleDiscoveryService;
	static boolean automaticallyTransmitPeerListWhenPeerProviderIsAdded = false;

	@Override
	public synchronized DiscoveryService createDiscoveryService(
			UpdateableHostBasedCacheManagerPeerProvider updateableCacheManagerPeerProvider,
			DiscoveryServiceConfig discoveryServiceConfig) {
		// Create a singular instance of the discovery service
		if (simpleDiscoveryService == null) {
			simpleDiscoveryService = new SimpleDiscoveryService();
		}

		simpleDiscoveryService.setAutomaticallyTransmitPeerListWhenPeerProviderIsAdded(automaticallyTransmitPeerListWhenPeerProviderIsAdded);
		simpleDiscoveryService.addPeerProvider(updateableCacheManagerPeerProvider, discoveryServiceConfig);
		return simpleDiscoveryService;
	}
	
	public static void setAutomaticallyTransmitPeerListWhenPeerProviderIsAdded(
			boolean automaticTransmissionFlag) {
		automaticallyTransmitPeerListWhenPeerProviderIsAdded = automaticTransmissionFlag;
	}
	
	/**
	 * Resets the {@link #simpleDiscoveryService}
	 */
	public static void reset() {
		simpleDiscoveryService = null;
	}

	/**
	 * Gets the only instance of the discovery service in this VM
	 */
	static SimpleDiscoveryService getSimpleDiscoveryService() {
		return simpleDiscoveryService;
	}
	
	/**
	 * Essentially this just maintains a list of hosts
	 * 
	 * @see SimpleDiscoveryServiceFactory
	 */
	class SimpleDiscoveryService implements DiscoveryService {
		private List<UpdateableHostBasedCacheManagerPeerProvider> updateableCacheManagerPeerProviders = new ArrayList<>();
		private List<DiscoveryServiceConfig> discoveryServiceConfigs = new ArrayList<>();
		private AtomicBoolean isActive = new AtomicBoolean(false);
		private boolean automaticallyTransmitPeerListWhenPeerProviderIsAdded;

		public boolean isAutomaticallyTransmitPeerListWhenPeerProviderIsAdded() {
			return automaticallyTransmitPeerListWhenPeerProviderIsAdded;
		}

		/**
		 * Set this to true so that when {@link #addPeerProvider(UpdateableHostBasedCacheManagerPeerProvider, DiscoveryServiceConfig)}
		 * is invoked from the factory {@link SimpleDiscoveryServiceFactory#createDiscoveryService(UpdateableHostBasedCacheManagerPeerProvider, DiscoveryServiceConfig)}
		 * method it will transmit the peer list automatically. This is useful for non-async bootstrapping of
		 * the cache, if this is not done first then the 
		 * @param automaticallyTransmitPeerListWhenPeerProviderIsAdded
		 */
		public void setAutomaticallyTransmitPeerListWhenPeerProviderIsAdded(
				boolean automaticallyTransmitPeerListWhenPeerProviderIsAdded) {
			this.automaticallyTransmitPeerListWhenPeerProviderIsAdded = automaticallyTransmitPeerListWhenPeerProviderIsAdded;
		}

		public void addPeerProvider(
				UpdateableHostBasedCacheManagerPeerProvider updateableCacheManagerPeerProvider,
				DiscoveryServiceConfig discoveryServiceConfig) {
			this.updateableCacheManagerPeerProviders.add(updateableCacheManagerPeerProvider);
			this.discoveryServiceConfigs.add(discoveryServiceConfig);

			// Transmit the peer list?
			if (automaticallyTransmitPeerListWhenPeerProviderIsAdded) {
				transmitPeerList();
			}
		}

		public void removePeerProvider(
				UpdateableHostBasedCacheManagerPeerProvider updateableCacheManagerPeerProvider,
				DiscoveryServiceConfig discoveryServiceConfig) {
			Assert.assertNotNull("The peer provider was not in the discovery list",
					updateableCacheManagerPeerProviders.remove(updateableCacheManagerPeerProvider));
			Assert.assertNotNull("The discovery service config was not in the discovery list",
					discoveryServiceConfigs.remove(discoveryServiceConfig));
		}

		@Override
		public void startDiscoveryService() {
			isActive.set(true);
		}

		@Override
		public void stopDiscoveryService() {
			isActive.set(false);
		}
		
		/**
		 * Kicked off by the tests, transmits the ports to all of the cache manager peer providers
		 * @param ports
		 */
		void transmitPeerList() {
			InetAddress localhostInetAddress;
			try {
				localhostInetAddress = InetAddress.getLocalHost();
			} catch (UnknownHostException e) {
				throw new CacheException("Could not resolve localhost address", e);
			}

			// Create a list of hosts based on the localhost and port number
			Set<CachePeerHost> cachePeerHosts = new HashSet<>();
			for (DiscoveryServiceConfig config : discoveryServiceConfigs) {
				cachePeerHosts.add(new CachePeerHost(localhostInetAddress.getHostAddress(), config.getRmiListenerPort()));
			}
			
			// Now update the hosts list on each of the peer providers
			for (UpdateableHostBasedCacheManagerPeerProvider updateableHostBasedCacheManagerPeerProvider :
				updateableCacheManagerPeerProviders) {
				updateableHostBasedCacheManagerPeerProvider.setCachePeerHosts(cachePeerHosts);
			}
		}

	}

}

package com.xarixa.ehcache.aws;

import java.io.InputStream;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.ReflectionException;

import junit.framework.Assert;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.junit.After;
import org.junit.Test;
import org.powermock.reflect.internal.WhiteboxImpl;

import com.xarixa.ehcache.aws.SimpleDiscoveryServiceFactory.SimpleDiscoveryService;
import com.xarixa.ehcache.aws.discovery.DiscoveryService;

/**
 * Check ehcache-1.xml and ehcache-2.xml test files for configuration here. This creates local EhCache instances.
 *
 * @author t-alexanderb
 *
 */
public class AwsSecurityGroupAwareCacheManagerPeerProviderIntegrationTest {
	
    private static final String TEST_OBJECT_NAMESPACE = "com.xarixa.ehcache.aws.Testing";

	@After
	public void tearDown() {
		// Reset between tests
		SimpleDiscoveryServiceFactory.reset();
	}

    @Test
	public void testThatEhCacheConfigurationWorksForASingleVmInstance() throws IntrospectionException, InstanceNotFoundException, ReflectionException {
		// Set up the two caches from the config files
		CacheManager cacheManager1 = getCacheFromConfiguration("ehcache-1.xml");
		
		try {
			AwsSecurityGroupAwareCacheManagerPeerProviderMBean peerProvider =
				(AwsSecurityGroupAwareCacheManagerPeerProviderMBean)cacheManager1
					.getCacheManagerPeerProvider(AwsSecurityGroupAwareCacheManagerPeerProvider.CACHE_SCHEME);
				Assert.assertEquals("Unexpected peers list: " + peerProvider.getPeerUrls(),
 						0, peerProvider.getPeerUrls().size());				
		} finally {
			cacheManager1.shutdown();
		}
	}

	@Test
	public void testThatCacheNotificationAndReplicationOperatesBetweenTwoCachesOnDifferentPortsInTheSameVm() {
		// Set up the two caches from the config files
		CacheManager cacheManager1 = getCacheFromConfiguration("ehcache-1.xml");
		
		AwsSecurityGroupAwareCacheManagerPeerProvider peerProvider1 =
				(AwsSecurityGroupAwareCacheManagerPeerProvider)cacheManager1
					.getCacheManagerPeerProvider(AwsSecurityGroupAwareCacheManagerPeerProvider.CACHE_SCHEME);

		try {
			CacheManager cacheManager2 = getCacheFromConfiguration("ehcache-2.xml");
			AwsSecurityGroupAwareCacheManagerPeerProvider peerProvider2 =
					(AwsSecurityGroupAwareCacheManagerPeerProvider)cacheManager2
						.getCacheManagerPeerProvider(AwsSecurityGroupAwareCacheManagerPeerProvider.CACHE_SCHEME);

			try {
				// Now get the factory which should be used by the cache managers we just started
				SimpleDiscoveryService simpleDiscoveryService = SimpleDiscoveryServiceFactory.getSimpleDiscoveryService();
				Assert.assertNotNull("Expected an instance of the simple discovery service to exist aftet the caches have been started",
						simpleDiscoveryService);

				// Broadcast the cache locations
				simpleDiscoveryService.transmitPeerList();
				
				Assert.assertEquals("Did not expect another instance of the simple discovery service to exist",
						simpleDiscoveryService, SimpleDiscoveryServiceFactory.getSimpleDiscoveryService());

				//Assert some truisms about the state of the providers and the discovery service
				Assert.assertTrue(peerProvider1.isActive());
				Assert.assertEquals(simpleDiscoveryService, WhiteboxImpl.getInternalState(peerProvider1, DiscoveryService.class));
				Assert.assertTrue(peerProvider2.isActive());
				Assert.assertEquals(simpleDiscoveryService, WhiteboxImpl.getInternalState(peerProvider2, DiscoveryService.class));

				// After startup the two should now be connected via broadcast
				Assert.assertEquals("Unexpected peers list: " + peerProvider1.getPeerUrls(),
						1, peerProvider1.getPeerUrls().size());
				Assert.assertEquals("Unexpected peers list: " + peerProvider2.getPeerUrls(),
						1, peerProvider2.getPeerUrls().size());
		
				// Get the first cache for our object and put an element into it...
				Cache cache1 = cacheManager1.getCache(TEST_OBJECT_NAMESPACE);
				Assert.assertNotNull("Expected cache to exist '" + TEST_OBJECT_NAMESPACE + "'",
						cache1);
				Element element1 = new Element("testKey1", "testValue1");
				cache1.put(element1);
				Assert.assertEquals("The first cache did not contain the element value that was just added to it!",
						element1, cache1.get("testKey1"));

				// This object should now be replicated in the second cache communicated via the RMI service...
				Cache cache2 = cacheManager2.getCache(TEST_OBJECT_NAMESPACE);
				Assert.assertNotSame("The second cache is the same instance as the first cache!", cache1, cache2);
				Assert.assertNotNull("Expected cache to exist '" + TEST_OBJECT_NAMESPACE + "'", cache2);
				Assert.assertEquals("The first cache did not replicate to the second cache",
						element1, cache2.get("testKey1"));

				// Now put an element into the second cache and make sure it appears in the first cache
				Element element2 = new Element("testKey2", "testValue2");
				cache2.put(element2);
				Assert.assertEquals("The second cache did not contain the element value that was just added to it!",
						element2, cache2.get("testKey2"));
				Assert.assertEquals("The second cache did not replicate to the first cache",
						element2, cache1.get("testKey2"));
			} finally {
				cacheManager2.shutdown();
				Assert.assertFalse(peerProvider2.isActive());
			}
		} finally {
			cacheManager1.shutdown();
			Assert.assertFalse(peerProvider1.isActive());
		}
	}

	@Test
	public void testThatWhenANewCacheIsAddedItIsBootstrappedWithPopulatedObjects() {
		// Set up the discovery service so that it automatically transmits the peer list, this way
		// the bootstrap process will work without us communicating the values.
		SimpleDiscoveryServiceFactory.setAutomaticallyTransmitPeerListWhenPeerProviderIsAdded(true);

		// Set up the two caches from the config files
		CacheManager cacheManager1 = getCacheFromConfiguration("ehcache-1.xml");

		// Now get the factory which should be used by the cache managers we just started
		SimpleDiscoveryService simpleDiscoveryService = SimpleDiscoveryServiceFactory.getSimpleDiscoveryService();
		Assert.assertNotNull("Expected an instance of the simple discovery service to exist aftet the caches have been started",
				simpleDiscoveryService);

		// Get the first cache for our object and put an element into it...
		Cache cache1 = cacheManager1.getCache(TEST_OBJECT_NAMESPACE);
		Element element1 = new Element("testKey1", "testValue1");
		cache1.put(element1);
		Assert.assertEquals("The first cache did not contain the element value that was just added to it!",
				element1, cache1.get("testKey1"));
		
		try {
			// Start up the second cache, which should bootstrap values from the first
			CacheManager cacheManager2 = getCacheFromConfiguration("ehcache-2.xml");

			try {
				// Broadcast the cache locations, which will bring the second cache into the cluster
				simpleDiscoveryService.transmitPeerList();

				// This object should have been bootstrapped into the second cache that was just added
				Cache cache2 = cacheManager2.getCache(TEST_OBJECT_NAMESPACE);
				Assert.assertNotSame("The second cache is the same instance as the first cache!", cache1, cache2);
				Assert.assertEquals("The second cache did not bootstrap values from the first cache",
						element1, cache2.get("testKey1"));

				// Now put an element into the second cache and make sure it appears in the first cache to
				// test that after adding into the cache the first and second cache are properly connected
				Element element2 = new Element("testKey2", "testValue2");
				cache2.put(element2);
				Assert.assertEquals("The second cache did not contain the element value that was just added to it!",
						element2, cache2.get("testKey2"));
				Assert.assertEquals("After adding into the cluster, the second cache did not replicate to the first cache",
						element2, cache1.get("testKey2"));
			} finally {
				cacheManager2.shutdown();
			}
		} finally {
			cacheManager1.shutdown();
		}
	}

	@Test
	public void testThatWhenACacheIsRemovedFromTheClusterItWillNotBeTransmittedAnyFurtherUpdates() {
		// Set up the two caches from the config files
		CacheManager cacheManager1 = getCacheFromConfiguration("ehcache-1.xml");

		try {
			// Start up the second cache, which should bootstrap values from the first
			CacheManager cacheManager2 = getCacheFromConfiguration("ehcache-2.xml");

			try {
				// Now get the factory which should be used by the cache managers we just started
				SimpleDiscoveryService simpleDiscoveryService = SimpleDiscoveryServiceFactory.getSimpleDiscoveryService();
				Assert.assertNotNull("Expected an instance of the simple discovery service to exist aftet the caches have been started",
						simpleDiscoveryService);

				// Broadcast the cache locations, which will bring the second cache into the cluster
				simpleDiscoveryService.transmitPeerList();

				// Get the first cache for our object and put an element into it...
				Cache cache1 = cacheManager1.getCache(TEST_OBJECT_NAMESPACE);
				Element element1 = new Element("testKey1", "testValue1");
				cache1.put(element1);
				Assert.assertEquals("The first cache did not contain the element value that was just added to it!",
						element1, cache1.get("testKey1"));
				
				// This object should now be replicated in the second cache communicated via the RMI service...
				Cache cache2 = cacheManager2.getCache(TEST_OBJECT_NAMESPACE);
				Assert.assertNotSame("The second cache is the same instance as the first cache!", cache1, cache2);
				Assert.assertEquals("The second cache did not bootstrap values from the first cache",
						element1, cache2.get("testKey1"));

				// Now remove the second cache from the cluster
				AwsSecurityGroupAwareCacheManagerPeerProvider peerProvider2 =
						(AwsSecurityGroupAwareCacheManagerPeerProvider)cacheManager2
							.getCacheManagerPeerProvider(AwsSecurityGroupAwareCacheManagerPeerProvider.CACHE_SCHEME);
				simpleDiscoveryService.removePeerProvider(peerProvider2, peerProvider2.getDiscoveryServiceConfig());
				simpleDiscoveryService.transmitPeerList();
				
				// Now put an element into the first cache, it should not appear in the second cache now
				Element element2 = new Element("testKey2", "testValue2");
				cache1.put(element2);
				Assert.assertEquals("The first cache did not contain the first element value that was added to it!",
						element1, cache1.get("testKey1"));
				Assert.assertEquals("The first cache did not contain the element value that was just added to it!",
						element2, cache1.get("testKey2"));
				Assert.assertNull("The second cache should not contain the element value that was just added to the first cache!",
						cache2.get("testKey2"));
			} finally {
				cacheManager2.shutdown();
			}
		} finally {
			cacheManager1.shutdown();
		}
	}

	private CacheManager getCacheFromConfiguration(String configResource) {
		InputStream cacheXmlStream = CacheManager.class.getResourceAsStream("/" + configResource);
		Assert.assertNotNull(cacheXmlStream);
		return new CacheManager(cacheXmlStream);
	}

}

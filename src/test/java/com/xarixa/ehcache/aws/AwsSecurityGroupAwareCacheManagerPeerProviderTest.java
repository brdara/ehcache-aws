package com.xarixa.ehcache.aws;

import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.distribution.CachePeer;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.internal.WhiteboxImpl;

import com.xarixa.ehcache.aws.discovery.DiscoveryService;
import com.xarixa.ehcache.aws.discovery.DiscoveryServiceConfig;
import com.xarixa.ehcache.aws.discovery.DiscoveryServiceFactory;

@RunWith(JMock.class)
public class AwsSecurityGroupAwareCacheManagerPeerProviderTest {
    private static final String CACHE_PEERS_MAP_VARIABLE_NAME = "cachePeers";
	private static final String PEER_URLS_SET_VARIABLE_NAME = "peerUrls";
	private Mockery context = new Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
	private TestWithoutRmiLookupAwsSecurityGroupAwareCacheManagerPeerProvider peerProvider;
	private DiscoveryServiceFactory discoveryServiceFactory;
	private DiscoveryServiceConfig discoveryServiceConfig;
    
    @Before
    public void setUp() {
    	discoveryServiceFactory = context.mock(DiscoveryServiceFactory.class);
    	discoveryServiceConfig = context.mock(DiscoveryServiceConfig.class);
    	
    	context.checking(new Expectations() {{
    		atMost(1).of(discoveryServiceConfig).validate();
    	}});
    	
    	peerProvider = new TestWithoutRmiLookupAwsSecurityGroupAwareCacheManagerPeerProvider(
    			discoveryServiceConfig, discoveryServiceFactory);
    }
    
	@Test
	public void testThatInitSetsTheActiveStatusAndInitialisesTheDiscoveryService() {
		invokeInitAndCheckDiscoveryServiceHasBeenStarted();
	}

	@Test
	public void testThatDisposeUnsetsTheActiveStatusAndStopsTheDiscoveryService() {
		final DiscoveryService discoveryService = context.mock(DiscoveryService.class);
		final Sequence seq = context.sequence("StartStopSequence");
		
		context.checking(new Expectations() {{
			atMost(1).of(discoveryServiceFactory).createDiscoveryService(peerProvider, discoveryServiceConfig);
			inSequence(seq);

			atMost(1).of(discoveryService).startDiscoveryService();
			inSequence(seq);
			
			atMost(1).of(discoveryService).stopDiscoveryService();
			inSequence(seq);
		}});

		peerProvider.init();
		Assert.assertTrue(peerProvider.isActive());
		peerProvider.dispose();
		Assert.assertFalse(peerProvider.isActive());
	}

	@Test
	public void testSetCachePeerHostsSetsANewListOfHosts() {
		invokeInitAndCheckDiscoveryServiceHasBeenStarted();

		context.checking(new Expectations() {{
			allowing(discoveryServiceConfig).getRmiListenerPort(); will(returnValue(61616));
		}});
		
		// Make sure that the initial list is empty
		Assert.assertTrue(((Set<String>)WhiteboxImpl.getInternalState(peerProvider, PEER_URLS_SET_VARIABLE_NAME)).isEmpty());

		// Set up a list of hosts. Because these hosts are actually resolved they need to be real.
		Set<CachePeerHost> cachePeerHosts = new HashSet<>();
		cachePeerHosts.add(new CachePeerHost("www.google.com", 61616));
		cachePeerHosts.add(new CachePeerHost("www.yahoo.com", 61618));

		// Set the hosts
		peerProvider.setCachePeerHosts(cachePeerHosts);
		
		// Check that RMI URL's should be formed from the values
		Set<String> peerUrls = (Set<String>)WhiteboxImpl.getInternalState(peerProvider, PEER_URLS_SET_VARIABLE_NAME);
		Assert.assertEquals(2, peerUrls.size());
		Assert.assertTrue(peerUrls.contains("//www.google.com:61616"));
		Assert.assertTrue(peerUrls.contains("//www.yahoo.com:61618"));
	}

	@Test
	public void testSetCachePeerHostsUpdatesTheSetOfHostsWhenAHostHasBeenRemoved() {
		invokeInitAndCheckDiscoveryServiceHasBeenStarted();

		context.checking(new Expectations() {{
			allowing(discoveryServiceConfig).getRmiListenerPort(); will(returnValue(61616));
		}});

		// Set up a list of hosts. Because these hosts are actually resolved they need to be real.
		Set<CachePeerHost> cachePeerHosts = new HashSet<>();
		CachePeerHost googleCachePeerHost = new CachePeerHost("www.google.com", 61616);
		cachePeerHosts.add(googleCachePeerHost);
		CachePeerHost yahooCachePeerHost = new CachePeerHost("www.yahoo.com", 61618);
		cachePeerHosts.add(yahooCachePeerHost);

		// Set the hosts
		peerProvider.setCachePeerHosts(cachePeerHosts);
		
		// Check that RMI URL's should be formed from the values
		Set<String> peerUrls = (Set<String>)WhiteboxImpl.getInternalState(peerProvider, PEER_URLS_SET_VARIABLE_NAME);
		Assert.assertEquals(2, peerUrls.size());
		Assert.assertTrue(peerUrls.contains("//www.google.com:61616"));
		Assert.assertTrue(peerUrls.contains("//www.yahoo.com:61618"));

		// Remove the first one so that we should only have one in the list and set this
		Assert.assertTrue(cachePeerHosts.remove(googleCachePeerHost));
		peerProvider.setCachePeerHosts(cachePeerHosts);
		
		// Check that RMI URL's should be formed from the values
		peerUrls = (Set<String>)WhiteboxImpl.getInternalState(peerProvider, PEER_URLS_SET_VARIABLE_NAME);
		Assert.assertEquals(1, peerUrls.size());
		Assert.assertTrue(peerUrls.contains("//www.yahoo.com:61618"));
	}

	@Test
	public void testListRemoteCachePeersBeforeAnythingHasBeenSetReturnsAnEmptyUnmodifiableList() {
		invokeInitAndCheckDiscoveryServiceHasBeenStarted();

		final Ehcache cache = context.mock(Ehcache.class);
		context.checking(new Expectations() {{
			allowing(cache).getName(); will(returnValue("myCache"));
		}});

		// Now list the peers, which should create a new entry in the map and return a list of CachePeer's
		List<CachePeer> listRemoteCachePeers = peerProvider.listRemoteCachePeers(cache);
		Assert.assertTrue(listRemoteCachePeers.isEmpty());

		try {
			listRemoteCachePeers.add(context.mock(CachePeer.class, "dummyCachePeer"));
			Assert.fail("Did not expect to be able to add a member to the cache peers list that was returned");
		} catch (UnsupportedOperationException e) {
			// OK
		}
	}

	@Test
	public void testListRemoteCachePeersReturnsAListofPeersPerCacheAndUpdatesTheInternalMap() {
		invokeInitAndCheckDiscoveryServiceHasBeenStarted();

		// Mock some stuff up
		final Ehcache cache = context.mock(Ehcache.class);
		final CachePeer googleCacheRemote = context.mock(CachePeer.class, "googleCacheRemote");
		final CachePeer yahooCacheRemote = context.mock(CachePeer.class, "yahooCacheRemote");

		// We are expecting RMI Naming.lookup calls to these URL's for the cache
		final String cacheName = "myCache";
		peerProvider.addCachePeerPerUrl("//www.google.com:61616/" + cacheName, googleCacheRemote);
		peerProvider.addCachePeerPerUrl("//www.yahoo.com:61618/" + cacheName, yahooCacheRemote);
		
		context.checking(new Expectations() {{
			allowing(discoveryServiceConfig).getRmiListenerPort(); will(returnValue(61616));
			allowing(cache).getName(); will(returnValue(cacheName));
		}});
		
		// Make sure that the initial list is empty
		Assert.assertTrue(((Map<String,List<CachePeer>>)WhiteboxImpl.getInternalState(peerProvider, CACHE_PEERS_MAP_VARIABLE_NAME)).isEmpty());

		// Set up a list of hosts. Because these hosts are actually resolved they need to be real.
		Set<CachePeerHost> cachePeerHosts = new HashSet<>();
		cachePeerHosts.add(new CachePeerHost("www.google.com", 61616));
		cachePeerHosts.add(new CachePeerHost("www.yahoo.com", 61618));

		// Set the hosts
		peerProvider.setCachePeerHosts(cachePeerHosts);
		
		// Now list the peers, which should create a new entry in the map and return a list of CachePeer's
		List<CachePeer> listRemoteCachePeers = peerProvider.listRemoteCachePeers(cache);
		Assert.assertEquals(2, listRemoteCachePeers.size());
		Assert.assertTrue(listRemoteCachePeers.contains(googleCacheRemote));
		Assert.assertTrue(listRemoteCachePeers.contains(yahooCacheRemote));

		try {
			listRemoteCachePeers.add(context.mock(CachePeer.class, "dummyCachePeer"));
			Assert.fail("Did not expect to be able to add a member to the cache peers list that was returned");
		} catch (UnsupportedOperationException e) {
			// OK
		}

		// Check that the cache has also been populated with this list
		Map<String,List<CachePeer>> cachePeers =
				(Map<String,List<CachePeer>>)WhiteboxImpl.getInternalState(peerProvider, CACHE_PEERS_MAP_VARIABLE_NAME);
		Assert.assertEquals(1, cachePeers.size());
		Assert.assertEquals(listRemoteCachePeers, cachePeers.get(cacheName));
		
		// Another invocation should return exactly the same object, i.e. this list should not be recreated
		Assert.assertEquals(listRemoteCachePeers.hashCode(), peerProvider.listRemoteCachePeers(cache).hashCode());
	}
	
	@Test
	public void testListRemoteCachePeersCreatesANewListWhenAHostHasBeenRemoved() {
		invokeInitAndCheckDiscoveryServiceHasBeenStarted();

		// Mock some stuff up
		final Ehcache cache = context.mock(Ehcache.class);
		final CachePeer googleCacheRemote = context.mock(CachePeer.class, "googleCacheRemote");
		final CachePeer yahooCacheRemote = context.mock(CachePeer.class, "yahooCacheRemote");

		// We are expecting RMI Naming.lookup calls to these URL's for the cache
		final String cacheName = "myCache";
		peerProvider.addCachePeerPerUrl("//www.google.com:61616/" + cacheName, googleCacheRemote);
		peerProvider.addCachePeerPerUrl("//www.yahoo.com:61618/" + cacheName, yahooCacheRemote);
		
		context.checking(new Expectations() {{
			allowing(discoveryServiceConfig).getRmiListenerPort(); will(returnValue(61616));
			allowing(cache).getName(); will(returnValue(cacheName));
		}});
		
		// Make sure that the initial list is empty
		Assert.assertTrue(((Map<String,List<CachePeer>>)WhiteboxImpl.getInternalState(peerProvider, CACHE_PEERS_MAP_VARIABLE_NAME)).isEmpty());

		// Set up a list of hosts. Because these hosts are actually resolved they need to be real.
		Set<CachePeerHost> cachePeerHosts = new HashSet<>();
		CachePeerHost googleCachePeerHost = new CachePeerHost("www.google.com", 61616);
		cachePeerHosts.add(googleCachePeerHost);
		CachePeerHost yahooCachePeerHost = new CachePeerHost("www.yahoo.com", 61618);
		cachePeerHosts.add(yahooCachePeerHost);

		// Set the hosts
		peerProvider.setCachePeerHosts(cachePeerHosts);
		
		// Now list the peers, which should create a new entry in the map and return a list of CachePeer's
		List<CachePeer> listRemoteCachePeers = peerProvider.listRemoteCachePeers(cache);
		Assert.assertEquals(2, listRemoteCachePeers.size());
		Assert.assertTrue(listRemoteCachePeers.contains(googleCacheRemote));
		Assert.assertTrue(listRemoteCachePeers.contains(yahooCacheRemote));

		// Now remove google cache peer
		Assert.assertTrue(cachePeerHosts.remove(googleCachePeerHost));
		peerProvider.setCachePeerHosts(cachePeerHosts);
		listRemoteCachePeers = peerProvider.listRemoteCachePeers(cache);
		Assert.assertEquals(1, listRemoteCachePeers.size());
		Assert.assertTrue(listRemoteCachePeers.contains(yahooCacheRemote));
	}
	



	private void invokeInitAndCheckDiscoveryServiceHasBeenStarted() {
		final DiscoveryService discoveryService = context.mock(DiscoveryService.class);
		
		context.checking(new Expectations() {{
			atMost(1).of(discoveryServiceFactory).createDiscoveryService(peerProvider, discoveryServiceConfig);
			atMost(1).of(discoveryService).startDiscoveryService();
		}});
		
		peerProvider.init();
		Assert.assertTrue(peerProvider.isActive());
	}

	private class TestWithoutRmiLookupAwsSecurityGroupAwareCacheManagerPeerProvider
		extends AwsSecurityGroupAwareCacheManagerPeerProvider {
		private Map<String,CachePeer> cachePeersPerUrlMap = new HashMap<String,CachePeer>();

		public TestWithoutRmiLookupAwsSecurityGroupAwareCacheManagerPeerProvider(
				CacheManager cacheManager) {
			super(cacheManager);
		}

		public TestWithoutRmiLookupAwsSecurityGroupAwareCacheManagerPeerProvider(
				DiscoveryServiceConfig discoveryServiceConfig,
				DiscoveryServiceFactory discoveryServiceFactory) {
			super(discoveryServiceConfig, discoveryServiceFactory);
		}

		private void addCachePeerPerUrl(String url, CachePeer cachePeer) {
			cachePeersPerUrlMap.put(url, cachePeer);
		}

		/**
		 * Override this method which does RMI lookups
		 */
		@Override
		public CachePeer lookupRemoteCachePeer(String url) throws MalformedURLException, NotBoundException, RemoteException {
			CachePeer cachePeer = cachePeersPerUrlMap.get(url);
			Assert.assertNotNull("The cache peer for URL '" + url + "' has not been set up in the test", cachePeer);
			return cachePeer;
		}
	}

}

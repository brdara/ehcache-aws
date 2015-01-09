package com.xarixa.ehcache.aws;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.distribution.CachePeer;
import net.sf.ehcache.distribution.RMICacheManagerPeerProvider;
import net.sf.ehcache.management.ManagedCacheManagerPeerProvider;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xarixa.ehcache.aws.discovery.AwsDiscoveryServiceConfig;
import com.xarixa.ehcache.aws.discovery.DiscoveryService;
import com.xarixa.ehcache.aws.discovery.DiscoveryServiceConfig;
import com.xarixa.ehcache.aws.discovery.DiscoveryServiceFactory;

public class AwsSecurityGroupAwareCacheManagerPeerProvider extends RMICacheManagerPeerProvider implements ManagedCacheManagerPeerProvider, UpdateableHostBasedCacheManagerPeerProvider, AwsSecurityGroupAwareCacheManagerPeerProviderMBean {
	/**
	 * Parser for URL's of the form <strong>//HOST:PORT/...</strong>
	 */
	private static final ThreadLocal<Matcher> EXTRACT_HOSTNAMES_REGEX = new ThreadLocal<Matcher>() {
		protected Matcher initialValue() {
			return Pattern.compile("^//([\\w\\-\\_\\.]+)(:[0-9]+)??(\\/.*)??$").matcher("");
		}
	};

	private static final Logger LOG = LoggerFactory.getLogger(AwsSecurityGroupAwareCacheManagerPeerProvider.class.getName());

    /**
     * @see #getScheme()
     */
	public static final String CACHE_SCHEME = "RMI";

	/**
	 * true after {@link #init()} is invoked, false before that or after {@link #dispose()}
	 */
	private final AtomicBoolean isActive = new AtomicBoolean(false);

    /**
     * Local address of this host
     */
    private final InetAddress localhostInetAddress;

    /**
     * A lock for {@link #peerUrls}
     */
    private final ReadWriteLock peerUrlsLock = new ReentrantReadWriteLock(true);

    /**
     * Contains RMI URLs of the form: <strong>//HOST:PORT</strong>
     */
    private Set<String> peerUrls = new HashSet<>();
    
    /**
     * A lock for {@link #cachePeers}
     */
    private final ReadWriteLock cachePeersLock = new ReentrantReadWriteLock(true);

    /**
     * A transient list of cache peers by cache
     * @see #listRemoteCachePeers(Ehcache)
     */
    private Map<String,List<CachePeer>> cachePeers = new HashMap<String,List<CachePeer>>();

    /**
     * A discovery service for AWS peer discovery
     */
    private DiscoveryService discoveryService;
    
    /**
     * Configuration for the discovery service
     */
	private final DiscoveryServiceConfig discoveryServiceConfig;

	/**
	 * Factory used to create a discovery service to update this set of hosts with through the
	 * implemented {@link UpdateableHostBasedCacheManagerPeerProvider} interface.
	 */
	private final DiscoveryServiceFactory discoveryServiceFactory;

	public AwsSecurityGroupAwareCacheManagerPeerProvider(DiscoveryServiceConfig discoveryServiceConfig,
			DiscoveryServiceFactory discoveryServiceFactory) {
		this.discoveryServiceFactory = discoveryServiceFactory;
		discoveryServiceConfig.validate();
		this.discoveryServiceConfig = discoveryServiceConfig;
		try {
			localhostInetAddress = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			throw new CacheException("Could not resolve localhost address", e);
		}
		LOG.debug("Localhost is: {}", localhostInetAddress);
    }

    public AwsSecurityGroupAwareCacheManagerPeerProvider(CacheManager cacheManager) {
    	throw new UnsupportedOperationException();
    }

    @Override
	public DiscoveryServiceConfig getDiscoveryServiceConfig() {
		return discoveryServiceConfig;
	}

    /**
     * Whether this cache is active or not
     * @return
     */
    public boolean isActive() {
    	return isActive.get();
    }

    /**
     * Initialises the discovery service
     */
    @Override
    public void init() {
    	if (isActive.get()) {
			LOG.warn("Cache manager has already been initialised for: " +
					discoveryServiceConfig.toString());
			return;
    	}

		discoveryService = discoveryServiceFactory.createDiscoveryService(this, discoveryServiceConfig);
		discoveryService.startDiscoveryService();
		isActive.set(true);
    }

    @Override
    public void registerPeer(String rmiUrl) {
		if (!isLocalCachePeer(rmiUrl) && !this.peerUrls.contains(rmiUrl)) {
			this.peerUrls.add(rmiUrl);
		}
	}

	/**
	 * Checks if this RMI URL (i.e. //HOST:PORT/...) is for the current cache host,
	 * derived from the local host name/IP and configured
	 * {@link AwsDiscoveryServiceConfig#getRmiListenerPort() listener port}.
	 * 
	 * @param rmiUrl
	 * @return
	 * @see #isLocalCacheHost(String, Integer)
	 */
	boolean isLocalCachePeer(String rmiUrl) {
		Matcher peerHostnameMatcher = EXTRACT_HOSTNAMES_REGEX.get().reset(rmiUrl);

		if (peerHostnameMatcher.matches() && peerHostnameMatcher.groupCount() >= 2) {
			String host = peerHostnameMatcher.group(1);
			Integer portNumber = Integer.parseInt(peerHostnameMatcher.group(2));
			return isLocalCacheHost(host, portNumber);
		} else {
			throw new RuntimeException("Cannot extract hostname from RMI URL '" + rmiUrl + "'");
		}
	}

	/**
	 * @see #isLocalCacheHost(String, Integer)
	 */
	boolean isLocalCacheHost(CachePeerHost host) {
		return isLocalCacheHost(host.getHostname(), host.getPort());
	}

	/**
	 * Goes further than {@link #isLocalCacheHost(String)} by checking the hostname and port
	 * number. This is useful when multiple EhCache's are running on the same VM on different
	 * ports.
	 * 
	 * @param hostname
	 * @param port
	 * @return
	 */
	boolean isLocalCacheHost(String hostname, Integer port) {
		return port.equals(discoveryServiceConfig.getRmiListenerPort()) && isLocalCacheHost(hostname);
	}

	/**
	 * Complements {@link #isLocalCachePeer(String)}, checks the host IP/DNS only
	 * @param host
	 */
	boolean isLocalCacheHost(String hostname) {
		InetAddress[] allHostNames;
		try {
			allHostNames = InetAddress.getAllByName(hostname);
		} catch (UnknownHostException e) {
			throw new RuntimeException("Could not get localhost IP address!", e);
		}

		for (InetAddress remoteHost : allHostNames) {
			if (localhostInetAddress.getHostAddress().equals(remoteHost.getHostAddress())) {
				return true;
			}
		}

		return false;
	}

	@Override
	public void setCachePeerHosts(Set<CachePeerHost> cachePeerHosts) {
		LOG.debug("Updating RMI cache peers list: {}", cachePeerHosts);
		Set<String> newPeerUrls = new HashSet<>(cachePeerHosts.size());
		boolean updated = false;
		peerUrlsLock.readLock().lock();

		try {
			for (CachePeerHost cachePeerHost : cachePeerHosts) {
				// Don't add the local peer in, we don't want comms with the same discovery instance!
				if (!isLocalCacheHost(cachePeerHost)) {
					// Build the peer URL
					String peerUrl = new StringBuilder("//")
						.append(cachePeerHost.getHostname())
						.append(':')
						.append(cachePeerHost.getPort())
						.toString();
	
					// Is this an update?
					updated = !peerUrls.contains(peerUrl);
					newPeerUrls.add(peerUrl);
				}
			}
	
			// Update the list only if necessary, if there was a new entry added or there were deletions
			if (updated || peerUrls.size() != newPeerUrls.size()) {
				// Upgrade to a write lock
				peerUrlsLock.readLock().unlock();
				peerUrlsLock.writeLock().lock();

				try {
					peerUrls = newPeerUrls;

					// Downgrade the lock to a read lock
					peerUrlsLock.readLock().lock();
				} finally {
					peerUrlsLock.writeLock().unlock();
				}
				
				// Wipe out the list of cache peers so that they can be recreated in listRemoteCachePeers
				cachePeersLock.writeLock().lock();
				try {
					cachePeers.clear();
				} finally {
					cachePeersLock.writeLock().unlock();
				}

				LOG.info("Updated the RMI cache peers list: {}", newPeerUrls);
			}
		} finally {
			peerUrlsLock.readLock().unlock();
		}
	}

	public final List<CachePeer> listRemoteCachePeers(Ehcache cache)
			throws CacheException {
        List<CachePeer> remoteCachePeers;
        String cacheName = cache.getName();

        // First attempt to extract any cached peers list
		cachePeersLock.readLock().lock();
		try {
			remoteCachePeers = cachePeers.get(cacheName);
		} finally {
			cachePeersLock.readLock().unlock();
		}

		// Return anything we have
		if (remoteCachePeers != null) {
			return remoteCachePeers;
		}

		// Try to update, get the write lock for our cache peers map
		cachePeersLock.writeLock().lock();
		
		try {
			// Daah-ble check! This may have been updated by another thread before we got the write lock.
			remoteCachePeers = cachePeers.get(cacheName);
			if (remoteCachePeers != null) {
				return remoteCachePeers;
			}

			// OK at this point we have the write lock and definitely need to create the list
			remoteCachePeers = new ArrayList<CachePeer>();
	        
	        // Get the read lock for the peer URL's list
	        peerUrlsLock.readLock().lock();
	
	        try {
				for (String rmiUrl : peerUrls) {
		            String cacheRmiUrl = createFullyQualifiedRmiCacheUrl(rmiUrl, cacheName);
		
		            CachePeer cachePeer = null;
		            try {
		                cachePeer = lookupRemoteCachePeer(cacheRmiUrl);
		                remoteCachePeers.add(cachePeer);
		            } catch (Exception e) {
		                if (LOG.isDebugEnabled()) {
		                    LOG.debug("Exception looking up RMI URL {}. This may be normal if a node has gone offline, "
		                    		+ "or it may indicate network connectivity issues.", rmiUrl, e);
		                }
		            }
		        }
			} finally {
				// Release read lock
				peerUrlsLock.readLock().unlock();
			}
	        
	        // Finally set this up in the map before releasing the write lock, making it available to other threads
	        remoteCachePeers = Collections.unmodifiableList(remoteCachePeers);
	        cachePeers.put(cacheName, remoteCachePeers);
		} finally {
			cachePeersLock.writeLock().unlock();
		}

        return remoteCachePeers;
	}

    /**
     * Gets the fully qualified RMI location for the cache name given a partial RMI URL, i.e.
     * <strong>//HOST:PORT/CACHE_NAME</strong>
     * @param rmiUrl	Partial RMI URL
     * @return The fully qualified RMI location of the cache
     */
    static String createFullyQualifiedRmiCacheUrl(String rmiUrl, String cacheName) {
        return new StringBuilder().append(rmiUrl).append('/').append(cacheName).toString();
    }

    @Override
    protected boolean stale(Date date) {
    	return false;
    }

    @Override
    public CachePeer lookupRemoteCachePeer(String url) throws MalformedURLException, NotBoundException, RemoteException {
        LOG.debug("Lookup remote cache peer URL {}", url);
        return (CachePeer)Naming.lookup(url);
    }

    @Override
    public void dispose() throws CacheException {
    	if (!isActive.get()) {
			LOG.warn("Cache manager has not been initialised or dispose has already been called for: " +
					discoveryServiceConfig.toString());
			return;
    	}

    	discoveryService.stopDiscoveryService();
    	discoveryService = null;
    	isActive.set(false);
    }

    @Override
    public String getScheme() {
        return CACHE_SCHEME;
    }

	@Override
	public long getTimeForClusterToForm() {
		return 0;
	}

	@Override
	public void register(MBeanServer mBeanServer) {
		ObjectName jmxObjectName = null;
		try {
			jmxObjectName = new ObjectName(mBeanServer.getDefaultDomain(), "name",
					StringUtils.substringAfter(AwsSecurityGroupAwareCacheManagerPeerProvider.class.getName(), "."));
		} catch(Exception e) {
			throw new CacheException("Could not create JMX object, malformed name", e);
		}

		try {
			mBeanServer.registerMBean(this, jmxObjectName);
		} catch(Exception e) {
			throw new CacheException("Could not create JMX object, malformed name", e);
		}
	}

	/**
	 * Mainly used for testing to retrieve the current list of peer URL's
	 * @return 
	 */
	@Override
	public Set<String> getPeerUrls() {
		peerUrlsLock.readLock().lock();
		
		try {
			return Collections.unmodifiableSet(peerUrls);
		} finally {
			peerUrlsLock.readLock().unlock();
		}
	}

}

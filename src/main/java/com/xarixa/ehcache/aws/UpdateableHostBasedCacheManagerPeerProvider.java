package com.xarixa.ehcache.aws;

import java.util.Set;

import net.sf.ehcache.distribution.CacheManagerPeerProvider;

/**
 * A {@link CacheManagerPeerProvider} which can have the set of hosts updated from an external mechanism.
 * 
 * @author Fabric WorldWide
 */
public interface UpdateableHostBasedCacheManagerPeerProvider extends CacheManagerPeerProvider {

	/**
	 * Sets the cache peer hosts from the given set of hosts. This list is the new list of hosts,
	 * not an updated delta list but the full list. The implementation must determine what to do
	 * with discarded entries.
	 * 
	 * @param cachePeerHosts	A collection of host IP's/DNS names
	 */
	void setCachePeerHosts(Set<CachePeerHost> cachePeerHosts);

}

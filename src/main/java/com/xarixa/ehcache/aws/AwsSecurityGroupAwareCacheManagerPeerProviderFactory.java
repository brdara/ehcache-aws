package com.xarixa.ehcache.aws;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.distribution.CacheManagerPeerListener;
import net.sf.ehcache.distribution.CacheManagerPeerProvider;
import net.sf.ehcache.distribution.RMICacheManagerPeerProviderFactory;
import net.sf.ehcache.util.PropertyUtil;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xarixa.ehcache.aws.discovery.AwsDiscoveryServiceConfig;
import com.xarixa.ehcache.aws.discovery.AwsSecurityGroupAwareDiscoveryServiceFactory;
import com.xarixa.ehcache.aws.discovery.DiscoveryServiceFactory;

/**
 * An {@link RMICacheManagerPeerProviderFactory} that works using AWS security groups to discover
 * peers. To use this, mark instances that have EhCache running on them with a security
 * group and provide configuration to this factory to enable.
 * 
 * @author Fabric WorldWide
 */
public class AwsSecurityGroupAwareCacheManagerPeerProviderFactory extends RMICacheManagerPeerProviderFactory {
	private static final Logger LOG = LoggerFactory
			.getLogger(AwsSecurityGroupAwareCacheManagerPeerProviderFactory.class.getName());
    private static final String AWS_SECURITY_GROUP_PROP = "securityGroup";
    private static final String AWS_SECURITY_GROUP_REFRESH_INTERVAL_PROP = "securityGroupRefreshInterval";
    private static final String DISCOVERY_SERVICE_CLASS_PROP = "discoveryServiceClass";
    private static final Pattern UNIQUE_RESOURCE_PORT_REGEX = Pattern.compile("^.*: ([0-9]+)$");
    private static final Map<Integer, AwsSecurityGroupAwareCacheManagerPeerProvider> cachePeerProviders = new HashMap<>();
    private static final ReentrantLock cachePeerProvidersLock = new ReentrantLock();

	@Override
	public CacheManagerPeerProvider createCachePeerProvider(
			CacheManager cacheManager, Properties properties)
			throws CacheException {
    	CacheManagerPeerListener cachePeerListener = cacheManager.getCachePeerListener("RMI");
    	if (cachePeerListener == null) {
    		throw new CacheException("Could not retrieve RMI cache peer listener. " +
    				"Please check your configuration, the class of the cacheManagerPeerListenerFactory " +
    				"element should be set to 'net.sf.ehcache.distribution.RMICacheManagerPeerListenerFactory'");
    	}
    	
      	// This returns "RMI listener port: 61616" from which we can parse the port number out
    	Matcher portMatcher =
    		UNIQUE_RESOURCE_PORT_REGEX.matcher(cachePeerListener.getUniqueResourceIdentifier());
    	if (!portMatcher.matches()) {
    		throw new CacheException("Could not parse RMI listener port from unique " +
    				"resource identifier string: '" + 
    				cachePeerListener.getUniqueResourceIdentifier() + 
    				"'");
    	}

    	AwsDiscoveryServiceConfig config = new AwsDiscoveryServiceConfig();
    	config.setRmiListenerPort(Integer.parseInt(portMatcher.group(1)));
    	config.setAccessKey(AwsSecurityUtils.getInstance().getAwsAccessKey());
    	config.setSecretKey(AwsSecurityUtils.getInstance().getAwsSecretKey());
   	 	config.setSecurityGroup(PropertyUtil.extractAndLogProperty(AWS_SECURITY_GROUP_PROP, properties));
   	 	String securityGroupRefreshIntervalString =
   	 			PropertyUtil.extractAndLogProperty(AWS_SECURITY_GROUP_REFRESH_INTERVAL_PROP, properties);
   	 	
   	 	try {
   	 		config.setSecurityGroupRefreshInterval(Integer.parseInt(securityGroupRefreshIntervalString));
   	 	} catch (NumberFormatException e) {
   	 		throw new CacheException("Expected an integer interval in ms for " +
   	 				AWS_SECURITY_GROUP_REFRESH_INTERVAL_PROP +
   	 				", but got '" + securityGroupRefreshIntervalString + "'");
   	 	}
   	 	
   	 	// Now attempt to get the class for the service factory
   	 	DiscoveryServiceFactory discoveryServiceFactory =
   	 			getDefaultOrConfiguredDiscoveryServiceFactory(
   	 					PropertyUtil.extractAndLogProperty(DISCOVERY_SERVICE_CLASS_PROP, properties));

   	 	cachePeerProvidersLock.lock();
		try {
			// Does this provider already exist?
			AwsSecurityGroupAwareCacheManagerPeerProvider rmiPeerProvider = cachePeerProviders.get(config.hashCode());
			if (rmiPeerProvider != null && rmiPeerProvider.isActive()) {
				LOG.debug("Returning cached RMI peer provider instance " + rmiPeerProvider.toString());
				return rmiPeerProvider;
			}

			// Create a new provider
			LOG.debug("Creating a new AWS security group aware RMI peer provider instance...");
			rmiPeerProvider = new AwsSecurityGroupAwareCacheManagerPeerProvider(config, discoveryServiceFactory);
			LOG.debug("Created a new AWS security group aware RMI peer provider instance {}", rmiPeerProvider.toString());
	        cachePeerProviders.put(config.hashCode(), rmiPeerProvider);
			return rmiPeerProvider;
		} finally {
			cachePeerProvidersLock.unlock();
		}
	}

	DiscoveryServiceFactory getDefaultOrConfiguredDiscoveryServiceFactory(String configuredDiscoveryServiceFactoryClassName) {
		// Default to the known factory class or use the configured one if it is set
		String discoveryServiceClassName =
				StringUtils.defaultString(configuredDiscoveryServiceFactoryClassName,
						AwsSecurityGroupAwareDiscoveryServiceFactory.class.getName());
		
		// Get the factory class
		Class<?> factoryClass;
		try {
			factoryClass =
					AwsSecurityGroupAwareDiscoveryServiceFactory.class.getClassLoader().loadClass(discoveryServiceClassName);
		} catch (ClassNotFoundException e) {
			throw new CacheException("Cannot find discovery service factory class " + discoveryServiceClassName, e);
		}

		// Use no-arg constructor to return the factory
		try {
			return (DiscoveryServiceFactory)factoryClass.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new CacheException("Could not create an instance of " + discoveryServiceClassName +
					" using the no-arg constructor", e);
		}
	}

}

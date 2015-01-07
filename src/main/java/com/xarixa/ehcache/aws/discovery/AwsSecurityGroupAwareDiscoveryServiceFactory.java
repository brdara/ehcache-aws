package com.xarixa.ehcache.aws.discovery;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import com.xarixa.ehcache.aws.AwsSecurityGroupAwareCacheManagerPeerProviderFactory;
import com.xarixa.ehcache.aws.UpdateableHostBasedCacheManagerPeerProvider;

/**
 * Default {@link DiscoveryServiceFactory} used by the {@link AwsSecurityGroupAwareCacheManagerPeerProviderFactory}.
 * @author Fabric WorldWide
 */
public class AwsSecurityGroupAwareDiscoveryServiceFactory implements DiscoveryServiceFactory {
	private static final int MAX_SCHEDULED_THREAD_POOL_SIZE = 3;
	private static final ScheduledExecutorService discoveryServiceExecutor = createIntervalBasedExecutor();

	private static ScheduledExecutorService createIntervalBasedExecutor() {
		ThreadFactory threadFactory = new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r);
				t.setDaemon(true);
				t.setPriority(Thread.MIN_PRIORITY);
				t.setName("AwsDiscoveryServiceTimerThread-" + t.getThreadGroup().activeCount());
				return t;
			}
		};

		return Executors.newScheduledThreadPool(MAX_SCHEDULED_THREAD_POOL_SIZE, threadFactory);
	}

	@Override
	public DiscoveryService createDiscoveryService(
			UpdateableHostBasedCacheManagerPeerProvider updateableCacheManagerPeerProvider,
			DiscoveryServiceConfig discoveryServiceConfig) {
		if (discoveryServiceConfig instanceof AwsDiscoveryServiceConfig) {
			return new AwsSecurityGroupAwareDiscoveryService(updateableCacheManagerPeerProvider,
					discoveryServiceExecutor, (AwsDiscoveryServiceConfig)discoveryServiceConfig);
		}
		
		throw new IllegalArgumentException("The discovery service config of type " +
				discoveryServiceConfig.getClass().getName() + " is invalid, it must be of the type " +
				AwsDiscoveryServiceConfig.class.getName());
	}

}

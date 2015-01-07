package com.xarixa.ehcache.aws.discovery;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A discovery service which runs on a schedule as defined in the configuration.
 * 
 * @author Fabric WorldWide
 */
public abstract class AbstractScheduledIntervalDiscoveryService implements DiscoveryService, Runnable {
	public final static int DEFAULT_START_INTERVAL_MS = 0;
	private final ScheduledExecutorService discoveryServiceExecutor;
	private final AwsDiscoveryServiceConfig discoveryServiceConfig;
	private ScheduledFuture<?> scheduled;

	public AbstractScheduledIntervalDiscoveryService(ScheduledExecutorService discoveryServiceExecutor,
			AwsDiscoveryServiceConfig discoveryServiceConfig) {
		this.discoveryServiceExecutor = discoveryServiceExecutor;
		this.discoveryServiceConfig = discoveryServiceConfig;
	}

	@Override
	public final void startDiscoveryService() {
		preStartDiscoveryService();
		// Schedule discovery according to the interval
		scheduled = discoveryServiceExecutor.scheduleWithFixedDelay(this, DEFAULT_START_INTERVAL_MS,
				discoveryServiceConfig.getSecurityGroupRefreshInterval(), TimeUnit.MILLISECONDS);
		postStartDiscoveryService();
	}
	
	/**
	 * Executed before thread execution is started and the run method is scheduled in {@link #startDiscoveryService()}
	 */
	protected void preStartDiscoveryService() {
	}

	/**
	 * Executed after thread execution is started and the run method is scheduled in {@link #startDiscoveryService()}
	 */
	protected void postStartDiscoveryService() {
	}
	
	@Override
	public final void stopDiscoveryService() {
		preStopDiscoveryService();
		try {
			scheduled.cancel(true);
		} finally {
			postStopDiscoveryService();
		}
	}

	/**
	 * Executed before thread execution is stopped and the run method is unscheduled in {@link #stopDiscoveryService()}
	 */
	protected void preStopDiscoveryService() {
	}

	/**
	 * Executed after thread execution is stopped and the run method is unscheduled in {@link #stopDiscoveryService()}
	 */
	protected void postStopDiscoveryService() {
	}
	

}

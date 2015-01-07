package com.xarixa.ehcache.aws.discovery;

import static com.google.common.base.Predicates.and;
import static com.google.common.collect.Iterables.filter;
import static org.jclouds.compute.predicates.NodePredicates.inGroup;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.predicates.NodePredicates;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.xarixa.ehcache.aws.CachePeerHost;
import com.xarixa.ehcache.aws.UpdateableHostBasedCacheManagerPeerProvider;

/**
 * A discovery service that works against AWS security groups by discovering members of the security group.
 * This runs periodically in a thread through the {@link #run()} method.
 * 
 * @author Fabric WorldWide
 */
public class AwsSecurityGroupAwareDiscoveryService extends AbstractScheduledIntervalDiscoveryService {
    private static final Logger LOG = LoggerFactory.getLogger(AwsSecurityGroupAwareDiscoveryService.class.getName());
	private final UpdateableHostBasedCacheManagerPeerProvider updateableCacheManagerPeerProvider;
	private final String securityGroup;
	private ComputeServiceContext context;
	private AwsDiscoveryServiceConfig discoveryServiceConfig;

	public AwsSecurityGroupAwareDiscoveryService(
			UpdateableHostBasedCacheManagerPeerProvider updateableCacheManagerPeerProvider,
			ScheduledExecutorService discoveryServiceExecutor,
			AwsDiscoveryServiceConfig discoveryServiceConfig) {
		super(discoveryServiceExecutor, discoveryServiceConfig);
		this.updateableCacheManagerPeerProvider = updateableCacheManagerPeerProvider;
		this.discoveryServiceConfig = discoveryServiceConfig;
		// This is the security group to look for
		securityGroup = discoveryServiceConfig.getSecurityGroup();
	}
	
	@Override
	protected void preStartDiscoveryService() {
		// Get an EC2 connection using JClouds
		context = ContextBuilder.newBuilder("aws-ec2")
                .credentials(discoveryServiceConfig.getAccessKey(), discoveryServiceConfig.getSecretKey())
                .modules(ImmutableSet.of(new SLF4JLoggingModule(), new SshjSshClientModule()))
                .buildView(ComputeServiceContext.class);
	}

	@Override
	protected void postStopDiscoveryService() {
		context = null;
	}

	@Override
	public void run() {
		Set<CachePeerHost> cachePeerHosts = getHostsInSecurityGroup();
		updateableCacheManagerPeerProvider.setCachePeerHosts(cachePeerHosts);
	}

	/**
	 * Retrieves all of the {@link CachePeerHost} entries in the AWS security group
	 * @return
	 */
	Set<CachePeerHost> getHostsInSecurityGroup() {
		// Create a query to list all of the nodes in this security group
		Iterable<? extends NodeMetadata> nodesInSecurityGroup =
				filter(context.getComputeService().listNodesDetailsMatching(
						NodePredicates.all()), and(inGroup(securityGroup), and(NodePredicates.RUNNING)));
		Iterator<? extends NodeMetadata> nodesInSecurityGroupIterator = nodesInSecurityGroup.iterator();
		Set<CachePeerHost> cachePeerHosts = new HashSet<CachePeerHost>();

		while (nodesInSecurityGroupIterator.hasNext()) {
			NodeMetadata nodeMetadata = nodesInSecurityGroupIterator.next();
			
			// Pick the first public IP and add it to the list
			Iterator<String> publicIpIterator = nodeMetadata.getPublicAddresses().iterator();
			if (publicIpIterator.hasNext()) {
				cachePeerHosts.add(new CachePeerHost(publicIpIterator.next(), discoveryServiceConfig.getRmiListenerPort()));
			} else {
				LOG.warn("No available public IP address for instance '{}', skipping this instance",  nodeMetadata.getId());
			}
		}

		return cachePeerHosts;
	}

}

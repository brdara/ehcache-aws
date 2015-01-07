package com.xarixa.ehcache.aws.discovery;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.NodeMetadata;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.xarixa.ehcache.aws.AbstractAwsIntegrationTest;
import com.xarixa.ehcache.aws.CachePeerHost;
import com.xarixa.ehcache.aws.UpdateableHostBasedCacheManagerPeerProvider;

@RunWith(JMock.class)
public class AwsSecurityGroupAwareDiscoveryServiceIntegrationTest extends AbstractAwsIntegrationTest {
    private static final Integer RMI_LISTENER_PORT = 73487;

    private Mockery context = new Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    private AwsSecurityGroupAwareDiscoveryService awsSecurityGroupAwareDiscoveryService;

    
    @BeforeClass
	public static void setUpClass() {
    	createTestSecurityGroup();
	}

	@AfterClass
	public static void tearDownClass() {
		destroyTestSecurityGroup();
	}

	@Before
	public void setUpTest() {
		final UpdateableHostBasedCacheManagerPeerProvider peerProviderMock =
				context.mock(UpdateableHostBasedCacheManagerPeerProvider.class);
		final AwsDiscoveryServiceConfig discoveryServiceConfig = context.mock(AwsDiscoveryServiceConfig.class);
		final ScheduledExecutorService mockExecutorService = context.mock(ScheduledExecutorService.class);
		
		context.checking(new Expectations() {{
			atMost(1).of(discoveryServiceConfig).getSecurityGroupRefreshInterval();
			will(returnValue(5000));
			
			atMost(1).of(discoveryServiceConfig).getAccessKey();
			will(returnValue(getAwsAccessKey()));
			
			atMost(1).of(discoveryServiceConfig).getSecretKey();
			will(returnValue(getAwsSecretKey()));
			
			atMost(1).of(discoveryServiceConfig).getSecurityGroup();
			will(returnValue(getSecurityGroupId()));
			
			atLeast(1).of(discoveryServiceConfig).getRmiListenerPort();
			will(returnValue(RMI_LISTENER_PORT));

			atMost(1).of(mockExecutorService).scheduleWithFixedDelay(with(any(AwsSecurityGroupAwareDiscoveryService.class)),
					with(any(Integer.class)), with(any(Integer.class)), with(TimeUnit.MILLISECONDS));
		}});
		
		awsSecurityGroupAwareDiscoveryService =
				new AwsSecurityGroupAwareDiscoveryService(peerProviderMock, mockExecutorService, discoveryServiceConfig);
		awsSecurityGroupAwareDiscoveryService.startDiscoveryService();
	}

	/**
	 * This integration test creates instances in the security group and then runs the
	 * {@link AwsSecurityGroupAwareDiscoveryService#getHostsInSecurityGroup()} to retrieve the hosts and check that all
	 * host IP addresses have been retrieved.
	 * 
	 * @throws RunNodesException
	 */
	@Test
	public void testThatWhenNodesAreCreatedTheyAreQueriedForAndReturnThePublicIpAddresses() throws RunNodesException {
		Set<? extends NodeMetadata> nodesInGroup =
				getComputeService().createNodesInGroup(getSecurityGroupId(), 2);
		
		// Run the method to get the hosts in this group
		Set<CachePeerHost> hostsInSecurityGroup = awsSecurityGroupAwareDiscoveryService.getHostsInSecurityGroup();
		Assert.assertEquals(2, hostsInSecurityGroup.size());

		// Make sure that all of the public IP's exist from what was created to what was discovered
		for (NodeMetadata node : nodesInGroup) {
			Iterator<String> publicIpIterator = node.getPublicAddresses().iterator();
			Assert.assertTrue("No public IP addresses available for '" + node.getId() + "'", publicIpIterator.hasNext());
			String publicIp = publicIpIterator.next();
			CachePeerHost hostToMatch = new CachePeerHost(publicIp, RMI_LISTENER_PORT);
			Assert.assertTrue("Could not find created AWS node '" + hostToMatch +
					"' in my hosts list: " + hostsInSecurityGroup,
					hostsInSecurityGroup.contains(hostToMatch));
		}
	}

}

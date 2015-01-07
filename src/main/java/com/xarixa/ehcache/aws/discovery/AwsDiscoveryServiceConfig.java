package com.xarixa.ehcache.aws.discovery;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

public class AwsDiscoveryServiceConfig implements DiscoveryServiceConfig {
    /**
     * The RMI listener port for the cache on this host
     */
    private Integer rmiListenerPort;

    /**
     * The AWS security group to query for members
     */
	private String securityGroup;

	/**
     * AWS security group disovery process refresh time interval
     */
    private Integer securityGroupRefreshInterval;
    
    /**
     * The AWS access key
     */
    private String accessKey;

    /**
     * The AWS secret key
     */
    private String secretKey;



    public AwsDiscoveryServiceConfig() {
    }

	@Override
	public Integer getRmiListenerPort() {
		return rmiListenerPort;
	}

	public void setRmiListenerPort(Integer rmiListenerPort) {
		this.rmiListenerPort = rmiListenerPort;
	}

	public String getSecurityGroup() {
		return securityGroup;
	}

	public void setSecurityGroup(String securityGroup) {
		this.securityGroup = securityGroup;
	}

	public Integer getSecurityGroupRefreshInterval() {
		return securityGroupRefreshInterval;
	}

	public void setSecurityGroupRefreshInterval(Integer securityGroupRefreshInterval) {
		this.securityGroupRefreshInterval = securityGroupRefreshInterval;
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder()
			.append(securityGroup)
			.append(rmiListenerPort)
			.toHashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof AwsDiscoveryServiceConfig) {
			AwsDiscoveryServiceConfig other = (AwsDiscoveryServiceConfig)obj;
			
			return new EqualsBuilder()
				.append(securityGroup, other.securityGroup)
				.append(rmiListenerPort, other.rmiListenerPort)
				.isEquals();
		}
		
		return false;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this);
	}

	public String getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public String getAccessKey() {
		return accessKey;
	}

	public void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}

	@Override
	public void validate() throws IllegalArgumentException {
		if (StringUtils.isBlank(securityGroup)) {
			throw new IllegalArgumentException("Configured security group cannot be empty");
		}
		if (StringUtils.isBlank(accessKey)) {
			throw new IllegalArgumentException("Configured AWS access key cannot be empty");
		}
		if (StringUtils.isBlank(secretKey)) {
			throw new IllegalArgumentException("Configured AWS secret key cannot be empty");
		}
		if (rmiListenerPort == null || rmiListenerPort <= 0) {
			throw new IllegalArgumentException("Configured RMI listener port is invalid (was " + rmiListenerPort + ")");
		}
		if (rmiListenerPort == null || rmiListenerPort <= 0) {
			throw new IllegalArgumentException("Configured security group refresh interval is invalid (was " +
					securityGroupRefreshInterval + ")");
		}
	}

}

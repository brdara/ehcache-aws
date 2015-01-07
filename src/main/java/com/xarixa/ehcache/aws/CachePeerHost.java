package com.xarixa.ehcache.aws;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

public class CachePeerHost {
	private final String hostname;
	private final Integer port;

	public CachePeerHost(String hostname, Integer port) {
		this.hostname = hostname;
		this.port = port;
	}

	/**
	 * Hostname or IP address of the cache peer host
	 * @return
	 */
	public String getHostname() {
		return hostname;
	}

	/**
	 * Listener port of the host
	 * @return
	 */
	public Integer getPort() {
		return port;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		
		if (obj instanceof CachePeerHost) {
			CachePeerHost other = (CachePeerHost)obj;
			return new EqualsBuilder()
				.append(this.hostname, other.hostname)
				.append(this.port, other.port)
				.isEquals();
		}

		return false;
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder()
			.append(hostname)
			.append(port)
			.toHashCode();
	}
	
	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this);
	}

}

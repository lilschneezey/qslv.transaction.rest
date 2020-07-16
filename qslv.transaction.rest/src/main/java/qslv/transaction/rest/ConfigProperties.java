package qslv.transaction.rest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import qslv.util.EnableQuickSilver;

@Configuration
@ConfigurationProperties(prefix="qslv")
@EnableQuickSilver
public class ConfigProperties {
	private String aitid = "78234";
	private int port;


	public String getAitid() {
		return aitid;
	}

	public void setAitid(String aitid) {
		this.aitid = aitid;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}
	
}

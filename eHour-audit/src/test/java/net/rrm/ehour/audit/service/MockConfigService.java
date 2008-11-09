package net.rrm.ehour.audit.service;

import org.springframework.stereotype.Component;

import net.rrm.ehour.audit.NonAuditable;

import net.rrm.ehour.config.EhourConfig;
import net.rrm.ehour.config.EhourConfigStub;
import net.rrm.ehour.config.service.ConfigurationService;

@Component("configurationService")
@NonAuditable
public class MockConfigService implements ConfigurationService
{
	public EhourConfigStub getConfiguration()
	{
		EhourConfigStub config = new EhourConfigStub();

		return config;
	}

	/*
	 * (non-Javadoc)
	 * @see net.rrm.ehour.config.service.ConfigurationService#persistConfiguration(net.rrm.ehour.config.EhourConfig)
	 */
	public void persistConfiguration(EhourConfig config)
	{
		
	}

}

/**
 * Created on Mar 19, 2007
 * Created by Thies Edeling
 * Copyright (C) 2005, 2006 te-con, All Rights Reserved.
 *
 * This Software is copyright TE-CON 2007. This Software is not open source by definition. The source of the Software is available for educational purposes.
 * TE-CON holds all the ownership rights on the Software.
 * TE-CON freely grants the right to use the Software. Any reproduction or modification of this Software, whether for commercial use or open source,
 * is subject to obtaining the prior express authorization of TE-CON.
 * thies@te-con.nl
 * TE-CON
 * Legmeerstraat 4-2h, 1058ND, AMSTERDAM, The Netherlands
 *
 */

package net.rrm.ehour.config.service;

import net.rrm.ehour.audit.NonAuditable;
import net.rrm.ehour.config.EhourConfig;
import net.rrm.ehour.config.EhourConfigStub;

/**
 * Service for modifying the configuration
 **/
@NonAuditable
public interface ConfigurationService
{
	/**
	 * Get all configuration items
	 * @return
	 */
	public EhourConfigStub getConfiguration();
	
	/**
	 * Persist all configuration items
	 * @param config
	 */
	public void persistConfiguration(EhourConfig config);
}

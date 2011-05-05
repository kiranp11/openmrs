/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.api.impl;

import java.util.List;
import java.util.Vector;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.Provider;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.ProviderDAO;
import org.openmrs.util.OpenmrsConstants;

/**
 * Default implementation of the {@link ProviderService}. This class should not be used on its own.
 * The current OpenMRS implementation should be fetched from the Context.
 * 
 * @since 1.9
 */
public class ProviderServiceImpl extends BaseOpenmrsService implements ProviderService {
	
	private ProviderDAO dao;
	
	private final Log log = LogFactory.getLog(this.getClass());
	
	/**
	 * Sets the data access object for Concepts. The dao is used for saving and getting concepts
	 * to/from the database
	 * 
	 * @param dao The data access object to use
	 */
	public void setProviderDAO(ProviderDAO dao) {
		this.dao = dao;
	}
	
	/**
	 * @see org.openmrs.api.ProviderService#getAllProviders()
	 */
	@Override
	public List<Provider> getAllProviders() {
		return getAllProviders(true);
	}
	
	/**
	 * @see org.openmrs.api.ProviderService#getAllProviders(boolean)
	 */
	public List<Provider> getAllProviders(boolean includeRetired) {
		return dao.getAllProviders(includeRetired);
	}
	
	/**
	 * @see org.openmrs.api.ProviderService#retireProvider(org.openmrs.Provider, java.lang.String)
	 */
	public void retireProvider(Provider provider, String reason) {
		dao.saveProvider(provider);
	}
	
	/**
	 * @see org.openmrs.api.ProviderService#unretireProvider(org.openmrs.Provider)
	 */
	public Provider unretireProvider(Provider provider) {
		return dao.saveProvider(provider);
	}
	
	/**
	 * @see org.openmrs.api.ProviderService#purgeProvider(org.openmrs.Provider)
	 */
	public void purgeProvider(Provider provider) {
		dao.deleteProvider(provider);
	}
	
	/**
	 * @see org.openmrs.api.ProviderService#getProvider(java.lang.Integer)
	 */
	@Override
	public Provider getProvider(Integer providerId) {
		return dao.getProvider(providerId);
	}
	
	/**
	 * @throws Exception
	 * @see org.openmrs.api.ProviderService#saveProvider(org.openmrs.Provider)
	 */
	@Override
	public Provider saveProvider(Provider provider) throws Exception {
		if (provider.isValid())
			return dao.saveProvider(provider);
		else
			throw new Exception("Provider Name or Person Required");
		
	}
	
	/**
	 * @see org.openmrs.api.ProviderService#getProviderbyUuid(java.lang.String)
	 */
	@Override
	public Provider getProviderbyUuid(String uuid) {
		return dao.getProviderByUuid(uuid);
	}
	
	/**
	 * @see org.openmrs.api.ProviderService#getCountOfProviders(java.lang.String)
	 */
	@Override
	public Integer getCountOfProviders(String query) {
		
		List<Provider> provider = new Vector<Provider>();
		if (StringUtils.isBlank(query) || query.length() < getMinSearchCharacters())
			return 0;
		
		// if there is a number in the query string
		if (query.matches(".*\\d+.*")) {
			log.debug("[Identifier search] Query: " + query);
			return dao.getCountOfProviders(null, query);
		} else {
			// there is no number in the string, search on name
			return dao.getCountOfProviders(query, null);
		}
	}
	
	/**
	 * @see org.openmrs.api.ProviderService#getProviders(java.lang.String, java.lang.Integer,
	 *      java.lang.Integer)
	 */
	@Override
	public List<Provider> getProviders(String query, Integer start, Integer length) {
		
		List<Provider> provider = new Vector<Provider>();
		if (StringUtils.isBlank(query) || query.length() < getMinSearchCharacters())
			return provider;
		
		// if there is a number in the query string
		if (query.matches(".*\\d+.*")) {
			log.debug("[Identifier search] Query: " + query);
			return dao.getProviders(null, query, start, length);
		} else {
			// there is no number in the string, search on name
			return dao.getProviders(query, null, start, length);
		}
		
	}
	
	/**
	 * @see org.openmrs.api.ProviderService#getProvider(java.lang.String)
	 */
	@Override
	public List<Provider> getProvider(String query) {
		return getProviders(query, 0, null);
	}
	
	/**
	 * Method returns the minimum number of search characters
	 * 
	 * @return the value of min search characters
	 */
	private int getMinSearchCharacters() {
		int minSearchCharacters = OpenmrsConstants.GLOBAL_PROPERTY_DEFAULT_MIN_SEARCH_CHARACTERS;
		String minSearchCharactersStr = Context.getAdministrationService().getGlobalProperty(
		    OpenmrsConstants.GLOBAL_PROPERTY_MIN_SEARCH_CHARACTERS);
		
		try {
			minSearchCharacters = Integer.valueOf(minSearchCharactersStr);
		}
		catch (NumberFormatException e) {
			//do nothing
		}
		
		return minSearchCharacters;
	}
}

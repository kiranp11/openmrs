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
package org.openmrs.attribute;

import org.openmrs.BaseOpenmrsMetadata;

/**
 * Abstract base implementation of {@link AttributeType}. Actual implementations (e.g. VisitAttributeType,
 * ProviderAttributeType) should be able to extend this and provide very little of their own code.
 * @since 1.9
 */
public abstract class BaseAttributeType<OwningType extends Customizable<?>> extends BaseOpenmrsMetadata implements AttributeType<OwningType> {
	
	private Integer minOccurs = 0;
	
	private Integer maxOccurs = null;
	
	private String datatype;
	
	private String handlerConfig;
	
	/**
	 * @see org.openmrs.attribute.AttributeType#getMinOccurs()
	 */
	public Integer getMinOccurs() {
		return minOccurs;
	}
	
	/**
	 * @param minOccurs the minOccurs to set
	 */
	public void setMinOccurs(Integer minOccurs) {
		this.minOccurs = minOccurs;
	}
	
	/**
	 * @see org.openmrs.attribute.AttributeType#getMaxOccurs()
	 */
	public Integer getMaxOccurs() {
		return maxOccurs;
	}
	
	/**
	 * @param maxOccurs the maxOccurs to set
	 */
	public void setMaxOccurs(Integer maxOccurs) {
		this.maxOccurs = maxOccurs;
	}
	
	/**
	 * @see org.openmrs.attribute.AttributeType#getHandlerConfig()
	 */
	public String getHandlerConfig() {
		return handlerConfig;
	}
	
	/**
	 * @param handlerConfig the handlerConfig to set
	 */
	public void setHandlerConfig(String handlerConfig) {
		this.handlerConfig = handlerConfig;
	}
	
	/**
	 * @see org.openmrs.attribute.AttributeType#getDatatype()
	 */
	public String getDatatype() {
		return datatype;
	}
	
	/**
	 * @param datatype the datatype to set
	 */
	public void setDatatype(String datatype) {
		this.datatype = datatype;
	}
	
	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof AttributeType) {
			AttributeType at = (AttributeType) obj;
			if (at != null)
				return getUuid().equals(at.getUuid());
		}
		return false;
		
	}
	
}

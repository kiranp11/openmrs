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
package org.openmrs.api;

import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.openmrs.Cohort;
import org.openmrs.DrugOrder;
import org.openmrs.api.context.Context;
import org.openmrs.test.BaseContextSensitiveTest;

/**
 *
 */
public class PatientSetServiceTest extends BaseContextSensitiveTest {

	protected static final String CREATE_PATIENT_XML = "org/openmrs/api/include/PatientServiceTest-createPatient.xml";
	
	@Test
	public void shouldGetDrugOrders() throws Exception {
		PatientSetService service = Context.getPatientSetService();
		Cohort nobody = new Cohort();
		Map<Integer, List<DrugOrder>> results = service.getDrugOrders(nobody, null);
		assertNotNull(results);
	}
	
}
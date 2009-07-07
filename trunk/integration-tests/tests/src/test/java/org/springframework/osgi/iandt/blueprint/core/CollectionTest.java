/*
 * Copyright 2006-2009 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.osgi.iandt.blueprint.core;

import org.springframework.osgi.iandt.blueprint.BaseBlueprintIntegrationTest;

/**
 * @author Costin Leau
 */
public class CollectionTest extends BaseBlueprintIntegrationTest {

	@Override
	protected String[] getConfigLocations() {
		return new String[] { "org/springframework/osgi/iandt/blueprint/core/blueprint-collections.xml" };
	}

	public void testExportedServiceProperties() throws Exception {
		String str = InitBean.class.getName();
		String bcn = Listener.class.getName();
		assertNotSame(str, bcn);
		System.out.println(applicationContext.getBean("bean"));
	}
}

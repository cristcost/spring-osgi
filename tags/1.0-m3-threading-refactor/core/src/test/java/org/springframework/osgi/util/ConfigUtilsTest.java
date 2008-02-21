/*
 * Copyright 2002-2007 the original author or authors.
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
package org.springframework.osgi.util;

import java.util.Dictionary;
import java.util.Hashtable;

import junit.framework.TestCase;

import org.springframework.util.ObjectUtils;

/**
 * @author Costin Leau
 * 
 */
public class ConfigUtilsTest extends TestCase {

	private Dictionary headers;

	private String DEFAULT_LOCATION = ConfigUtils.SPRING_CONTEXT_FILES;

	/*
	 * (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		headers = new Hashtable();
	}

	/*
	 * (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		headers = null;
	}

	public void testGetCompletelyEmptySpringContextHeader() throws Exception {
		String[] locations = ConfigUtils.getConfigLocations(headers);
		assertEquals(1, locations.length);
		assertEquals(DEFAULT_LOCATION, locations[0]);

	}

	public void testGetEmptyConfigLocations() throws Exception {
		String entry = ";early-init-importers=true";
		headers.put(ConfigUtils.SPRING_CONTEXT_HEADER, entry);
		String[] locations = ConfigUtils.getConfigLocations(headers);
		assertEquals(1, locations.length);
		assertEquals(DEFAULT_LOCATION, locations[0]);
	}

	public void testGetNotExistingConfigLocations() throws Exception {
		String location = "bundle:/META-INF/non-existing.xml";
		String entry = location + "; early-init-importers=true";

		headers.put(ConfigUtils.SPRING_CONTEXT_HEADER, entry);
		String[] locations = ConfigUtils.getConfigLocations(headers);
		assertEquals(1, locations.length);
		assertEquals(location, locations[0]);

	}

	public void testGetWildcardConfigLocs() throws Exception {
		String location = "classpath:/META-INF/spring/*.xml";
		String entry = location + "; early-init-importers=true";
		headers.put(ConfigUtils.SPRING_CONTEXT_HEADER, entry);
		String[] locations = ConfigUtils.getConfigLocations(headers);
		assertEquals(1, locations.length);
		assertEquals(location, locations[0]);
	}

	public void testMultipleConfigLocs() throws Exception {
		String location1 = "classpath:/META-INF/spring/*.xml";
		String location2 = "bundle:/META-INF/non-existing.xml";

		String entry = location1 + "," + location2 + "; early-init-importers=true";
		headers.put(ConfigUtils.SPRING_CONTEXT_HEADER, entry);
		String[] locations = ConfigUtils.getConfigLocations(headers);
		assertEquals(2, locations.length);
		assertEquals(location1, locations[1]);
		assertEquals(location2, locations[0]);
	}
}
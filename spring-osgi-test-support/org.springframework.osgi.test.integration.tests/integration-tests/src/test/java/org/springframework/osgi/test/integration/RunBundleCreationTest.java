/*
 * Copyright 2006 the original author or authors.
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
package org.springframework.osgi.test.integration;

import junit.framework.TestCase;
import junit.framework.TestResult;

/**
 * Start executing the {@link RunBundleCreationTest} (which is an integration
 * test) and tests failures and errors.
 * 
 * @author Hal Hildebrand
 * @author Costin Leau
 */
public class RunBundleCreationTest extends TestCase {

	private TestCase test;

	private TestResult result;

	protected void setUp() throws Exception {
		test = new BundleCreationTests();
		result = new TestResult();
	}

	public void testAssertionPass() {
		executeTest("testAssertionPass");
		assertEquals(0, result.errorCount());
		assertEquals(0, result.failureCount());
	}

	public void testAssertionFailure() {
		executeTest("testAssertionFailure");
		assertEquals(0, result.errorCount());
		assertEquals(1, result.failureCount());

	}

	public void testFailure() {
		executeTest("testFailure");
		assertEquals(0, result.errorCount());
		assertEquals(1, result.failureCount());

	}

	public void testException() {
		executeTest("testException");
		assertEquals(1, result.errorCount());
		assertEquals(0, result.failureCount());

	}

	public void testError() {
		executeTest("testError");
		assertEquals(1, result.errorCount());
		assertEquals(0, result.failureCount());

	}

	private void executeTest(String testMethod) {
		test.setName(testMethod);
		test.run(result);
		assertEquals(1, result.runCount());
	}
}

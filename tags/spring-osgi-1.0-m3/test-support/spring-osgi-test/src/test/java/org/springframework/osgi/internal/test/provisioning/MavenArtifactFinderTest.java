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
package org.springframework.osgi.internal.test.provisioning;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import org.springframework.osgi.internal.test.provisioning.MavenPackagedArtifactFinder;

public class MavenArtifactFinderTest extends TestCase {

	public void testFindMyArtifact()throws IOException {
		MavenPackagedArtifactFinder finder = 
			new MavenPackagedArtifactFinder("test-artifact","1.0-SNAPSHOT", "jar");
		File found = finder.findPackagedArtifact(new File("target/test-classes/org/springframework/osgi/test"));
		assertNotNull(found);
		assertTrue(found.exists());
	}

	public void testFindChildArtifact()throws IOException {
		MavenPackagedArtifactFinder finder = 
			new MavenPackagedArtifactFinder("test-child-artifact","1.0-SNAPSHOT", "jar");
		File found = finder.findPackagedArtifact(new File("target/test-classes/org/springframework/osgi/test"));
		assertNotNull(found);
		assertTrue(found.exists());
	}

	public void testFindParentArtifact()throws IOException {
		MavenPackagedArtifactFinder finder = 
			new MavenPackagedArtifactFinder("test-artifact","1.0-SNAPSHOT", "jar");
		File found = finder.findPackagedArtifact(new File("target/test-classes/org/springframework/osgi/test/child"));
		assertNotNull(found);
		assertTrue(found.exists());
	}

}

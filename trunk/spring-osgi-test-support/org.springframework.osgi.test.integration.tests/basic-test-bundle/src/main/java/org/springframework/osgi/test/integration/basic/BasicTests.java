/*
 * Copyright 2002-2006 the original author or authors.
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
package org.springframework.osgi.test.integration.basic;

import java.io.File;

import org.springframework.osgi.test.AbstractOsgiTests;

/**
 * @author Costin Leau
 */
public class BasicTests extends AbstractOsgiTests {

    /*
      * (non-Javadoc)
      *
      * @see org.springframework.osgi.test.OsgiTest#getBundles()
      */
    protected String[] getBundlesLocations() {
        String jar
                = "org/springframework/osgi-junit-framework-testing-bundle/2.1/osgi-junit-framework-testing-bundle-2.1.jar";
        File repo = new File(new File(System.getProperty("user.home")), ".m2/repository");
        return new String[]{"file:" + new File(repo, jar).getAbsolutePath()};

    }


    public void testAssertionPass() {
        System.out.println("*** test is running ***");
        assertTrue(true);
    }


    public void testAssertionFailure() {
        System.out.println("*** test is running ***");
        assertTrue(false);
    }


    public void testFailure() {
        fail("this is a failure");
    }


    public void testException() {
        throw new RuntimeException("this is an exception");
    }

}

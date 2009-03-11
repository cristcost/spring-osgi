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

package org.springframework.osgi.blueprint.config.internal.temporary;

import org.springframework.beans.factory.support.ManagedSet;

/**
 * @author Costin Leau
 */
public class TempManagedSet extends ManagedSet {

	private final String defaultTypeClassName;


	public TempManagedSet(int size, String defaultTypeClassName) {
		super(size);
		this.defaultTypeClassName = defaultTypeClassName;
	}

	public String getDefaultTypeClassName() {
		return defaultTypeClassName;
	}
}

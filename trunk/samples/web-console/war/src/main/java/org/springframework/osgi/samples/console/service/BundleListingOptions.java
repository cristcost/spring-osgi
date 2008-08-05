/*
 * Copyright 2006-2008 the original author or authors.
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

package org.springframework.osgi.samples.console.service;

import java.util.EnumMap;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.springframework.osgi.util.OsgiStringUtils;

/**
 * Enumeration describing the bundle listing options.
 * 
 * @author Costin Leau
 */
public enum BundleListingOptions {
	ID {

		@Override
		public String display(Bundle bundle) {
			return (bundle == null ? "null" : String.valueOf(bundle.getBundleId()));
		}
	},
	NAME {

		@Override
		public String display(Bundle bundle) {
			return OsgiStringUtils.nullSafeName(bundle);
		}

	},
	SYMBOLIC_NAME {

		@Override
		public String display(Bundle bundle) {
			return OsgiStringUtils.nullSafeSymbolicName(bundle);
		}

	};

	// Initialize the phase transition map
	private static final Map<BundleListingOptions, String> toStringMap = new EnumMap<BundleListingOptions, String>(
		BundleListingOptions.class);

	static {
		// create to String map
		for (BundleListingOptions option : BundleListingOptions.values())
			toStringMap.put(option, option.toString().toLowerCase().replace('_', ' '));
	}


	/**
	 * Returns a map of enum<->toString association.
	 * 
	 * @return
	 */
	public static Map<BundleListingOptions, String> toStringMap() {
		return toStringMap;
	}

	public abstract String display(Bundle bundle);
}

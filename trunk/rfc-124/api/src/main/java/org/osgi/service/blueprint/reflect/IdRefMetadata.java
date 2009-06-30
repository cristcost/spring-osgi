/*
 * Copyright (c) OSGi Alliance (2008, 2009). All Rights Reserved.
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
package org.osgi.service.blueprint.reflect;

/**
 * A value which represents the id of another manager in the Blueprint Container.
 * The id itself will be injected, not the manager that the id refers to.
 * 
 */
public interface IdRefMetadata extends NonNullMetadata {
	/**
	 * The id of the referenced manager. Defined in the
	 * <code>component-id</code> attribute.
	 * 
	 * @return The referred manager id.
	 */
	String getComponentId();
}

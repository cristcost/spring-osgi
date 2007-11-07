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
package org.springframework.osgi.internal.service.interceptor;

import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.springframework.util.Assert;

/**
 * Simple {@link ServiceReference} implementation that delegates to an
 * underlying implementation which can be swapped at runtime.
 * 
 * <strong>Note:</strong> this class is thread-safe.
 * 
 * @author Costin Leau
 * 
 */
class ServiceReferenceDelegate implements ServiceReference {

	private ServiceReference delegate;

	synchronized ServiceReference swapDelegates(ServiceReference newDelegate) {
		Assert.notNull(newDelegate);
		ServiceReference old = delegate;
		delegate = newDelegate;

		return old;
	}

	public synchronized Bundle getBundle() {
		return delegate.getBundle();
	}

	public synchronized Object getProperty(String key) {
		return delegate.getProperty(key);
	}

	public synchronized String[] getPropertyKeys() {
		return delegate.getPropertyKeys();
	}

	public synchronized Bundle[] getUsingBundles() {
		return delegate.getUsingBundles();
	}

	public synchronized boolean isAssignableTo(Bundle bundle, String className) {
		return delegate.isAssignableTo(bundle, className);
	}
}

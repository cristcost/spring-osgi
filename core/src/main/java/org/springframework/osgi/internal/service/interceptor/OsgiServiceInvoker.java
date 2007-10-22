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
package org.springframework.osgi.internal.service.interceptor;

import java.lang.reflect.InvocationTargetException;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.ServiceReference;
import org.springframework.util.Assert;

/**
 * Base around interceptor for OSGi service invokers.
 * 
 * @author Costin Leau
 * 
 */
public abstract class OsgiServiceInvoker implements MethodInterceptor {

	protected transient final Log log = LogFactory.getLog(getClass());

	/**
	 * Actual invocation - the class is being executed on a different object
	 * then the one exposed in the invocation object.
	 * 
	 * @param service
	 * @param invocation
	 * @return
	 * @throws Throwable
	 */
	protected Object doInvoke(Object service, MethodInvocation invocation) throws Throwable {
		Assert.notNull(service, "service should not be null!");
		try {
			return invocation.getMethod().invoke(service, invocation.getArguments());
		}
		catch (InvocationTargetException ex) {
			throw ex.getTargetException();
		}
	}

	public final Object invoke(MethodInvocation invocation) throws Throwable {
		return doInvoke(getTarget(), invocation);
	}

	/**
	 * Determine the target object to execute the invocation upon.
	 * 
	 * @return
	 * @throws Throwable
	 */
	protected abstract Object getTarget();

	/**
	 * Convenience method exposing the target (OSGi service) reference so that
	 * subinterceptors can access it. By default, returns null.
	 * 
	 * @return
	 */
	protected ServiceReference getServiceReference() {
		return null;
	}

}

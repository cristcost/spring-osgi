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

package org.springframework.osgi.compendium.internal.cm;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.service.cm.ManagedService;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.osgi.util.OsgiBundleUtils;
import org.springframework.osgi.util.OsgiServiceUtils;
import org.springframework.osgi.util.OsgiStringUtils;
import org.springframework.osgi.util.internal.MapBasedDictionary;
import org.springframework.util.StringUtils;

/**
 * Utility class for the Configuration Admin package.
 * 
 * @author Costin Leau
 */
public abstract class CMUtils {

	/**
	 * Injects the properties from the given Map to the given object. Additionally, a bean factory can be passed in for
	 * copying property editors inside the injector.
	 * 
	 * @param instance bean instance to configure
	 * @param properties
	 * @param beanFactory
	 */
	public static void applyMapOntoInstance(Object instance, Map<String, ?> properties, AbstractBeanFactory beanFactory) {
		if (properties != null && !properties.isEmpty()) {
			BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(instance);
			// configure bean wrapper (using method from Spring 2.5.6)
			if (beanFactory != null) {
				beanFactory.copyRegisteredEditorsTo(beanWrapper);
			}
			for (Iterator<?> iterator = properties.entrySet().iterator(); iterator.hasNext();) {
				Map.Entry<String, ?> entry = (Map.Entry<String, ?>) iterator.next();
				String propertyName = entry.getKey();
				if (beanWrapper.isWritableProperty(propertyName)) {
					beanWrapper.setPropertyValue(propertyName, entry.getValue());
				}
			}
		}
	}

	public static void bulkUpdate(UpdateCallback callback, Collection<?> instances, Map<?, ?> properties) {
		for (Iterator<?> iterator = instances.iterator(); iterator.hasNext();) {
			Object instance = iterator.next();
			callback.update(instance, properties);
		}
	}

	public static UpdateCallback createCallback(boolean autowireOnUpdate, String methodName, BeanFactory beanFactory) {
		UpdateCallback beanManaged = null, containerManaged = null;
		if (autowireOnUpdate) {
			containerManaged = new ContainerManagedUpdate(beanFactory);
		}
		if (StringUtils.hasText(methodName)) {
			beanManaged = new BeanManagedUpdate(methodName);
		}

		// if both strategies are present, return a chain
		if (containerManaged != null && beanManaged != null)
			return new ChainedManagedUpdate(new UpdateCallback[] { containerManaged, beanManaged });

		// otherwise return the non-null one
		return (containerManaged != null ? containerManaged : beanManaged);
	}

	/**
	 * Returns a map containing the Configuration Admin entry with given pid. Waits until a non-null (initialized)
	 * object is returned if initTimeout is bigger then 0.
	 * 
	 * @param bundleContext
	 * @param pid
	 * @param initTimeout
	 * @return
	 * @throws IOException
	 */
	public static Map getConfiguration(BundleContext bundleContext, final String pid, long initTimeout)
			throws IOException {
		ServiceReference ref = bundleContext.getServiceReference(ConfigurationAdmin.class.getName());
		if (ref != null) {
			ConfigurationAdmin cm = (ConfigurationAdmin) bundleContext.getService(ref);
			if (cm != null) {
				Dictionary dict = cm.getConfiguration(pid).getProperties();
				// if there are properties or no timeout, return as is
				if (dict != null || initTimeout == 0) {
					return new MapBasedDictionary(dict);
				}
				// no valid props, register a listener and start waiting
				final Object monitor = new Object();
				Properties props = new Properties();
				props.put(Constants.SERVICE_PID, pid);
				
				ServiceRegistration reg =
						bundleContext.registerService(ConfigurationListener.class.getName(),
								new ConfigurationListener() {
									public void configurationEvent(ConfigurationEvent event) {
										if (ConfigurationEvent.CM_UPDATED == event.getType()
												&& pid.equals(event.getPid())) {
											synchronized (monitor) {
												monitor.notify();
											}
										}
									}
								}, props);

				try {
					// try to get the configuration one more time (in case the update was fired before the service was
					// registered)
					dict = cm.getConfiguration(pid).getProperties();
					if (dict != null) {
						return new MapBasedDictionary(dict);
					}

					// start waiting
					synchronized (monitor) {
						try {
							monitor.wait(initTimeout);
						} catch (InterruptedException ie) {
							// consider the timeout has passed
						}
					}

					// return whatever is available (either we timed out or an update occured)
					return new MapBasedDictionary(cm.getConfiguration(pid).getProperties());

				} finally {
					OsgiServiceUtils.unregisterService(reg);
				}
			}
		}
		return Collections.EMPTY_MAP;
	}

	public static ServiceRegistration registerManagedService(BundleContext bundleContext, ManagedService listener,
			String pid) {

		Properties props = new Properties();
		props.put(Constants.SERVICE_PID, pid);
		Bundle bundle = bundleContext.getBundle();
		props.put(Constants.BUNDLE_SYMBOLICNAME, OsgiStringUtils.nullSafeSymbolicName(bundle));
		props.put(Constants.BUNDLE_VERSION, OsgiBundleUtils.getBundleVersion(bundle));

		return bundleContext.registerService(ManagedService.class.getName(), listener, props);
	}
}
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

package org.springframework.osgi.extender.internal.support;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.osgi.context.ConfigurableOsgiBundleApplicationContext;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;
import org.springframework.util.ObjectUtils;

/**
 * Configuration class for the extender. Takes care of locating the extender
 * specific configurations and merging the results with the defaults.
 * 
 * @author Costin Leau
 * 
 */
public class ExtenderConfiguration implements DisposableBean {

	/** logger */
	private static final Log log = LogFactory.getLog(ExtenderConfiguration.class);

	private static final String TASK_EXECUTOR_NAME = "taskExecutor";

	private static final String PROPERTIES_NAME = "extenderProperties";

	private static final String SHUTDOWN_WAIT_KEY = "shutdown.wait.time";

	private ConfigurableOsgiBundleApplicationContext extenderConfiguration;

	private TaskExecutor taskExecutor;

	private boolean isTaskExecutorManagedInternally = false;

	private long shutdownWaitTime;

	private ApplicationEventMulticaster eventMulticaster;

	private boolean forceThreadShutdown;


	/**
	 * Constructs a new <code>ExtenderConfiguration</code> instance. Locates
	 * the extender configuration, creates an application context which will
	 * returned the extender items.
	 * 
	 * @param bundleContext extender OSGi bundle context
	 */
	public ExtenderConfiguration(BundleContext bundleContext) {
		Bundle bundle = bundleContext.getBundle();
		Properties properties = new Properties(createDefaultProperties());

		Enumeration enm = bundle.findEntries("META-INF/spring", "*.xml", false);
		if (enm == null) {
			log.info("No custom configuration detected; using defaults");

			taskExecutor = createDefaultTaskExecutor();
			eventMulticaster = new SimpleApplicationEventMulticaster();
		}
		else {
			String[] configs = copyEnumerationToList(enm);
			log.info("Detected custom configurations " + ObjectUtils.nullSafeToString(configs));
			// create OSGi specific XML context
			ConfigurableOsgiBundleApplicationContext context = new OsgiBundleXmlApplicationContext(configs);
			context.setBundleContext(bundleContext);
			context.refresh();

			taskExecutor = context.containsBean(TASK_EXECUTOR_NAME) ? (TaskExecutor) context.getBean(
				TASK_EXECUTOR_NAME, TaskExecutor.class) : createDefaultTaskExecutor();

			eventMulticaster = (ApplicationEventMulticaster) context.getBean(AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME);

			// extender properties using the defaults as backup
			if (context.containsBean(PROPERTIES_NAME)) {
				Properties customProperties = (Properties) context.getBean(PROPERTIES_NAME, Properties.class);
				Enumeration propertyKey = customProperties.propertyNames();
				while (propertyKey.hasMoreElements()) {
					String property = (String) propertyKey.nextElement();
					properties.setProperty(property, customProperties.getProperty(property));
				}
			}
		}

		shutdownWaitTime = getShutdownWaitTime(properties);
	}

	/**
	 * {@inheritDoc}
	 * 
	 * Cleanup the configuration items.
	 */
	public void destroy() {

		if (extenderConfiguration != null) {
			extenderConfiguration.close();
			extenderConfiguration = null;
		}

		eventMulticaster.removeAllListeners();
		eventMulticaster = null;

		// postpone the task executor shutdown
		if (forceThreadShutdown && isTaskExecutorManagedInternally) {
			log.warn("Forcing the (internally created) taskExecutor to stop");
			ThreadGroup th = ((SimpleAsyncTaskExecutor) taskExecutor).getThreadGroup();
			if (th.isDestroyed()) {
				// first ask the threads nicely to stop
				th.interrupt();
				th.stop();
				th.destroy();
			}
		}
		taskExecutor = null;
	}

	/**
	 * Copies the URLs returned by the given enumeration and returns them as an
	 * array of Strings for consumption by the application context.
	 * 
	 * @param enm
	 * @return
	 */
	private String[] copyEnumerationToList(Enumeration enm) {
		List urls = new ArrayList(4);
		while (enm.hasMoreElements()) {
			URL configURL = (URL) enm.nextElement();
			String configURLAsString = configURL.toExternalForm();
			try {
				urls.add(URLDecoder.decode(configURLAsString, "UTF8"));
			}
			catch (UnsupportedEncodingException uee) {
				log.warn("UTF8 encoding not supported, using the platform default");
				urls.add(URLDecoder.decode(configURLAsString));
			}
		}

		return (String[]) urls.toArray(new String[urls.size()]);
	}

	private Properties createDefaultProperties() {
		Properties properties = new Properties();
		properties.setProperty(SHUTDOWN_WAIT_KEY, "" + 10 * 1000);

		return properties;
	}

	private TaskExecutor createDefaultTaskExecutor() {
		// create thread-pool for starting contexts
		ThreadGroup threadGroup = new ThreadGroup("spring-osgi-extender[" + ObjectUtils.getIdentityHexString(this)
				+ "]-threads");
		threadGroup.setDaemon(false);

		SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor();
		taskExecutor.setThreadGroup(threadGroup);
		taskExecutor.setThreadNamePrefix("SpringOsgiExtenderThread-");

		isTaskExecutorManagedInternally = true;

		return taskExecutor;
	}

	private long getShutdownWaitTime(Properties properties) {
		return Long.parseLong(properties.getProperty(SHUTDOWN_WAIT_KEY));
	}

	/**
	 * Returns the taskExecutor.
	 * 
	 * @return Returns the taskExecutor
	 */
	public TaskExecutor getTaskExecutor() {
		return taskExecutor;
	}

	/**
	 * Returns the shutdownWaitTime.
	 * 
	 * @return Returns the shutdownWaitTime
	 */
	public long getShutdownWaitTime() {
		return shutdownWaitTime;
	}

	/**
	 * Returns the eventMulticaster.
	 * 
	 * @return Returns the eventMulticaster
	 */
	public ApplicationEventMulticaster getEventMulticaster() {
		return eventMulticaster;
	}

	/**
	 * Sets the flag to force the taskExtender to close up in case of runaway
	 * threads - this applies *only* if the taskExecutor has been created
	 * internally.
	 * 
	 * <p/> The flag will cause a best attempt to shutdown the threads.
	 * 
	 * @param forceThreadShutdown The forceThreadShutdown to set.
	 */
	public void setForceThreadShutdown(boolean forceThreadShutdown) {
		this.forceThreadShutdown = forceThreadShutdown;
	}
}

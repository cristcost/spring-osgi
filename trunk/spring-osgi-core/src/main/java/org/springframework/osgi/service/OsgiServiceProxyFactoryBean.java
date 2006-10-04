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
 *
 * Created on 23-Jan-2006 by Adrian Colyer
 */
package org.springframework.osgi.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.HotSwappableTargetSource;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.osgi.context.BundleContextAware;
import org.springframework.osgi.context.support.LocalBundleContext;
import org.springframework.util.StringUtils;

/**
 * Factory bean for OSGi services. Returns a proxy implementing the service
 * interface. This allows Spring to manage service lifecycle events
 * (such as the bundle providing the service being stopped and restarted)
 * and transparently rebind to a new service instance if one is available.
 *
 * @author Adrian Colyer
 * @since 2.0
 */
public class OsgiServiceProxyFactoryBean implements FactoryBean,
		InitializingBean, DisposableBean, BundleContextAware, ApplicationContextAware
{

	public static final long DEFAULT_MILLIS_BETWEEN_RETRIES = 1000;
	public static final int DEFAULT_MAX_RETRIES = 3;
	public static final String FILTER_ATTRIBUTE = "filter";
	public static final String INTERFACE_ATTRIBUTE = "interface";

	/**
	 * Logger, available to subclasses.
	 */
	protected final Log logger = LogFactory.getLog(getClass());


	private BundleContext bundleContext;
	private ServiceReference serviceReference;
	private boolean retryOnUnregisteredService = true;
	private int retryTimes = DEFAULT_MAX_RETRIES;
	private boolean broadcast = false;
	private String resultSummarizer;
	private String cardinality;

	private PropertyValues properties;

	private List listeners = new ArrayList();

	private long retryDelayMs = DEFAULT_MILLIS_BETWEEN_RETRIES;

	// not required to be an interface, but usually should be...
	private Class serviceType;

	// filter used to narrow service matches, may be null
	private String filter;

	// if looking for a bean published as a service, this is the name we're after
	private String beanName;

	// reference to our app context (we need the classloader for proxying...)
	private ApplicationContext applicationContext;
	private boolean pushBundleAsContextClassLoader;

	/* (non-Javadoc)
	 * @see org.springframework.beans.factory.FactoryBean#getObject()
	 */
	public Object getObject() throws Exception {
		// try to find the service
		String lookupFilter = getFilterStringForServiceLookup();
		// REVIEW andyp -- the bundle hosting this service may not have come up and hence the service
		// may not even exist, so we block based on the service availability attributes
		int numAttempts = 0;
		do {
			try {
				this.serviceReference = OsgiServiceUtils.getService(this.bundleContext, getInterface(), lookupFilter);
				if (logger.isDebugEnabled()) {
					logger.debug("Resolved service reference: [" + this.serviceReference + "] after " + (numAttempts + 1) + " attempts");
				}
			} catch (NoSuchServiceException nsse) {
				this.serviceReference = null;
				if (numAttempts++ < this.retryTimes && retryOnUnregisteredService) {
					try {
						Thread.sleep(this.retryDelayMs);
					} catch (InterruptedException ie) {
						return null;  // Never silently catch an InterruptedException!!!
					}
				} else {
					throw nsse;
				}
			}
		} while (this.serviceReference == null && numAttempts <= this.retryTimes && retryOnUnregisteredService);
		Object target = this.bundleContext.getService(this.serviceReference);
		// Apply any service properties to the underlying bean
		applyServiceProperties(target);
		return getServiceProxyFor(target, lookupFilter);
	}

	private void applyServiceProperties(Object target) {
		if (properties != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Setting properties [" + properties + "] on [" + target +"]");
			}
			BeanWrapper bw = new BeanWrapperImpl(target);
			bw.setPropertyValues(properties);
		}
		// REVIEW andyp -- Apply other initialization callbacks
		applicationContext.getAutowireCapableBeanFactory().initializeBean(target, beanName);
	}

	/* (non-Javadoc)
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 */
	public Class getObjectType() {
		return getInterface();
	}

	/* (non-Javadoc)
	 * @see org.springframework.beans.factory.FactoryBean#isSingleton()
	 */
	public boolean isSingleton() {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
	 */
	public void afterPropertiesSet() throws IllegalArgumentException {
		if (this.bundleContext == null) {
			throw new IllegalArgumentException("Required bundleContext property was not set");
		}
		if (getInterface() == null) {
			throw new IllegalArgumentException("Required serviceType property was not set");
		}
		if (getTarget() != null) {
			// this call forces parsing of the filter string to generate an exception right
			// now if it is not well-formed
			try {
				FrameworkUtil.createFilter(getFilterStringForServiceLookup());
			}
			catch (InvalidSyntaxException ex) {
				throw new IllegalArgumentException(
						"Filter string '" + getTarget() +
								"' set on OsgiServiceProxyFactoryBean has invalid syntax: " +
								ex.getMessage(), ex);
			}
		}
		if (!listeners.isEmpty()) {
			for (Iterator i = listeners.iterator(); i.hasNext();) {
				Object listener = i.next();
				if (!(listener instanceof ServiceLifecycleListener)) {
					throw new IllegalArgumentException("Listener [" + listener.getClass().getName() + "] does not implement ServiceLifecycleListener or its derivatives");
				}
			}
		}
		if (this.applicationContext == null) {
			throw new IllegalArgumentException("Required applicationContext property was not set");
		}
		if (! (this.applicationContext instanceof DefaultResourceLoader)) {
			throw new IllegalArgumentException(
					"ApplicationContext does not provide access to classloader, " +
							"provided type was : '" + this.applicationContext.getClass().getName() +
							"' which does not extend DefaultResourceLoader");
		}
	}

	/**
	 * Returns the bean properties for initializing the service.
	 *
	 * @return the service properties
	 */
	public PropertyValues getProperties() {
		return properties;
	}

	/**
	 * Sets the bean properties for initializing the service.
	 */
	public void setProperties(PropertyValues properties) {
		this.properties = properties;
	}

	/**
	 * @return Returns the serviceType.
	 */
	public Class getInterface() {
		return this.serviceType;
	}

	/**
	 * The type that the OSGi service was registered with
	 */
	public void setInterface(Class serviceType) {
		this.serviceType = serviceType;
	}

	/**
	 * @return Returns the filter.
	 */
	public String getTarget() {
		return this.filter;
	}

	/**
	 * An OSGi filter used to narrow service matches. If you
	 * just want to find a spring bean published as a service by
	 * the OsgiServiceExporter, use the beanName property instead.
	 */
	public void setTarget(String filter) {
		this.filter = filter;
	}

	/**
	 * @return Returns the beanName.
	 */
	public String getBeanName() {
		return this.beanName;
	}

	/**
	 * @return the registered service listeners
	 */
	public List getListeners() {
		return listeners;
	}

	/**
	 * Set the registered service listeners.
	 *
	 * @param listeners
	 */
	public void setListeners(List listeners) {
		this.listeners = listeners;
	}

	/**
	 * To find a bean published as a service by the OsgiServiceExporter,
	 * simply set this property. You may specify additional filtering
	 * criteria if needed (using the filter property) but this is not
	 * required.
	 */
	public void setBeanName(String beanName) {
		this.beanName = beanName;
	}

	/**
	 * Determines whether invocations on the remote service should be performed in the context
	 * of the target bundle's ClassLoader. The default is false.
	 */
	public boolean isPushBundleAsContextClassloader() {
		return pushBundleAsContextClassLoader;
	}

	/**
	 * Determines whether invocations on the remote service should be performed in the context
	 * of the target bundle's ClassLoader. The default is false.
	 *
	 * @param pushBundleAsContextClassLoader
	 */
	public void setPushBundleAsContextClassloader(boolean pushBundleAsContextClassLoader) {
		this.pushBundleAsContextClassLoader = pushBundleAsContextClassLoader;
	}

	public String getCardinality() {
		return cardinality;
	}

	/**
	 * The optional cardinality attribute allows a reference cardinality to be specified (0..1, 1..1, 0..n, or 1..n).
	 * The default is ?1..1?.
	 *
	 * @param cardinality
	 */
	public void setCardinality(String cardinality) {
		this.cardinality = cardinality;
	}

	public String getResultSummarizer() {
		return resultSummarizer;
	}

	/**
	 * The result-summarizer attribute is required for any reference cardinality other than 1..1.
	 * It specifies the name of a result summarizing bean.
	 * <p/>
	 * A result summarizer must implement the ResultSummarizer interface.
	 *
	 * @param resultSummarizer
	 */
	public void setResultSummarizer(String resultSummarizer) {
		this.resultSummarizer = resultSummarizer;
	}

	public boolean isBroadcast() {
		return broadcast;
	}

	/**
	 * The optional broadcast attribute specifies the behaviour when an operation is invoked on a reference with multiple cardinality.
	 * If set to ?true?, then the operation is invoked on all matching target services.
	 * The default is ?false?, which means that any one of the matching target services will be selected.
	 *
	 * @param broadcast
	 */
	public void setBroadcast(boolean broadcast) {
		this.broadcast = broadcast;
	}

	/* (non-Javadoc)
	 * @see org.springframework.osgi.context.BundleContextAware#setBundleContext(org.osgi.framework.BundleContext)
	 */
	public void setBundleContext(BundleContext context) {
		this.bundleContext = context;
	}

	/* (non-Javadoc)
	 * @see org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
	 */
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	/**
	 * If the target OSGi service is unregistered, should we attempt to rebind
	 * to a replacement? (For example, if the bundle providing the service is
	 * stopped and then subsequently started again).
	 * <p/>
	 * By default retry <em>will</em> be attempted.
	 * <p/>
	 * Changing this property after initialization is complete has no effect.
	 *
	 * @param retryOnUnregisteredService The retryOnUnregisteredService to set.
	 */
	public void setRetryOnUnregisteredService(boolean retryOnUnregisteredService) {
		this.retryOnUnregisteredService = retryOnUnregisteredService;
	}

	public boolean isRetryOnUnregisteredService() {
		return retryOnUnregisteredService;
	}

	/**
	 * How many times should we attempt to rebind to a target service if the
	 * service we are currently using is unregistered. Default is 3 times.
	 * <p/>
	 * Changing this property after initialization is complete has no effect.
	 *
	 * @param maxRetries The maxRetries to set.
	 */
	public void setRetryTimes(int maxRetries) {
		this.retryTimes = maxRetries;
	}

	public int getRetryTimes() {
		return retryTimes;
	}


	/**
	 * How long should we wait between failed attempts at rebinding to a service
	 * that has been unregistered.
	 * <p/>
	 * Changing this property after initialization is complete has no effect.
	 *
	 * @param millisBetweenRetries The millisBetweenRetries to set.
	 */
	public void setRetryDelayMs(long millisBetweenRetries) {
		this.retryDelayMs = millisBetweenRetries;
	}

	public long getRetryDelayMs() {
		return retryDelayMs;
	}

	// this is as nasty as dynamic sql generation.
	// build an osgi filter string to find the service we are
	// looking for.
	private String getFilterStringForServiceLookup() {
		StringBuffer sb = new StringBuffer();
		boolean andFilterWithBeanName = ((getTarget() != null) && (getBeanName() != null));
		if (andFilterWithBeanName) {
			sb.append("(&");
		}
		if (getTarget() != null) {
			sb.append(getTarget());
		}
		if (getBeanName() != null) {
			sb.append("(");
			sb.append(BeanNameServicePropertiesResolver.BEAN_NAME_PROPERTY_KEY);
			sb.append("=");
			sb.append(getBeanName());
			sb.append(")");
		}
		if (andFilterWithBeanName) {
			sb.append(")");
		}
		String filter = sb.toString();
		if (StringUtils.hasText(filter)) {
			return filter;
		} else {
			return null;
		}
	}

	/* (non-Javadoc)
	 * @see org.springframework.beans.factory.DisposableBean#destroy()
	 */
	public void destroy() throws Exception {
		if (this.serviceReference != null) {
			this.bundleContext.ungetService(this.serviceReference);
		}
	}

	/**
	 * We proxy the actual service so that we can listen to service events
	 * and rebind transparently if the service goes down and comes back up
	 * for example
	 */
	private Object getServiceProxyFor(Object target, String lookupFilter) {
		ProxyFactory pf = new ProxyFactory();
		if (getInterface().isInterface()) {
			pf.setInterfaces(new Class[]{getInterface()});
		}
		HotSwappableTargetSource targetSource = new HotSwappableTargetSource(target);
		pf.setTargetSource(targetSource);

		OsgiServiceInterceptor interceptor
				= new OsgiServiceInterceptor(this.bundleContext,
				this.serviceReference,
				targetSource, getInterface(), lookupFilter, listeners);
		interceptor.setMaxRetries(this.retryOnUnregisteredService ? this
				.retryTimes : 0);
		interceptor.setRetryIntervalMillis(this.retryDelayMs);

		pf.addAdvice(interceptor);
		if (pushBundleAsContextClassLoader) {
			pf.addAdvice(new BundleContextClassLoaderAdvice(serviceReference.getBundle()));
		}
		// Add advice for pushing the bundle context
		pf.addAdvice(new LocalBundleContext(serviceReference.getBundle()));
		// FIXME andyp -- using the application context classloader discriminates against Spring classes
		// that we may want to see, for instance org.springframework.aop.framework.Advised
		ClassLoader classLoader = this.applicationContext.getClassLoader();
		return pf.getProxy(classLoader);
	}

}

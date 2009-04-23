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

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.xml.NamespaceHandlerResolver;
import org.springframework.osgi.util.OsgiBundleUtils;
import org.springframework.osgi.util.OsgiServiceUtils;
import org.springframework.osgi.util.OsgiStringUtils;
import org.springframework.util.Assert;
import org.xml.sax.EntityResolver;

/**
 * Support class that deals with namespace parsers discovered inside Spring
 * bundles.
 * 
 * @author Costin Leau
 * 
 */
public class NamespaceManager implements InitializingBean, DisposableBean {

	private static final Log log = LogFactory.getLog(NamespaceManager.class);

	/** The set of all namespace plugins known to the extender */
	private NamespacePlugins namespacePlugins;

	/**
	 * ServiceRegistration object returned by OSGi when registering the
	 * NamespacePlugins instance as a service
	 */
	private ServiceRegistration nsResolverRegistration, enResolverRegistration = null;

	/** OSGi Environment */
	private final BundleContext context;

	private final String extenderInfo;

	private static final String META_INF = "META-INF/";

	private static final String SPRING_HANDLERS = "spring.handlers";

	private static final String SPRING_SCHEMAS = "spring.schemas";


	/**
	 * Constructs a new <code>NamespaceManager</code> instance.
	 * 
	 * @param context containing bundle context
	 */
	public NamespaceManager(BundleContext context) {
		this.context = context;

		extenderInfo = context.getBundle().getSymbolicName() + "|"
				+ OsgiBundleUtils.getBundleVersion(context.getBundle());

		// detect package admin
		this.namespacePlugins = new NamespacePlugins();
	}

	/**
	 * Registers the namespace plugin handler if this bundle defines handler
	 * mapping or schema mapping resources.
	 * 
	 * <p/>
	 * This method considers only the bundle space and not the class space.
	 * 
	 * @param bundle target bundle
	 * @param isLazyBundle indicator if the bundle analyzed is lazily activated
	 */
	public void maybeAddNamespaceHandlerFor(Bundle bundle, boolean isLazyBundle) {
		// Ignore system bundle
		if (OsgiBundleUtils.isSystemBundle(bundle)) {
			return;
		}

		boolean debug = log.isDebugEnabled();
		// FIXME: RFC-124 big bundle temporary hack
		// since embedded libraries are not discovered by findEntries and inlining them doesn't work
		// (due to resource classes such as namespace handler definitions)
		// we use getResource

		boolean hasHandlers = false, hasSchemas = false;
		// extender/RFC 124 bundle
		if (context.getBundle().equals(bundle)) {

			try {
				Enumeration<?> handlers = bundle.getResources(META_INF + SPRING_HANDLERS);
				Enumeration<?> schemas = bundle.getResources(META_INF + SPRING_SCHEMAS);

				hasHandlers = handlers != null;
				hasSchemas = schemas != null;

				if (hasHandlers && debug) {
					log.debug("Found namespace handlers: " + Collections.list(schemas));
				}
			}
			catch (IOException ioe) {
				log.warn("Cannot discover own namespaces", ioe);
			}
		}
		else {
			hasHandlers = bundle.findEntries(META_INF, SPRING_HANDLERS, false) != null;
			hasSchemas = bundle.findEntries(META_INF, SPRING_SCHEMAS, false) != null;
		}

		// if the bundle defines handlers
		if (hasHandlers) {

			if (debug)
				log.debug("Adding as " + (isLazyBundle ? " lazy " : "") + "namespace handler resolver bundle "
						+ OsgiStringUtils.nullSafeNameAndSymName(bundle));

			if (isLazyBundle) {
				this.namespacePlugins.addPlugin(bundle, isLazyBundle);
			}
			else {
				// check type compatibility between the bundle's and spring-extender's spring version
				if (hasCompatibleNamespaceType(bundle)) {
					this.namespacePlugins.addPlugin(bundle, isLazyBundle);
				}
				else {
					if (debug)
						log.debug("Bundle [" + OsgiStringUtils.nullSafeNameAndSymName(bundle)
								+ "] declares namespace handlers but is not compatible with extender [" + extenderInfo
								+ "]; ignoring...");
				}
			}
		}
		else {
			// bundle declares only schemas, add it though the handlers might not be compatible...
			// FIXME: check should not be performed for these bundles
			if (hasSchemas)
				this.namespacePlugins.addPlugin(bundle, isLazyBundle);
		}
	}

	private boolean hasCompatibleNamespaceType(Bundle bundle) {
		return namespacePlugins.isTypeCompatible(bundle);
	}

	/**
	 * Removes the target bundle from the set of those known to provide handler
	 * or schema mappings.
	 * 
	 * @param bundle handler bundle
	 */
	public void maybeRemoveNameSpaceHandlerFor(Bundle bundle) {
		Assert.notNull(bundle);
		boolean removed = this.namespacePlugins.removePlugin(bundle);
		if (removed && log.isDebugEnabled()) {
			log.debug("Removed namespace handler resolver for " + OsgiStringUtils.nullSafeNameAndSymName(bundle));
		}
	}

	/**
	 * Registers the NamespacePlugins instance as an Osgi Resolver service
	 */
	private void registerResolverServices() {
		if (log.isDebugEnabled()) {
			log.debug("Registering Spring NamespaceHandlerResolver and EntityResolver...");
		}

		nsResolverRegistration = context.registerService(new String[] { NamespaceHandlerResolver.class.getName() },
			this.namespacePlugins, null);

		enResolverRegistration = context.registerService(new String[] { EntityResolver.class.getName() },
			this.namespacePlugins, null);

	}

	/**
	 * Unregisters the NamespaceHandler and EntityResolver service
	 */
	private void unregisterResolverService() {

		boolean result = OsgiServiceUtils.unregisterService(nsResolverRegistration);
		result = result || OsgiServiceUtils.unregisterService(enResolverRegistration);

		if (result) {
			if (log.isDebugEnabled())
				log.debug("Unregistering Spring NamespaceHandler and EntityResolver service");
		}

		this.nsResolverRegistration = null;
		this.enResolverRegistration = null;
	}

	public NamespacePlugins getNamespacePlugins() {
		return namespacePlugins;
	}

	//
	// Lifecycle methods
	//

	public void afterPropertiesSet() {
		registerResolverServices();
	}

	public void destroy() {
		unregisterResolverService();
		this.namespacePlugins.destroy();
		this.namespacePlugins = null;
	}
}
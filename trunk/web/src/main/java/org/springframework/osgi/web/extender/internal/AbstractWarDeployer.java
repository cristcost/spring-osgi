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

package org.springframework.osgi.web.extender.internal;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.Bundle;
import org.springframework.core.CollectionFactory;
import org.springframework.osgi.util.OsgiStringUtils;
import org.springframework.osgi.web.extender.WarDeployer;

/**
 * Template class offering common functionality for war deployers such as
 * tracking.
 * 
 * @author Costin Leau
 */
public abstract class AbstractWarDeployer implements WarDeployer {

	/** logger */
	protected final Log log = LogFactory.getLog(getClass());

	/** map associating bundles with specific deployment artifacts */
	private final Map deployments = CollectionFactory.createConcurrentMap(4);


	public void deploy(Bundle bundle, String contextPath) throws Exception {
		if (log.isDebugEnabled())
			log.debug("Creating deployment for [" + OsgiStringUtils.nullSafeNameAndSymName(bundle) + "] at ["
					+ contextPath + "] on server " + getServerInfo());
		Object deployment = createDeployment(bundle, contextPath);
		deployments.put(bundle, deployment);

		if (log.isDebugEnabled())
			log.debug("About to deploy [" + OsgiStringUtils.nullSafeNameAndSymName(bundle) + "] to [" + contextPath
					+ "] on server " + getServerInfo());

		startDeployment(deployment);
	}

	public void undeploy(Bundle bundle, String contextPath) throws Exception {
		if (log.isDebugEnabled())
			log.debug("About to undeploy [" + OsgiStringUtils.nullSafeNameAndSymName(bundle) + "] on server "
					+ getServerInfo());

		Object deployment = deployments.remove(bundle);
		if (deployment != null)
			stopDeployment(bundle, deployment);
	}

	/**
	 * Creates and configures the web deployment for the given bundle. The
	 * returned object is used for tracking the bundle and implementations are
	 * free to use whatever appeals to the target environment. The returned
	 * object will be given as argument to {@link #deploy(Bundle)} and
	 * {@link #undeploy(Bundle)}.
	 * 
	 * @param bundle OSGi bundle deployed as war
	 * @param contextPath WAR context path
	 * @return web deployment artifact
	 * @throws Exception if something goes wrong
	 */
	protected abstract Object createDeployment(Bundle bundle, String contextPath) throws Exception;

	/**
	 * Starts the deployment artifact.
	 * 
	 * @param deployment web deployment artifact
	 * @throws Exception if something goes wrong
	 */
	protected abstract void startDeployment(Object deployment) throws Exception;

	/**
	 * Stops the deployment artifact.
	 * 
	 * @param bundle OSGi bundle backing the OSGi deployment
	 * @param deployment web deployment artifact
	 * @throws Exception if something goes wrong
	 */
	protected abstract void stopDeployment(Bundle bundle, Object deployment) throws Exception;

	/**
	 * Returns a nice String representation of the underlying server for logging
	 * messages.
	 * 
	 * @return toString for the running environment
	 */
	protected abstract String getServerInfo();
}
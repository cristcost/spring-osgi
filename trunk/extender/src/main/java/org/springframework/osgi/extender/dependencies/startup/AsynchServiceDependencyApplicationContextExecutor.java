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
package org.springframework.osgi.extender.dependencies.startup;

import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.Bundle;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.osgi.context.DelegatedExecutionOsgiBundleApplicationContext;
import org.springframework.osgi.context.OsgiBundleApplicationContextExecutor;
import org.springframework.osgi.util.OsgiStringUtils;
import org.springframework.osgi.util.concurrent.Counter;
import org.springframework.util.Assert;

/**
 * Asynchronous executor that breaks the 'traditional'
 * {@link ConfigurableApplicationContext#refresh()} in two pieces so that beans
 * are not actually created unless the OSGi service imported are present.
 * 
 * @author Hal Hildebrand
 * @author Costin Leau
 */
public class AsynchServiceDependencyApplicationContextExecutor implements OsgiBundleApplicationContextExecutor {

	private static final Log log = LogFactory.getLog(AsynchServiceDependencyApplicationContextExecutor.class);

	/**
	 * this class monitor. Since multiple threads will access this object, we
	 * have to use synchronization to guarantee thread visibility
	 */
	private final Object monitor = new Object();

	/** waiting timeout */
	private long timeout;

	/** the timer used for executing the timeout */
	private Timer watchdog;

	/** watchdog task */
	private TimerTask watchdogTask;

	/** OSGi service dependencyDetector used for detecting dependencies */
	protected DependencyServiceManager dependencyDetector;

	protected DelegatedExecutionOsgiBundleApplicationContext delegateContext;

	/**
	 * State of the associated context from the executor POV.
	 */
	private ContextState state = ContextState.INITIALIZED;

	private TaskExecutor taskExecutor;

	/**
	 * A synchronized counter used by the Listener to determine the number of
	 * children to wait for when shutting down.
	 */
	private Counter monitorCounter;

	/**
	 * The task for the watch dog.
	 * 
	 * @author Hal Hildebrand
	 */
	private class WatchDogTask extends TimerTask {

		public void run() {
			log.warn("timeout occured before finding service dependencies for [" + delegateContext.getDisplayName()
					+ "]");

			ApplicationContextException e = new ApplicationContextException("Application context initializition for '"
					+ OsgiStringUtils.nullSafeSymbolicName(getBundle()) + "' has timed out");
			e.fillInStackTrace();
			fail(e);
		}
	}

	/**
	 * Crete the Runnable action which will complete the context creation
	 * process. This process can be called synchronously or asynchronously,
	 * depending on context configuration and availability of dependencies.
	 * Therefore, it is super critical to have the correct context class loader
	 * set as well as the correct error handling.
	 * 
	 * @author Hal Hildebrand
	 * @author Costin Leau
	 * 
	 */
	private class CompleteRefreshTask implements Runnable {
		public void run() {
			boolean debug = log.isDebugEnabled();
			if (debug)
				log.debug("completing refresh for " + getDisplayName());

			try {

				synchronized (monitor) {
					if (state != ContextState.DEPENDENCIES_RESOLVED) {
						if (debug)
							logWrongState(ContextState.DEPENDENCIES_RESOLVED);
						return;
					}
					// otherwise update the state
					state = ContextState.STARTED;
				}

				// Continue with the refresh process...
				synchronized (delegateContext.getMonitor()) {
					delegateContext.postRefresh();
				}

			}
			catch (Throwable t) {
				fail(t);
			}

		}

	}

	public AsynchServiceDependencyApplicationContextExecutor(
			DelegatedExecutionOsgiBundleApplicationContext delegateContext) {
		this.delegateContext = delegateContext;
		this.delegateContext.setExecutor(this);
	}

	/**
	 * Provide a continuation like approach to the application context. Will
	 * execute just some parts of refresh and then leave the rest of to be
	 * executed after a number of conditions have been met.
	 */
	public void refresh() throws BeansException, IllegalStateException {
		if (log.isDebugEnabled())
			log.debug("starting first stage of refresh for " + getDisplayName());

		// sanity check
		init();

		// start the first stage
		stageOne();
	}

	/** Do some sanity checks */
	protected void init() {
		synchronized (monitor) {
			Assert.notNull(watchdog, "watchdog timer required");
			Assert.notNull(monitorCounter, " monitorCounter required");
		}
	}

	/**
	 * Start the first stage of the application context refresh. Determines the
	 * service dependencies and if there are any, registers a OSGi service
	 * dependencyDetector which will continue the refresh process
	 * asynchronously.
	 * 
	 */
	protected void stageOne() {

		boolean debug = log.isDebugEnabled();

		try {
			if (debug)
				log.debug("calling preRefresh on " + getDisplayName());

			synchronized (monitor) {

				// check before kicking the pedal
				if (state != ContextState.INITIALIZED) {
					if (debug)
						logWrongState(ContextState.INITIALIZED);
					return;
				}

				state = ContextState.RESOLVING_DEPENDENCIES;
			}

			synchronized (delegateContext.getMonitor()) {
				delegateContext.preRefresh();
			}

			if (debug)
				log.debug("prerefresh completed; determining dependencies...");
			
			DependencyServiceManager dl = createDependencyServiceListener();
			dl.findServiceDependencies();

			if (!dl.isSatisfied()) {
				// Asynchronously finish the context refresh process
				dependencyDetector = dl;
				if (debug)
					log.debug("registering service dependency dependencyDetector for " + getDisplayName());
				dependencyDetector.register();
				startWatchDog();
			}

			else {
				// Synchronously finish the context refresh process
				if (debug) {
					log.debug("No outstanding dependencies, completing initialization for " + getDisplayName());
				}
				stageTwo();
			}
		}
		catch (Throwable e) {
			fail(e);
		}

	}

	protected void stageTwo() {
		boolean debug = log.isDebugEnabled();

		if (debug)
			log.debug("starting stage two for " + getDisplayName());

		synchronized (monitor) {

			if (state != ContextState.RESOLVING_DEPENDENCIES) {
				if (debug)
					logWrongState(ContextState.RESOLVING_DEPENDENCIES);
				return;
			}

			stopWatchDog();
			state = ContextState.DEPENDENCIES_RESOLVED;
		}

		// always delegate to the taskExecutor since we might be called by the
		// OSGi platform listener
		taskExecutor.execute(new CompleteRefreshTask());
	}

	/**
	 * The application context is being shutdown. Deregister the listener and
	 * prevent classes from being loaded since it's Doom's day.
	 */
	public void close() {
		boolean debug = log.isDebugEnabled();

		if (debug)
			log.debug("about to close down..." + getDisplayName());

		boolean normalShutdown = false;

		synchronized (monitor) {

			// no need for cleanup
			if (state.isDown()) {
				return;
			}
			if (debug)
				log.debug("closing appCtx for " + getDisplayName());

			if (dependencyDetector != null) {
				dependencyDetector.deregister();
				dependencyDetector = null;
			}

			if (state == ContextState.STARTED) {
				if (debug)
					log.debug("shutting down normaly appCtx " + getDisplayName());
				// close the context only if it was actually started
				state = ContextState.STOPPED;
				normalShutdown = true;
			}
			else {
				if (debug)
					log.debug("no need to stop context (it hasn't been started yet)");
				state = ContextState.INTERRUPTED;
			}
		}
		try {
			synchronized (delegateContext.getMonitor()) {
				if (normalShutdown)
					delegateContext.normalClose();
			}
		}
		finally {
			monitorCounter.decrement();
		}

	}

	/**
	 * Fail creating the context. Figure out unsatisfied dependencies and
	 * provide a very nice log message before closing the appContext.
	 * 
	 * Normally this method is called when an exception is caught.
	 * 
	 * @param t - the offending Throwable which caused our demise
	 */
	private void fail(Throwable t) {
		try {
			close();

			boolean debug = log.isDebugEnabled();

			if (debug) {
				StringBuffer buf = new StringBuffer();
				if (dependencyDetector == null || dependencyDetector.getUnsatisfiedDependencies().isEmpty()) {
					buf.append("none");
				}
				else {
					for (Iterator dependencies = dependencyDetector.getUnsatisfiedDependencies().iterator(); dependencies.hasNext();) {
						ServiceDependency dependency = (ServiceDependency) dependencies.next();
						buf.append(dependency.toString());
						if (dependencies.hasNext()) {
							buf.append(", ");
						}
					}
				}
				StringBuffer message = new StringBuffer();
				message.append("Unable to create application context for [");
				message.append(getBundleSymbolicName());
				message.append("], unsatisfied dependencies: ");
				message.append(buf.toString());

				log.debug(message.toString(), t);
				log.debug("[" + getBundleSymbolicName() + "]" + " creation calling code: ", t);
			}

		}
		catch (Throwable e) {
			// last ditch effort to get useful error information
			t.printStackTrace();
			e.printStackTrace();
		}
	}

	protected DependencyServiceManager createDependencyServiceListener() {
		return new DependencyServiceManager(this, delegateContext);
	}

	/**
	 * Schedule the watchdog task.
	 */
	protected void startWatchDog() {
		synchronized (monitor) {
			watchdogTask = new WatchDogTask();
			watchdog.schedule(watchdogTask, timeout * 1000);
		}
	}

	protected void stopWatchDog() {
		synchronized (monitor) {
			if (watchdogTask != null) {
				watchdogTask.cancel();
				watchdogTask = null;
			}
		}
	}

	/**
	 * The timeout for waiting for service dependencies.
	 * 
	 * @param timeout
	 */
	public void setTimeout(long timeout) {
		synchronized (monitor) {
			this.timeout = timeout;
		}
	}

	public void setTaskExecutor(TaskExecutor taskExec) {
		synchronized (monitor) {
			this.taskExecutor = taskExec;
		}
	}

	public ContextState getState() {
		synchronized (monitor) {
			return state;
		}
	}

	private Bundle getBundle() {
		synchronized (monitor) {
			return delegateContext.getBundle();
		}
	}

	private String getDisplayName() {
		synchronized (monitor) {
			return delegateContext.getDisplayName();
		}

	}

	private String getBundleSymbolicName() {
		return OsgiStringUtils.nullSafeSymbolicName(getBundle());
	}

	public void setWatchdog(Timer watchdog) {
		synchronized (monitor) {
			this.watchdog = watchdog;
		}
	}

	/**
	 * Reduce the code pollution.
	 * @param expected the expected value for the context state.
	 */
	private void logWrongState(ContextState expected) {
		log.debug("expecting state (" + expected + ") not (" + state + ") for context [" + getDisplayName()
				+ "]; assuming an interruption and bailing out");
	}

	public void setMonitoringCounter(Counter asynchCounter) {
		this.monitorCounter = asynchCounter;
	}

}

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

package org.springframework.osgi.extender.internal.blueprint.activator;

import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.container.BlueprintEvent;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.osgi.blueprint.container.BlueprintConverter;
import org.springframework.osgi.blueprint.container.SpringBlueprintContainer;
import org.springframework.osgi.blueprint.container.support.BlueprintContainerServicePublisher;
import org.springframework.osgi.blueprint.container.support.BlueprintEditorRegistrar;
import org.springframework.osgi.blueprint.reflect.EnvironmentManagerFactoryBean;
import org.springframework.osgi.context.ConfigurableOsgiBundleApplicationContext;
import org.springframework.osgi.context.event.OsgiBundleApplicationContextEvent;
import org.springframework.osgi.context.event.OsgiBundleApplicationContextListener;
import org.springframework.osgi.context.event.OsgiBundleContextFailedEvent;
import org.springframework.osgi.context.event.OsgiBundleContextRefreshedEvent;
import org.springframework.osgi.extender.event.BootstrappingDependenciesEvent;
import org.springframework.osgi.extender.event.BootstrappingDependenciesFailedEvent;
import org.springframework.osgi.extender.internal.activator.OsgiContextProcessor;
import org.springframework.osgi.extender.internal.blueprint.event.EventAdminDispatcher;

/**
 * Blueprint specific context processor.
 * 
 * @author Costin Leau
 */
public class BlueprintContainerProcessor implements
		OsgiBundleApplicationContextListener<OsgiBundleApplicationContextEvent>, OsgiContextProcessor {

	private final EventAdminDispatcher dispatcher;
	private final BlueprintListenerManager listenerManager;
	private final Bundle extenderBundle;

	public BlueprintContainerProcessor(EventAdminDispatcher dispatcher, BlueprintListenerManager listenerManager,
			Bundle extenderBundle) {
		this.dispatcher = dispatcher;
		this.listenerManager = listenerManager;
		this.extenderBundle = extenderBundle;
	}

	public void postProcessClose(ConfigurableOsgiBundleApplicationContext context) {
		BlueprintEvent destroyedEvent =
				new BlueprintEvent(BlueprintEvent.DESTROYED, context.getBundle(), extenderBundle);

		listenerManager.blueprintEvent(destroyedEvent);
		dispatcher.afterClose(destroyedEvent);
	}

	public void postProcessRefresh(ConfigurableOsgiBundleApplicationContext context) {
		BlueprintEvent createdEvent = new BlueprintEvent(BlueprintEvent.CREATED, context.getBundle(), extenderBundle);

		listenerManager.blueprintEvent(createdEvent);
		dispatcher.afterRefresh(createdEvent);
	}

	public void postProcessRefreshFailure(ConfigurableOsgiBundleApplicationContext context, Throwable th) {
		BlueprintEvent failureEvent =
				new BlueprintEvent(BlueprintEvent.FAILURE, context.getBundle(), extenderBundle, th);

		listenerManager.blueprintEvent(failureEvent);
		dispatcher.refreshFailure(failureEvent);
	}

	public void preProcessClose(ConfigurableOsgiBundleApplicationContext context) {
		BlueprintEvent destroyingEvent =
				new BlueprintEvent(BlueprintEvent.DESTROYING, context.getBundle(), extenderBundle);

		listenerManager.blueprintEvent(destroyingEvent);
		dispatcher.beforeClose(destroyingEvent);
	}

	public void preProcessRefresh(final ConfigurableOsgiBundleApplicationContext context) {
		final BundleContext bundleContext = context.getBundleContext();
		// create the ModuleContext adapter
		final BlueprintContainer blueprintContainer = new SpringBlueprintContainer(context, bundleContext);
		// add service publisher
		context.addApplicationListener(new BlueprintContainerServicePublisher(blueprintContainer, bundleContext));
		// add moduleContext bean
		context.addBeanFactoryPostProcessor(new BeanFactoryPostProcessor() {

			private static final String BLUEPRINT_BUNDLE = "blueprintBundle";
			private static final String BLUEPRINT_BUNDLE_CONTEXT = "blueprintBundleContext";
			private static final String BLUEPRINT_CONTAINER = "blueprintContainer";
			private static final String BLUEPRINT_EXTENDER = "blueprintExtenderBundle";
			private static final String BLUEPRINT_CONVERTER = "blueprintConverter";

			public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
				// lazy logger evaluation
				Log logger = LogFactory.getLog(context.getClass());

				if (beanFactory instanceof BeanDefinitionRegistry) {
					logger.warn("Environmental beans will be registered as singletons instead "
							+ "of usual bean definitions since beanFactory " + beanFactory
							+ " is not a BeanDefinitionRegistry");
				}

				// add blueprint container bean
				addPredefinedBlueprintBean(beanFactory, BLUEPRINT_BUNDLE, bundleContext.getBundle(), logger);
				addPredefinedBlueprintBean(beanFactory, BLUEPRINT_BUNDLE_CONTEXT, bundleContext, logger);
				addPredefinedBlueprintBean(beanFactory, BLUEPRINT_CONTAINER, blueprintContainer, logger);
				// addPredefinedBlueprintBean(beanFactory, BLUEPRINT_EXTENDER, extenderBundle, logger);
				addPredefinedBlueprintBean(beanFactory, BLUEPRINT_CONVERTER, new BlueprintConverter(), logger);

				// add Blueprint built-in converters
				beanFactory.addPropertyEditorRegistrar(new BlueprintEditorRegistrar());
			}

			private void addPredefinedBlueprintBean(ConfigurableListableBeanFactory beanFactory, String beanName,
					Object value, Log logger) {
				if (!beanFactory.containsLocalBean(beanName)) {
					logger.debug("Registering pre-defined bean named " + beanName);
					if (beanFactory instanceof BeanDefinitionRegistry) {
						BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;

						GenericBeanDefinition def = new GenericBeanDefinition();
						def.setBeanClass(EnvironmentManagerFactoryBean.class);
						ConstructorArgumentValues cav = new ConstructorArgumentValues();
						cav.addIndexedArgumentValue(0, value);
						def.setConstructorArgumentValues(cav);
						def.setLazyInit(false);
						registry.registerBeanDefinition(beanName, def);

					} else {
						beanFactory.registerSingleton(beanName, value);
					}

				} else {
					logger.warn("A bean named " + beanName
							+ " already exists; aborting registration of the predefined value...");
				}
			}
		});

		BlueprintEvent creatingEvent = new BlueprintEvent(BlueprintEvent.CREATING, context.getBundle(), extenderBundle);
		listenerManager.blueprintEvent(creatingEvent);

		dispatcher.beforeRefresh(creatingEvent);
	}

	public void onOsgiApplicationEvent(OsgiBundleApplicationContextEvent evt) {

		if (evt instanceof BootstrappingDependenciesEvent) {
			BootstrappingDependenciesEvent event = (BootstrappingDependenciesEvent) evt;
			Collection<String> flts = event.getDependencyFilters();
			String[] filters = flts.toArray(new String[flts.size()]);
			BlueprintEvent graceEvent =
					new BlueprintEvent(BlueprintEvent.GRACE_PERIOD, evt.getBundle(), extenderBundle, filters);
			listenerManager.blueprintEvent(graceEvent);
			dispatcher.grace(graceEvent);
			return;
		}

		if (evt instanceof BootstrappingDependenciesFailedEvent) {
			BootstrappingDependenciesFailedEvent event = (BootstrappingDependenciesFailedEvent) evt;
			Collection<String> flts = event.getDependencyFilters();
			String[] filters = flts.toArray(new String[flts.size()]);
			BlueprintEvent failureEvent =
					new BlueprintEvent(BlueprintEvent.FAILURE, evt.getBundle(), extenderBundle, filters, event
							.getFailureCause());
			listenerManager.blueprintEvent(failureEvent);
			dispatcher.refreshFailure(failureEvent);
			return;
		}

		if (evt instanceof OsgiBundleContextRefreshedEvent) {
			postProcessRefresh((ConfigurableOsgiBundleApplicationContext) evt.getApplicationContext());
			return;
		}

		if (evt instanceof OsgiBundleContextFailedEvent) {
			OsgiBundleContextFailedEvent failureEvent = (OsgiBundleContextFailedEvent) evt;
			postProcessRefreshFailure(
					((ConfigurableOsgiBundleApplicationContext) failureEvent.getApplicationContext()), failureEvent
							.getFailureCause());
			return;
		}
	}
}
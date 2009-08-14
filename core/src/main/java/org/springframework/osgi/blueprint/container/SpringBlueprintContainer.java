/*
 * Copyright 2008 the original author or authors.
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
 */

package org.springframework.osgi.blueprint.container;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.osgi.framework.BundleContext;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.container.ComponentDefinitionException;
import org.osgi.service.blueprint.container.NoSuchComponentException;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.osgi.blueprint.reflect.MetadataFactory;
import org.springframework.util.CollectionUtils;

/**
 * Default {@link ModuleContext} implementation. Wraps a Spring's {@link ConfigurableListableBeanFactory} to the
 * BlueprintContainer interface.
 * 
 * <b>Note</b>: This class does not fully implements the Blueprint contract: for example it does not trigger any of the
 * Blueprint events or does not perform exception handling - these concerned are left to the Blueprint extender.
 * 
 * @author Adrian Colyer
 * @author Costin Leau
 */
public class SpringBlueprintContainer implements BlueprintContainer {

	// cannot use a ConfigurableBeanFactory since the context is not yet refreshed at construction time
	private final ConfigurableApplicationContext applicationContext;
	private final BundleContext bundleContext;
	private transient ConfigurableListableBeanFactory beanFactory;

	public SpringBlueprintContainer(ConfigurableApplicationContext applicationContext, BundleContext bundleContext) {
		this.applicationContext = applicationContext;
		this.bundleContext = bundleContext;
	}

	public Object getComponentInstance(String name) throws NoSuchComponentException {
		if (applicationContext.containsBean(name)) {
			try {
				return applicationContext.getBean(name);
			} catch (RuntimeException ex) {
				throw new ComponentDefinitionException("Cannot get component instance " + name, ex);
			}
		} else {
			throw new NoSuchComponentException(name);
		}
	}

	public ComponentMetadata getComponentMetadata(String name) throws NoSuchComponentException {
		if (applicationContext.containsBeanDefinition(name)) {
			BeanDefinition beanDefinition = getBeanFactory().getBeanDefinition(name);
			return MetadataFactory.buildComponentMetadataFor(name, beanDefinition);
		} else {
			throw new NoSuchComponentException(name);
		}
	}

	public Set<String> getComponentIds() {
		String[] names = applicationContext.getBeanDefinitionNames();
		Set<String> components = new LinkedHashSet<String>(names.length);
		CollectionUtils.mergeArrayIntoCollection(names, components);
		Set<String> filtered = MetadataFactory.filterIds(components);
		return Collections.unmodifiableSet(filtered);
	}

	@SuppressWarnings("unchecked")
	public Collection<?> getMetadata(Class type) {
		return getComponentMetadata(type);
	}

	@SuppressWarnings("unchecked")
	private <T extends ComponentMetadata> Collection<T> getComponentMetadata(Class<T> clazz) {
		List<ComponentMetadata> metadatas = getComponentMetadataForAllComponents();
		Collection<T> filteredMetadata = new ArrayList<T>(metadatas.size());

		for (ComponentMetadata metadata : metadatas) {
			if (clazz.isInstance(metadata)) {
				filteredMetadata.add((T) metadata);
			}
		}

		return Collections.unmodifiableCollection(filteredMetadata);
	}

	private List<ComponentMetadata> getComponentMetadataForAllComponents() {
		return MetadataFactory.buildComponentMetadataFor(getBeanFactory());
	}

	private ConfigurableListableBeanFactory getBeanFactory() {
		if (beanFactory == null) {
			beanFactory = applicationContext.getBeanFactory();
		}

		return beanFactory;
	}
}
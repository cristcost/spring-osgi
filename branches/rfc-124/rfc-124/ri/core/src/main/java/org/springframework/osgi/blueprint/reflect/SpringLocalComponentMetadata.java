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

package org.springframework.osgi.blueprint.reflect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.osgi.service.blueprint.reflect.ConstructorInjectionMetadata;
import org.osgi.service.blueprint.reflect.LocalComponentMetadata;
import org.osgi.service.blueprint.reflect.MethodInjectionMetadata;
import org.osgi.service.blueprint.reflect.PropertyInjectionMetadata;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.util.StringUtils;

/**
 * Default {@link LocalComponentMetadata} implementation based on Spring's
 * {@link BeanDefinition}.
 * 
 * @author Costin Leau
 */
class SpringLocalComponentMetadata extends SpringComponentMetadata implements LocalComponentMetadata {

	private final ConstructorInjectionMetadata constructorMetadata;
	private final MethodInjectionMetadata methodMetadata;
	private final Collection<PropertyInjectionMetadata> propertyMetadata;
	private final ComponentMetadata factoryComponent;


	/**
	 * Constructs a new <code>SpringLocalComponentMetadata</code> instance.
	 * 
	 * @param definition Spring bean definition
	 */
	public SpringLocalComponentMetadata(BeanDefinition definition) {
		super(definition);

		constructorMetadata = new SimpleConstructorInjectionMetadata(definition);
		methodMetadata = (StringUtils.hasText(beanDefinition.getFactoryMethodName()) ? new SimpleMethodInjectionMetadata(
			definition)
				: null);

		List<PropertyValue> pvs = (List<PropertyValue>) definition.getPropertyValues().getPropertyValueList();
		List<PropertyInjectionMetadata> props = new ArrayList<PropertyInjectionMetadata>(pvs.size());

		for (PropertyValue propertyValue : pvs) {
			props.add(new SimplePropertyInjectionMetadata(propertyValue));
		}

		propertyMetadata = Collections.unmodifiableCollection(props);

		final String factoryName = beanDefinition.getFactoryBeanName();
		if (StringUtils.hasText(factoryName)) {
			// FIXME: refactor this into a top-level class
			factoryComponent = new ComponentMetadata() {

				public Set<String> getExplicitDependencies() {
					throw new UnsupportedOperationException(
						"the dependencies for factory components aren't resolved yet");
				}

				public String getName() {
					return factoryName;
				}
			};
		}
		else {
			factoryComponent = null;
		}
	}

	public String getClassName() {
		return beanDefinition.getBeanClassName();
	}

	public ConstructorInjectionMetadata getConstructorInjectionMetadata() {
		return constructorMetadata;
	}

	public String getDestroyMethodName() {
		return beanDefinition.getInitMethodName();
	}

	public ComponentMetadata getFactoryComponent() {
		return factoryComponent;
	}

	public MethodInjectionMetadata getFactoryMethodMetadata() {
		return methodMetadata;
	}

	public String getInitMethodName() {
		return beanDefinition.getInitMethodName();
	}

	public Collection<PropertyInjectionMetadata> getPropertyInjectionMetadata() {
		return propertyMetadata;
	}

	public String getScope() {
		return beanDefinition.getScope();
	}

	public boolean isLazy() {
		return beanDefinition.isLazyInit();
	}
}
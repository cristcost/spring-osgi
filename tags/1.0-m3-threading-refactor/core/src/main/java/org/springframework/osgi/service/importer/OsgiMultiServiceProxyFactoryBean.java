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
package org.springframework.osgi.service.importer;

import java.util.Comparator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.Filter;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.core.Constants;
import org.springframework.osgi.service.collection.OsgiServiceCollection;
import org.springframework.osgi.service.collection.OsgiServiceList;
import org.springframework.osgi.service.collection.OsgiServiceSet;
import org.springframework.osgi.service.collection.OsgiServiceSortedList;
import org.springframework.osgi.service.collection.OsgiServiceSortedSet;
import org.springframework.osgi.service.CardinalityOptions;
import org.springframework.util.Assert;

/**
 * Specialized single-service proxy creator. Will return a proxy that will
 * select only one OSGi service which matches the configuration criteria. If the
 * selected service goes away, the proxy will search for a replacement.
 * 
 * @see java.util.Collection
 * @see java.util.List
 * @see java.util.Set
 * @see java.util.SortedSet
 * 
 * @author Costin Leau
 * 
 */
public class OsgiMultiServiceProxyFactoryBean extends AbstractOsgiServiceProxyFactoryBean {

	/**
	 * Enumeration style class used to specify the collection type that this
	 * FactoryBean should produce.
	 * 
	 * @author Costin Leau
	 * 
	 */
	public static abstract class CollectionOptions {

		public static final Constants COLLECTION_OPTIONS = new Constants(CollectionOptions.class);

		public static final int COLLECTION = 1;

		public static final int LIST = 2;

		public static final int SET = 3;

		public static final int SORTED_LIST = 4;

		public static final int SORTED_SET = 5;

		public static final Class[] classMapping = { OsgiServiceCollection.class, OsgiServiceList.class,
				OsgiServiceSet.class, OsgiServiceSortedList.class, OsgiServiceSortedSet.class };

		public static Class getClassMapping(int collectionOptions) {
			if (isOptionValid(collectionOptions))
				return classMapping[collectionOptions - 1];
			else
				throw new IllegalArgumentException();
		}

		public static boolean isOptionValid(int option) {
			return (option > 0 && option == classMapping.length);
		}
	}

	private static final Log log = LogFactory.getLog(OsgiMultiServiceProxyFactoryBean.class);

	private Object proxy;

	private Comparator comparator;

	private int collectionType = CollectionOptions.COLLECTION;

	public Object getObject() {
		if (!initialized)
			throw new FactoryBeanNotInitializedException();

		// lazy creation
		if (proxy == null) {
			proxy = createMultiServiceCollection(getUnifiedFilter());
		}

		return proxy;
	}

	public Class getObjectType() {
		return (proxy != null ? proxy.getClass() : CollectionOptions.getClassMapping(collectionType));
	}

	public void destroy() throws Exception {
		// FIXME: do cleanup
	}

	protected Object createMultiServiceCollection(Filter filter) {
		if (log.isDebugEnabled())
			log.debug("creating a multi-value/collection proxy");

		OsgiServiceCollection collection;

		switch (collectionType) {
		case CollectionOptions.COLLECTION:
			Assert.isNull(comparator, "when specifying a Comparator, a Set or a List have to be used");
			collection = new OsgiServiceCollection(filter, bundleContext, classLoader, mandatory);
			break;

		case CollectionOptions.LIST:

			collection = (comparator == null ? new OsgiServiceList(filter, bundleContext, classLoader, mandatory)
					: new OsgiServiceSortedList(filter, bundleContext, classLoader, comparator, mandatory));
			break;

		case CollectionOptions.SET:
			collection = (comparator == null ? new OsgiServiceSet(filter, bundleContext, classLoader, mandatory)
					: new OsgiServiceSortedSet(filter, bundleContext, classLoader, comparator, mandatory));
			break;

		case CollectionOptions.SORTED_LIST:
			collection = new OsgiServiceSortedList(filter, bundleContext, classLoader, comparator, mandatory);
			break;

		case CollectionOptions.SORTED_SET:
			collection = new OsgiServiceSortedSet(filter, bundleContext, classLoader, comparator, mandatory);
			break;

		default:
			throw new IllegalArgumentException("unknown collection type:" + collectionType);
		}

		collection.setListeners(listeners);
		collection.setContextClassLoader(contextClassloader);
		collection.afterPropertiesSet();

		return collection;
	}

	public void setComparator(Comparator comparator) {
		this.comparator = comparator;
	}

	public void setCollectionType(int collectionType) {
		this.collectionType = collectionType;
	}

    public void setCardinality(String cardinality) {
        switch (CardinalityOptions.asInt(cardinality)) {
            case CardinalityOptions.C_0__N:
                mandatory = false;
                break;
            case CardinalityOptions.C_1__N:
                mandatory = true;
                break;
            default:
                throw new BeanInitializationException("Invalid cardinality [" + cardinality
                        + "] for bean type [" + getClass().getName() +"]");
        };
    }
    
}
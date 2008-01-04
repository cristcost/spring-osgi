package org.springframework.osgi.iandt.extender;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.util.tracker.ServiceTracker;
import org.springframework.context.support.AbstractRefreshableApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.osgi.test.AbstractConfigurableBundleCreatorTests;
import org.springframework.util.CollectionUtils;

/**
 * @author Hal Hildebrand Date: May 21, 2007 Time: 4:43:52 PM
 */
public class ExtenderTest extends AbstractConfigurableBundleCreatorTests {

	protected String getManifestLocation() {
		return null;
	}

	// Overridden to remove the spring extender bundle!
	protected String[] getMandatoryBundles() {
		String[] bundles = super.getMandatoryBundles();
		List list = new ArrayList(bundles.length);

		// remove extender
		CollectionUtils.mergeArrayIntoCollection(bundles, list);

		boolean found = false;
		for (Iterator iter = list.iterator(); iter.hasNext() && !found;) {
			String element = (String) iter.next();
			if (element.indexOf("extender") >= 0) {
				iter.remove();
				found = true;
			}
		}

		return (String[]) list.toArray(new String[list.size()]);
	}

	// Specifically cannot wait - test scenario has bundles which are spring
	// powered, but will not be started.
	protected boolean shouldWaitForSpringBundlesContextCreation() {
		return false;
	}

	protected String[] getBundles() {
		return new String[] { "org.springframework.osgi, org.springframework.osgi.iandt.lifecycle,"
				+ getSpringOsgiVersion() };
	}

	public void testLifecycle() throws Exception {
		assertNull("Guinea pig has already been started",
			System.getProperty("org.springframework.osgi.iandt.lifecycle.GuineaPig.close"));

		StringBuffer filter = new StringBuffer();
		filter.append("(&");
		filter.append("(").append(Constants.OBJECTCLASS).append("=").append(
			AbstractRefreshableApplicationContext.class.getName()).append(")");
		filter.append("(").append("org.springframework.context.service.name");
		filter.append("=").append("org.springframework.osgi.iandt.lifecycle").append(")");
		filter.append(")");
		ServiceTracker tracker = new ServiceTracker(bundleContext, bundleContext.createFilter(filter.toString()), null);
		tracker.open();

		AbstractRefreshableApplicationContext appContext = (AbstractRefreshableApplicationContext) tracker.waitForService(1);

		assertNull("lifecycle application context does not exist", appContext);

		Resource extenderResource = getLocator().locateArtifact("org.springframework.osgi", "spring-osgi-extender",
			getSpringOsgiVersion());
		assertNotNull("Extender bundle resource", extenderResource);
		Bundle extenderBundle = bundleContext.installBundle(extenderResource.getURL().toExternalForm());
		assertNotNull("Extender bundle", extenderBundle);

		extenderBundle.start();

		tracker.open();

		appContext = (AbstractRefreshableApplicationContext) tracker.waitForService(60000);

		assertNotNull("lifecycle application context exists", appContext);

		assertNotSame("Guinea pig hasn't already been shutdown", "true",
			System.getProperty("org.springframework.osgi.iandt.lifecycle.GuineaPig.close"));

		assertEquals("Guinea pig started up", "true",
			System.getProperty("org.springframework.osgi.iandt.lifecycle.GuineaPig.startUp"));

	}
}
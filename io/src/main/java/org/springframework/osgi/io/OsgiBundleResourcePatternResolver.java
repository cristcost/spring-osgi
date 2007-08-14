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
 */
package org.springframework.osgi.io;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;

import org.osgi.framework.Bundle;
import org.springframework.core.CollectionFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * OSGi-aware subclass of PathMatchingResourcePatternResolver.
 * 
 * Can find resources in the bundle jar, bundle space or class space. See
 * {@link OsgiBundleResource} for more information.
 * 
 * @see Bundle
 * @see OsgiBundleResource
 * 
 * @author Costin Leau
 * 
 */
public class OsgiBundleResourcePatternResolver extends PathMatchingResourcePatternResolver {

    /**
     * Our own logger to protect against incompatible class changes.
     */
    protected static final Log logger = LogFactory.getLog(OsgiBundleResourcePatternResolver.class);

    /**
     * The bundle on which this resolver works on.
     */
    private Bundle bundle;


    public OsgiBundleResourcePatternResolver(Bundle bundle) {
        this(new OsgiBundleResourceLoader(bundle));
    }

    public OsgiBundleResourcePatternResolver(ResourceLoader resourceLoader) {
        super(resourceLoader);
        if (resourceLoader instanceof OsgiBundleResourceLoader) {
            this.bundle = ((OsgiBundleResourceLoader) resourceLoader).getBundle();

        }
    }

    public Resource[] getResources(String locationPattern) throws IOException {
        Assert.notNull(locationPattern, "Location pattern must not be null");
        int type = OsgiResourceUtils.getSearchType(locationPattern);

        // look for patterns
        if (getPathMatcher().isPattern(locationPattern)) {
            if (type == OsgiResourceUtils.PREFIX_CLASS_SPACE)
                throw new IllegalArgumentException("pattern matching is unsupported for class space lookups");
            return findPathMatchingResources(locationPattern, type);
        }
        else {
            // consider bundle-space which can return multiple URLs
            if (type == OsgiResourceUtils.PREFIX_NOT_SPECIFIED || type == OsgiResourceUtils.PREFIX_BUNDLE_SPACE) {
                OsgiBundleResource resource = new OsgiBundleResource(bundle, locationPattern);
                URL[] urls = resource.getAllUrlsFromBundleSpace(locationPattern);
                return OsgiResourceUtils.convertURLArraytoResourceArray(urls);
            }

            else if (type == OsgiResourceUtils.PREFIX_CLASS_SPACE) {
                // remove prefix
                String location = OsgiResourceUtils.stripPrefix(locationPattern);
                return OsgiResourceUtils.convertURLEnumerationToResourceArray(bundle.getResources(location));
            }
            else {
                // otherwise return only one
                return new Resource[] { getResourceLoader().getResource(locationPattern) };
            }
        }
    }

    /**
     * Override it to pass in the searchType parameter.
     */
    protected Resource[] findPathMatchingResources(String locationPattern, int searchType) throws IOException {
        String rootDirPath = determineRootDir(locationPattern);
        String subPattern = locationPattern.substring(rootDirPath.length());
        Resource[] rootDirResources = getResources(rootDirPath);

        Set result = CollectionFactory.createLinkedSetIfPossible(16);
        for (int i = 0; i < rootDirResources.length; i++) {
            Resource rootDirResource = rootDirResources[i];
            if (isJarResource(rootDirResource)) {
                result.addAll(doFindPathMatchingJarResources(rootDirResource, subPattern));
            }
            else {
                result.addAll(doFindPathMatchingFileResources(rootDirResource, subPattern, searchType));
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Resolved location pattern [" + locationPattern + "] to resources " + result);
        }
        return (Resource[]) result.toArray(new Resource[result.size()]);
    }

    /**
     * Based on the search type, use the approapriate method
     *
     * @see OsgiBundleResource#BUNDLE_URL_PREFIX
     * @see org.springframework.core.io.support.PathMatchingResourcePatternResolver#getResources(java.lang.String)
     */

    protected Set doFindPathMatchingFileResources(Resource rootDirResource, String subPattern, int searchType)
            throws IOException {

        String rootPath = null;

        if (rootDirResource instanceof OsgiBundleResource) {
            OsgiBundleResource bundleResource = (OsgiBundleResource) rootDirResource;
            rootPath = bundleResource.getPath();
            searchType = bundleResource.getSearchType();
        }
        else if (rootDirResource instanceof UrlResource) {
            rootPath = rootDirResource.getURL().getPath();
        }

        if (rootPath != null) {
            String cleanPath = OsgiResourceUtils.stripPrefix(rootPath);
            String fullPattern = cleanPath + subPattern;
            Set result = CollectionFactory.createLinkedSetIfPossible(16);
            doRetrieveMatchingBundleEntries(bundle, fullPattern, cleanPath, result, searchType);
            return result;
        }
        else {
            return super.doFindPathMatchingFileResources(rootDirResource, subPattern);
        }
    }

    /**
     * Seach each level inside the bundle for entries based on the search
     * strategy chosen.
     *
     * @param bundle the bundle to do the lookup
     * @param fullPattern matching pattern
     * @param dir directory inside the bundle
     * @param result set of results (used to concatenate matching sub dirs)
     * @param searchType the search strategy to use
     * @throws IOException
     */
    protected void doRetrieveMatchingBundleEntries(Bundle bundle, String fullPattern, String dir, Set result,
                                                   int searchType) throws IOException {

        Enumeration candidates;

        switch (searchType) {
        case OsgiResourceUtils.PREFIX_NOT_SPECIFIED:
        case OsgiResourceUtils.PREFIX_BUNDLE_SPACE:
            // returns an enumeration of URLs
            candidates = bundle.findEntries(dir, null, false);
            break;
        case OsgiResourceUtils.PREFIX_BUNDLE_JAR:
            // returns an enumeration of Strings
            candidates = bundle.getEntryPaths(dir);
            break;
        case OsgiResourceUtils.PREFIX_CLASS_SPACE:
            // returns an enumeration of URLs
            throw new IllegalArgumentException("class space does not support pattern matching");

        default:
            throw new IllegalArgumentException("unknown searchType " + searchType);
        }

        // entries are relative to the root path - miss the leading /
        if (candidates != null) {
            boolean dirDepthNotFixed = (fullPattern.indexOf("**") != -1);
            while (candidates.hasMoreElements()) {

                Object path = candidates.nextElement();
                String currPath;

                if (path instanceof String)
                    currPath = handleString((String) path);
                else
                    currPath = handleURL((URL) path);

                if (!currPath.startsWith(dir)) {
                    // Returned resource path does not start with relative
                    // directory:
                    // assuming absolute path returned -> strip absolute path.
                    int dirIndex = currPath.indexOf(dir);
                    if (dirIndex != -1) {
                        currPath = currPath.substring(dirIndex);
                    }
                }
                if (currPath.endsWith("/")
                        && (dirDepthNotFixed || StringUtils.countOccurrencesOf(currPath, "/") < StringUtils.countOccurrencesOf(
                            fullPattern, "/"))) {
                    // Search subdirectories recursively: we manually get the
                    // folders on only one level

                    doRetrieveMatchingBundleEntries(bundle, fullPattern, currPath, result, searchType);
                }
                if (getPathMatcher().match(fullPattern, currPath)) {
                    if (path instanceof URL)
                        result.add(new UrlResource((URL) path));
                    else
                        result.add(new OsgiBundleResource(bundle, currPath));

                }
            }
        }
    }

    /**
     * Handle candidates returned as URLs.
     * @param path
     * @return
     */
    protected String handleURL(URL path) {
        return path.getPath();
    }

    /**
     * Handle candidates returned as Strings.
     *
     * @param path
     * @return
     */
    protected String handleString(String path) {
        return "/" + path;
    }
}
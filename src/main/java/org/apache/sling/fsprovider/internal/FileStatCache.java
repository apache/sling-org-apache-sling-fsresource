/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.fsprovider.internal;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A cache that caches whether files exist or don't exist.
 */
public class FileStatCache {

    private static final Logger LOG = LoggerFactory.getLogger(FileStatCache.class);

    private final Map<String, FileStat> fileStates = new ConcurrentHashMap<>();

    private enum FileStat {
        EXISTING_DIRECTORY(true, true),
        EXISTING_FILE(true, false),
        NON_EXISTING(false, false);

        private final boolean exists;

        private final boolean isDirectory;

        public static FileStat compute(File file) {
            if (file.isDirectory()) {
                return EXISTING_DIRECTORY;
            } else if (file.exists()) {
                return EXISTING_FILE;
            } else {
                return NON_EXISTING;
            }
        }

        FileStat(boolean exists, boolean isDirectory) {
            this.exists = exists;
            this.isDirectory = isDirectory;
        }

        public boolean exists() {
            return exists;
        }

        public boolean isDirectory() {
            return isDirectory;
        }

        public boolean isFile() {
            return exists && !isDirectory;
        }


        @Override
        public String toString() {
            return exists() ? (isDirectory() ? "existing directory" : "existing file") : "non existing file";
        }
    }

    private final String providerFilePath;

    @SuppressWarnings("unchecked")
    FileStatCache(final File providerFile) {
        this.providerFilePath = providerFile.getPath();
    }

    // used by FileMonitor to notify for changes (added or deleted only)
    void clear() {
        fileStates.clear();
    }

    public boolean isDirectory(final File file) {
        return getFileState(file).isDirectory();
    }

    public boolean isFile(final File file) {
        return getFileState(file).isFile();
    }

    public boolean exists(final File file) {
        return getFileState(file).exists();
    }

    private FileStat getFileState(File file) {
        String path = relativePath(providerFilePath, file.getPath());
        if (StringUtils.isBlank(path)) {
            return FileStat.EXISTING_DIRECTORY;
        }
        FileStat fileStat = fileStates.get(path);
        if (fileStat == null) {
            if (!parentExists(path, file)) {
                LOG.trace("Does not exist (via parent): {}", path);
                return FileStat.NON_EXISTING;
            }
            fileStat = FileStat.compute(file);
            fileStates.put(path, fileStat);
            if (fileStat.exists()) {
                CacheStatistics.EXISTS_ACCESS.uncachedAccess(path);
            } else {
                CacheStatistics.NOT_EXISTS_ACCESS.uncachedAccess(path);
            }
        } else if (fileStat.exists()) {
            CacheStatistics.EXISTS_ACCESS.cachedAccess(path);
        } else {
            CacheStatistics.NOT_EXISTS_ACCESS.cachedAccess(path);
        }
        return fileStat;
    }

    private String relativePath(final String basePath, final String path) {
        final String suffix = StringUtils.removeStart(path, basePath);
        final String normalizedPath = StringUtils.replaceChars(suffix, '\\', '/');

        // If path starts with a '.', it is likely the descriptor file that
        // is a sibling of the providerFilePath, i.e. most likely a .json file.
        // We need to fix that up, as it is not a child of the providerFilePath.
        if (normalizedPath == null || normalizedPath.startsWith(".") && normalizedPath.length() > 2) {
            return "../" + ResourceUtil.getName(path);
        }

        return normalizedPath;
    }

    private FileStat getClosestCachedAncestorState(String path) {
        String ancestorPath = path;
        FileStat fileStat = null;
        do {
            String nextAncestorPath = StringUtils.substringBeforeLast(ancestorPath, "/");
            if (StringUtils.equals(ancestorPath, nextAncestorPath)) {
                break;
            }
            ancestorPath = nextAncestorPath;
            if (ancestorPath != null) {
                fileStat = fileStates.get(ancestorPath);
            }
        } while (ancestorPath != null && fileStat == null);
        return fileStat;
    }

    private boolean parentExists(final String path, final File file) {
        FileStat cachedAncestorState = getClosestCachedAncestorState(path);
        if (cachedAncestorState != null && !cachedAncestorState.exists()) {
            return false;
        } else {
            File parentFile = file.getParentFile();
            return parentFile == null || exists(parentFile);
        }
    }


    private enum CacheStatistics {
        EXISTS_ACCESS("Does exist (cached: {}/{}): {}", "Does exist (added to cache: {}/{}): {}"),
        NOT_EXISTS_ACCESS("Does not exist (cached: {}/{}): {}", "Does not exist (added to cache: {}/{}): {}");

        private final String cachedAccessLogStatement;

        private final String uncachedAccessLogStatement;

        private final AtomicLong cachedAccess;

        private final AtomicLong uncachedAccess;

        CacheStatistics(final String cachedAccessLogStatement, final String uncachedAccessLogStatement) {
            this.cachedAccessLogStatement = cachedAccessLogStatement;
            this.uncachedAccessLogStatement = uncachedAccessLogStatement;
            this.cachedAccess = new AtomicLong(0);
            this.uncachedAccess = new AtomicLong(0);
        }

        public void cachedAccess(String path) {
            long cached = cachedAccess.incrementAndGet();
            long uncached = uncachedAccess.get();
            log(cached, uncached, true, path);
        }

        public void uncachedAccess(String path) {
            long cached = cachedAccess.get();
            long uncached = uncachedAccess.incrementAndGet();
            log(cached, uncached, false, path);
        }

        private void log(long cached, long uncached, boolean logCached, String path) {
            if (LOG.isDebugEnabled()) {
                String statement = logCached ? cachedAccessLogStatement : uncachedAccessLogStatement;
                long all = uncached + uncached;
                long count = logCached ? cached : uncached;
                LOG.trace(statement, count, all, path);
                if (!LOG.isTraceEnabled() && all % 100_000 == 0) {
                    LOG.debug(statement, count, all, path);
                }
            }
        }
    }
}

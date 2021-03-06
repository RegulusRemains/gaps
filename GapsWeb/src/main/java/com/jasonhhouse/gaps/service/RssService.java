/*
 * Copyright 2020 Jason H House
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.jasonhhouse.gaps.service;

import com.jasonhhouse.gaps.PlexLibrary;
import com.jasonhhouse.gaps.PlexServer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RssService {

    private final IoService ioService;

    @Autowired
    public RssService(IoService ioService) {
        this.ioService = ioService;
    }

    @NotNull
    public Map<PlexLibrary, PlexServer> foundAnyRssFeeds() {
        Map<PlexLibrary, PlexServer> plexServerMap = new HashMap<>();
        List<PlexServer> plexServers = ioService.readPlexConfiguration();
        if (CollectionUtils.isEmpty(plexServers)) {
            return Collections.emptyMap();
        }

        for (PlexServer plexServer : plexServers) {
            if (CollectionUtils.isEmpty(plexServer.getPlexLibraries())) {
                continue;
            }

            for (PlexLibrary plexLibrary : plexServer.getPlexLibraries()) {
                if (ioService.doesRssFileExist(plexLibrary.getMachineIdentifier(), plexLibrary.getKey())) {
                    plexServerMap.put(plexLibrary, plexServer);
                }
            }
        }

        return plexServerMap;
    }
}

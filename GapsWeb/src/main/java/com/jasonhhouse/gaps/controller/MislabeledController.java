/*
 * Copyright 2020 Jason H House
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.jasonhhouse.gaps.controller;

import com.jasonhhouse.gaps.GapsService;
import com.jasonhhouse.gaps.Mislabeled;
import com.jasonhhouse.gaps.Pair;
import com.jasonhhouse.gaps.PlexQuery;
import com.jasonhhouse.gaps.service.MislabeledService;
import com.jasonhhouse.plex.MediaContainer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping(value = "/mislabeled")
public class MislabeledController {
    private static final Logger LOGGER = LoggerFactory.getLogger(MislabeledController.class);

    private final GapsService gapsService;
    private final PlexQuery plexQuery;
    private final MislabeledService mislabeledService;

    @Autowired
    public MislabeledController(GapsService gapsService, PlexQuery plexQuery, MislabeledService mislabeledService) {
        this.gapsService = gapsService;
        this.plexQuery = plexQuery;
        this.mislabeledService = mislabeledService;
    }

    @RequestMapping(method = RequestMethod.GET,
            produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView getMislabeled() {
        LOGGER.info("getMislabeled()");

        return new ModelAndView("mislabeled");
    }

    @RequestMapping(method = RequestMethod.GET,
            value = "/{machineIdentifier}/{key}/{percentage}")
    @ResponseBody
    public ResponseEntity<List<Mislabeled>> getMisMatched(@PathVariable("machineIdentifier") final String machineIdentifier, @PathVariable("key") final Integer key,
                                                          @PathVariable("percentage") final Double percentage) {
        LOGGER.info("getPlexMovies( " + machineIdentifier + ", " + key + ", " + percentage + " )");

        String url = generatePlexUrl(machineIdentifier, key);
        MediaContainer mediaContainer = plexQuery.findAllPlexVideos(url);
        List<Mislabeled> mislabeled = mislabeledService.findMatchPercentage(mediaContainer, percentage);

        return ResponseEntity.ok().body(mislabeled);
    }

    private String generatePlexUrl(String machineIdentifier, Integer key) {
        LOGGER.info("generatePlexUrl( " + machineIdentifier + ", " + key + " )");
        return gapsService
                .getPlexSearch()
                .getPlexServers()
                .stream()
                .filter(plexServer -> plexServer.getMachineIdentifier().equals(machineIdentifier))
                .map(plexServer -> plexServer
                        .getPlexLibraries()
                        .stream()
                        .filter(plexLibrary -> plexLibrary.getKey().equals(key))
                        .map(plexLibrary -> "http://" + plexServer.getAddress() + ":" + plexServer.getPort() + "/library/sections/" + plexLibrary.getKey() + "/all/?X-Plex-Token=" + plexServer.getPlexToken())
                        .findFirst().orElse(null))
                .findFirst()
                .orElse("");
    }
}
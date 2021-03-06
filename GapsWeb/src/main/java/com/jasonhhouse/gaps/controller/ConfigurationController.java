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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jasonhhouse.gaps.GapsService;
import com.jasonhhouse.gaps.Payload;
import com.jasonhhouse.gaps.PlexSearch;
import com.jasonhhouse.gaps.PlexServer;
import com.jasonhhouse.gaps.service.BindingErrorsService;
import com.jasonhhouse.gaps.service.IoService;
import com.jasonhhouse.gaps.service.PlexQueryImpl;
import com.jasonhhouse.gaps.service.TmdbService;
import java.io.IOException;
import java.util.List;
import javax.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping(value = "/configuration")
public class ConfigurationController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final BindingErrorsService bindingErrorsService;
    private final TmdbService tmdbService;
    private final SimpMessagingTemplate template;
    private final PlexQueryImpl plexQuery;
    private final GapsService gapsService;
    private final IoService ioService;

    public ConfigurationController(BindingErrorsService bindingErrorsService, TmdbService tmdbService, SimpMessagingTemplate template, PlexQueryImpl plexQuery, GapsService gapsService, IoService ioService) {
        this.bindingErrorsService = bindingErrorsService;
        this.tmdbService = tmdbService;
        this.template = template;
        this.plexQuery = plexQuery;
        this.gapsService = gapsService;
        this.ioService = ioService;
    }

    @RequestMapping(method = RequestMethod.GET,
            produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView getConfiguration() {
        LOGGER.info("getConfiguration()");

        try {
            PlexSearch plexSearch = ioService.readProperties();
            gapsService.updatePlexSearch(plexSearch);

            List<PlexServer> plexServers = ioService.readPlexConfiguration();
            gapsService.getPlexSearch().getPlexServers().addAll(plexServers);
        } catch (IOException e) {
            LOGGER.warn("Failed to read gaps properties.", e);
        }

        ModelAndView modelAndView = new ModelAndView("configuration");
        modelAndView.addObject("plexSearch", gapsService.getPlexSearch());
        return modelAndView;
    }

    @RequestMapping(value = "/add/plex",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseStatus(value = HttpStatus.OK)
    public void postAddPlexServer(@Valid final PlexServer plexServer, BindingResult bindingResult) {
        LOGGER.info("postAddPlexServer( " + plexServer + " )");

/*
        if (bindingErrorsService.hasBindingErrors(bindingResult)) {
            LOGGER.error("Error binding PlexServer object: " + plexServer);
        }
*/
        try {
            PlexSearch plexSearch = ioService.readProperties();
            gapsService.updatePlexSearch(plexSearch);

            plexQuery.queryPlexServer(plexServer);
            Payload payload = plexQuery.getLibraries(plexServer);

            if (payload.getCode() == Payload.PLEX_LIBRARIES_FOUND.getCode()) {
                int initialCount = gapsService.getPlexSearch().getPlexServers().size();
                gapsService.getPlexSearch().addPlexServer(plexServer);
                if (gapsService.getPlexSearch().getPlexServers().size() == initialCount) {
                    template.convertAndSend("/configuration/plex/duplicate", Payload.DUPLICATE_PLEX_LIBRARY);
                } else {
                    ioService.writePlexConfiguration(gapsService.getPlexSearch().getPlexServers());
                    template.convertAndSend("/configuration/plex/complete", payload.setExtras(plexServer));
                }
            } else {
                template.convertAndSend("/configuration/plex/complete", payload);
            }
        } catch (Exception e) {
            LOGGER.error("Could not add plex server", e);
            template.convertAndSend("/configuration/plex/complete", Payload.UNKNOWN_ERROR);
        }
    }

    @RequestMapping(value = "/test/plex",
            method = RequestMethod.PUT,
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Payload> putTestPlexServer(@Valid final PlexServer plexServer, BindingResult bindingResult) {
        LOGGER.info("putTestPlexServer( " + plexServer + " )");

        /*if (bindingErrorsService.hasBindingErrors(bindingResult)) {
            LOGGER.error("Error binding PlexServer object: " + plexServer);
        }*/

        Payload payload;
        try {
            payload = plexQuery.queryPlexServer(plexServer);
        } catch (Exception e) {
            payload = Payload.UNKNOWN_ERROR;
        }

        return ResponseEntity.ok().body(payload);
    }

    @RequestMapping(value = "/test/plex/{machineIdentifier}",
            method = RequestMethod.PUT,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<String> putTestPlexServerByMachineId(@PathVariable("machineIdentifier") final String machineIdentifier) {
        LOGGER.info("putTestPlexServerByMachineId( " + machineIdentifier + " )");

        try {
            PlexSearch plexSearch = ioService.readProperties();
            gapsService.updatePlexSearch(plexSearch);

            ObjectNode objectNode = objectMapper.createObjectNode();
            PlexServer returnedPlexServer = gapsService.getPlexSearch().getPlexServers().stream().filter(plexServer -> plexServer.getMachineIdentifier().equals(machineIdentifier)).findFirst().orElse(new PlexServer());

            if (StringUtils.isEmpty(returnedPlexServer.getMachineIdentifier())) {
                //Failed to find and delete
                objectNode.put("success", false);
            } else {
                plexQuery.queryPlexServer(returnedPlexServer);
                objectNode.put("success", true);
            }

            return ResponseEntity.ok().body(objectNode.toString());
        } catch (IOException e) {
            LOGGER.error("Failed to read PlexSearch", e);
            return ResponseEntity.status(500).body("Failed to read PlexSearch");
        }
    }

    @RequestMapping(value = "/delete/plex/{machineIdentifier}",
            method = RequestMethod.DELETE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<String> deletePlexServer(@PathVariable("machineIdentifier") final String machineIdentifier) {
        LOGGER.info("deletePlexServer( " + machineIdentifier + " )");

        try {
            PlexSearch plexSearch = ioService.readProperties();
            gapsService.updatePlexSearch(plexSearch);

            ObjectNode objectNode = objectMapper.createObjectNode();
            PlexServer returnedPlexServer = gapsService.getPlexSearch().getPlexServers().stream().filter(plexServer -> plexServer.getMachineIdentifier().equals(machineIdentifier)).findFirst().orElse(new PlexServer());
            if (StringUtils.isEmpty(returnedPlexServer.getMachineIdentifier())) {
                //Failed to find and delete
                objectNode.put("success", false);
            } else {
                gapsService.getPlexSearch().getPlexServers().remove(returnedPlexServer);
                ioService.writePlexConfiguration(gapsService.getPlexSearch().getPlexServers());
                objectNode.put("success", true);
            }

            return ResponseEntity.ok().body(objectNode.toString());
        } catch (IOException e) {
            LOGGER.error("Failed to read PlexSearch", e);
            return ResponseEntity.status(500).body("Failed to read PlexSearch");
        }
    }

    @RequestMapping(value = "/test/tmdbKey/{tmdbKey}",
            method = RequestMethod.PUT,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Payload> postTestTmdbKey(@PathVariable("tmdbKey") final String tmdbKey) {
        LOGGER.info("postTestTmdbKey( " + tmdbKey + " )");

        Payload payload = tmdbService.testTmdbKey(tmdbKey).setExtras("tmdbKey:" + tmdbKey);
        ;
        return ResponseEntity.ok().body(payload);
    }

    @RequestMapping(value = "/save/tmdbKey/{tmdbKey}",
            method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Payload> postSaveTmdbKey(@PathVariable("tmdbKey") final String tmdbKey) {
        LOGGER.info("postSaveTmdbKey( " + tmdbKey + " )");

        Payload payload;
        try {
            PlexSearch plexSearch = ioService.readProperties();
            gapsService.updatePlexSearch(plexSearch);
            gapsService.getPlexSearch().setMovieDbApiKey(tmdbKey);

            ioService.writeProperties(gapsService.getPlexSearch());
            payload = Payload.TMDB_KEY_SAVE_SUCCESSFUL.setExtras("tmdbKey:" + tmdbKey);
        } catch (IOException e) {
            payload = Payload.TMDB_KEY_SAVE_UNSUCCESSFUL.setExtras("tmdbKey:" + tmdbKey);
            LOGGER.warn("Could not save TMDB key");
        }

        return ResponseEntity.ok().body(payload);
    }

}

package com.janrain.backplane.server;

import com.janrain.backplane.server.config.BackplaneConfig;
import com.janrain.simpledb.SuperSimpleDB;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.inject.Inject;

/**
 * Backplane API implementation.
 *
 * @author Johnny Bufu
 */
@Controller
@RequestMapping(value="/v1/bus/*")
@SuppressWarnings({"UnusedDeclaration"})
public class BackplaneController {

    // - PUBLIC

    @RequestMapping(value = "/{bus}", method = RequestMethod.GET)
    public String getBusMessages(@PathVariable String bus,
                                 @RequestParam(value = "since", required = false) String since) {

        // todo: auth -> filter

    }

    @RequestMapping(value = "/{bus}/channel/{channel}", method = RequestMethod.GET)
    public String getChannelMessages(@PathVariable String bus,
                                     @PathVariable String channel,
                                     @RequestParam(value = "since", required = false) String since) {

    }

    @RequestMapping(value = "/{bus}/channel/{channel}", method = RequestMethod.POST)
    public String postToChannel(@PathVariable String bus, @PathVariable String channel) {
        // todo: auth -> filter

    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(BackplaneController.class);

    @Inject
    private BackplaneConfig bpConfig;

    @Inject
    private SuperSimpleDB simpleDb;


}

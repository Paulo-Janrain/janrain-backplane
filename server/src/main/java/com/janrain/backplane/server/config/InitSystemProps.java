/*
 * Copyright 2011 Janrain, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.janrain.backplane.server.config;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.Logger;

import javax.naming.InitialContext;

/**
 * Verifies required parameters are included as system properties.  If the parameters do not exist as
 * system properties, as would be provided via -D command line declarations, the application context
 * is consulted for the values.
 *
 * @author Tom Raney
 */

public class InitSystemProps {

    // - PUBLIC

    public static final String AWS_ACCESS_KEY_ID    = "AWS_ACCESS_KEY_ID";
    public static final String AWS_SECRET_KEY       = "AWS_SECRET_KEY";
    public static final String BP_AWS_INSTANCE_ID   = "PARAM1";
    public static final String BP_EMAIL_DOMAIN      = "PARAM2";
    public static final String BP_EMAIL_TARGET      = "PARAM3";

    // - PRIVATE
    private static final Logger logger = Logger.getLogger(InitSystemProps.class);

    private InitSystemProps() {
        logger.info("Verifying external parameters required to start");
        load(BP_AWS_INSTANCE_ID);
        load(BP_EMAIL_DOMAIN );
        load(BP_EMAIL_TARGET);
        load(AWS_ACCESS_KEY_ID);
        load(AWS_SECRET_KEY);

        logger.info("Creating SMTPAppender for log4j");
        //Create the Log4j smtp appender - which must be created programmatically to
        //be sure the parameters are loaded first.
        org.apache.log4j.net.SMTPAppender appender = new org.apache.log4j.net.SMTPAppender();
        appender.setName("mail");
        appender.setBufferSize(10);
        appender.setFrom(System.getProperty("PARAM1") + "@" + System.getProperty("PARAM2"));
        appender.setTo(System.getProperty("PARAM3"));
        appender.setSubject("Backplane Errors");
        appender.setLayout(new EnhancedPatternLayout("%d %-5p [%t]: %c:%L - %m%n"));
        appender.activateOptions();

        Logger.getRootLogger().addAppender(appender);

    }

    private void load(String paramName) {
        String result = System.getProperty(paramName);
        if (StringUtils.isBlank(result)) {
            try {
                javax.naming.Context initCtx = new InitialContext();
                result = (String) initCtx.lookup("java:comp/env/" + paramName);
                System.setProperty(paramName, result);
                logger.info("Parameter " + paramName + " fetched from context and inserted as system property");
            } catch (Exception e) {
                //continue
                logger.info("An error occurred trying to locate required parameter " + paramName + " => " + e.getMessage());
            }
        } else {
            logger.info("Parameter " + paramName + " exists as a system property");
        }
    }

}

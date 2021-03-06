/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.plugin.common;

import javax.inject.Inject;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;

/**
 * The default implementation for connecting to a running WildFly instance
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author Stuart Douglas
 */
public abstract class AbstractServerConnection extends AbstractMojo {

    public static final String DEBUG_MESSAGE_NO_CREDS = "No username and password in settings.xml file - falling back to CLI entry";
    public static final String DEBUG_MESSAGE_NO_ID = "No <id> element was found in the POM - Getting credentials from CLI entry";
    public static final String DEBUG_MESSAGE_NO_SERVER_SECTION = "No <server> section was found for the specified id";
    public static final String DEBUG_MESSAGE_NO_SETTINGS_FILE = "No settings.xml file was found in this Mojo's execution context";
    public static final String DEBUG_MESSAGE_POM_HAS_CREDS = "Getting credentials from the POM";
    public static final String DEBUG_MESSAGE_SETTINGS_HAS_CREDS = "Found username and password in the settings.xml file";
    public static final String DEBUG_MESSAGE_SETTINGS_HAS_ID = "Found the server's id in the settings.xml file";

    /**
     * The protocol used to connect to the server for management.
     */
    @Parameter(property = PropertyNames.PROTOCOL)
    private String protocol;

    /**
     * Specifies the host name of the server where the deployment plan should be executed.
     */
    @Parameter(defaultValue = "localhost", property = PropertyNames.HOSTNAME)
    private String hostname;

    /**
     * Specifies the port number the server is listening on.
     */
    @Parameter(defaultValue = "9990", property = PropertyNames.PORT)
    private int port;

    /**
     * Specifies the id of the server if the username and password is to be
     * retrieved from the settings.xml file
     */
    @Parameter(property = PropertyNames.ID)
    private String id;

    /**
     * Provides a reference to the settings file.
     */
    @Parameter(property = "settings", readonly = true, required = true, defaultValue = "${settings}")
    private Settings settings;

    /**
     * Specifies the username to use if prompted to authenticate by the server.
     * <p/>
     * If no username is specified and the server requests authentication the user
     * will be prompted to supply the username,
     */
    @Parameter(property = PropertyNames.USERNAME)
    private String username;

    /**
     * Specifies the password to use if prompted to authenticate by the server.
     * <p/>
     * If no password is specified and the server requests authentication the user
     * will be prompted to supply the password,
     */
    @Parameter(property = PropertyNames.PASSWORD)
    private String password;

    /**
     * The timeout, in seconds, to wait for a management connection.
     */
    @Parameter(property = PropertyNames.TIMEOUT, defaultValue = "60")
    private int timeout;

    @Inject
    private SettingsDecrypter settingsDecrypter;

    private ManagementClientConfiguration clientConfiguration;

    /**
     * The goal of the deployment.
     *
     * @return the goal of the deployment.
     */
    public abstract String goal();

    /**
     * Creates a new client.
     *
     * @return the client
     */
    protected ManagementClient createClient() {
        return new ManagementClient(getClientConfiguration());
    }

    /**
     * Gets a client configuration used to create a new {@link ManagementClient}.
     *
     * @return the configuration to use
     */
    protected synchronized ManagementClientConfiguration getClientConfiguration() {
        if (clientConfiguration == null) {
            final Log log = getLog();
            String username = this.username;
            String password = this.password;
            if (username == null && password == null) {
                if (id != null) {
                    if (settings != null) {
                        Server server = settings.getServer(id);
                        if (server != null) {
                            log.debug(DEBUG_MESSAGE_SETTINGS_HAS_ID);
                            password = decrypt(server);
                            username = server.getUsername();
                            if (username != null && password != null) {
                                log.debug(DEBUG_MESSAGE_SETTINGS_HAS_CREDS);
                            } else {
                                log.debug(DEBUG_MESSAGE_NO_CREDS);
                            }
                        } else {
                            log.debug(DEBUG_MESSAGE_NO_SERVER_SECTION);
                        }
                    } else {
                        log.debug(DEBUG_MESSAGE_NO_SETTINGS_FILE);
                    }
                } else {
                    log.debug(DEBUG_MESSAGE_NO_ID);
                }
            } else {
                log.debug(DEBUG_MESSAGE_POM_HAS_CREDS);
            }
            final String u = username;
            final String p = password;
            clientConfiguration = new ManagementClientConfiguration(
                    new ModelControllerClientConfiguration.Builder()
                            .setProtocol(protocol)
                            .setHostName(hostname)
                            .setPort(port)
                            .setConnectionTimeout(timeout * 1000)
                            .setHandler(new ClientCallbackHandler(u, p, log))
                            .build()) {
                @Override
                public String getUsername() {
                    return u;
                }

                @Override
                public String getPassword() {
                    return p;
                }
            };
        }
        return clientConfiguration;
    }

    private String decrypt(final Server server) {
        SettingsDecryptionResult decrypt = settingsDecrypter.decrypt(new DefaultSettingsDecryptionRequest(server));
        return decrypt.getServer().getPassword();
    }
}

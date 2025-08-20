/*
 * This file is part of HuskHomes, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.william278.huskhomes.network;

import io.nats.client.*;
import net.william278.huskhomes.HuskHomes;
import net.william278.huskhomes.user.OnlineUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Level;

import static net.william278.huskhomes.config.Settings.CrossServerSettings.NatsSettings;

/**
 * NATS message broker implementation
 */
public class NatsBroker extends PluginMessageBroker {
    private Connection nats;
    private Dispatcher dispatcher;
    private final String channel;

    public NatsBroker(@NotNull HuskHomes plugin) {
        super(plugin);
        this.channel = "huskhomes:" + getSubChannelId();
    }

    @Override
    public void initialize() throws IllegalStateException {
        // Initialize plugin message channels
        super.initialize();

        // Connect to NATS
        try {
            final NatsSettings settings = plugin.getSettings().getCrossServer().getNats();
            Options.Builder builder = new Options.Builder()
                    .server(settings.getUrl())
                    .connectionTimeout(Duration.ofSeconds(5))
                    .connectionName("HuskHomes");

            // Add credentials if provided
            if (!settings.getUsername().isEmpty() && !settings.getPassword().isEmpty()) {
                builder.userInfo(settings.getUsername().toCharArray(), settings.getPassword().toCharArray());
            }

            // Connect to NATS
            nats = Nats.connect(builder.build());
            dispatcher = nats.createDispatcher(msg -> {
                final String messageData = new String(msg.getData(), StandardCharsets.UTF_8);
                plugin.runAsync(() -> {
                    try {
                        final Message message = plugin.getGson().fromJson(messageData, Message.class);
                        if (message.getTargetType() == Message.TargetType.PLAYER) {
                            plugin.getOnlineUsers().stream()
                                    .filter(online -> message.getTarget().equals(Message.TARGET_ALL)
                                            || online.getName().equals(message.getTarget()))
                                    .forEach(receiver -> handle(receiver, message));
                            return;
                        }

                        if (message.getTarget().equals(plugin.getServerName())
                                || message.getTarget().equals(Message.TARGET_ALL)) {
                            handle(null, message);
                        }
                    } catch (Exception e) {
                        plugin.log(Level.WARNING, "Failed to handle message from NATS: " + e.getMessage());
                    }
                });
            });
            
            // Subscribe to channel
            dispatcher.subscribe(channel);
            
            plugin.log(Level.INFO, "Successfully connected to NATS server");
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed to establish connection with NATS server. " +
                    "Please check the supplied credentials in the config file", e);
        }
    }

    @Override
    public void close() {
        if (nats != null) {
            try {
                nats.close();
            } catch (InterruptedException e) {
                plugin.log(Level.WARNING, "Error closing NATS connection", e);
            }
        }
        super.close();
    }

    @Override
    protected void send(@NotNull Message message, @Nullable OnlineUser sender) {
        if (nats != null && nats.getStatus() == Connection.Status.CONNECTED) {
            try {
                final String messageJson = plugin.getGson().toJson(message);
                nats.publish(channel, messageJson.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                plugin.log(Level.WARNING, "Failed to publish message to NATS", e);
            }
        }
    }
}

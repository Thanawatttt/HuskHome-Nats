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

package net.william278.huskhomes.command;

import net.william278.huskhomes.HuskHomes;
import net.william278.huskhomes.network.Broker;
import net.william278.huskhomes.network.Message;
import net.william278.huskhomes.network.NatsBroker;
import net.william278.huskhomes.user.CommandUser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TestNatsCommand extends Command {

    public TestNatsCommand(@NotNull HuskHomes plugin) {
        super(List.of("testnats"), "", plugin);
        setOperatorCommand(true);
    }

    @Override
    public void execute(@NotNull CommandUser executor, @NotNull String[] args) {
        if (!plugin.getSettings().getCrossServer().isEnabled()) {
            plugin.getLocales().getLocale("error_cross_server_disabled")
                    .ifPresent(executor::sendMessage);
            return;
        }

        if (plugin.getSettings().getCrossServer().getBrokerType() != Broker.Type.NATS) {
            plugin.getLocales().getLocale("error_no_nats")
                    .ifPresent(executor::sendMessage);
            return;
        }

        plugin.getBroker().ifPresent(broker -> {
            if (!(broker instanceof NatsBroker)) {
                plugin.getLocales().getLocale("error_no_nats")
                        .ifPresent(executor::sendMessage);
                return;
            }
            
            // Test NATS connectivity by sending a ping message
            try {
                Message.builder()
                        .type(Message.MessageType.UPDATE_USER_LIST)
                        .target(Message.TARGET_ALL, Message.TargetType.SERVER)
                        .build()
                        .send(broker, null);
                plugin.getLocales().getLocale("nats_test_success")
                        .ifPresent(executor::sendMessage);
            } catch (Exception e) {
                plugin.getLocales().getLocale("nats_test_failed", e.getMessage())
                        .ifPresent(executor::sendMessage);
            }
        });
    }
}

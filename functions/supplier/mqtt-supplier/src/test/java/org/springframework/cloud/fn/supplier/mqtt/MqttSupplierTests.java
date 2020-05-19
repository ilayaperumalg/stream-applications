/*
 * Copyright 2017-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.fn.supplier.mqtt;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Mqtt Supplier.
 *
 * @author Janne Valkealahti
 * @author Gary Russell
 * @author Soby Chacko
 *
 */
@SpringBootTest(properties = {"mqtt.supplier.topics=test,fake", "mqtt.supplier.qos=0,0"})
@DirtiesContext
public class MqttSupplierTests {

	static {
		Consumer<CreateContainerCmd> cmd = e -> e.withPortBindings(new PortBinding(Ports.Binding.bindPort(1883), new ExposedPort(1883)));
		GenericContainer mosquitto = new GenericContainer("eclipse-mosquitto")
				.withExposedPorts(1883)
				.withCreateContainerCmdModifier(cmd);
		mosquitto.start();
	}

	@Autowired
	private Supplier<Flux<Message<?>>> mqttSupplier;

	@Autowired
	private MessageHandler mqttOutbound;

	@Test
	public void testBasicFlow() {

		mqttOutbound.handleMessage(MessageBuilder.withPayload("hello").build());

		final Flux<Message<?>> messageFlux = mqttSupplier.get();

		StepVerifier.create(messageFlux)
				.assertNext((message) -> {
							assertThat(message.getPayload())
									.isEqualTo("hello");
						}
				)
				.thenCancel()
				.verify();
	}

	@SpringBootApplication
	static class TestApplication {

		@Autowired
		private MqttPahoClientFactory mqttClientFactory;

		@Bean
		public MessageHandler mqttOutbound() {
			MqttPahoMessageHandler messageHandler = new MqttPahoMessageHandler("test", mqttClientFactory);
			messageHandler.setAsync(true);
			messageHandler.setDefaultTopic("test");
			messageHandler.setConverter(producerConverter());
			return messageHandler;
		}

		@Bean
		public DefaultPahoMessageConverter producerConverter() {
			return new DefaultPahoMessageConverter(1, true, "UTF-8");
		}
	}
}

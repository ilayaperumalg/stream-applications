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

package org.springframework.cloud.fn.consumer.mqtt;

import java.util.function.Consumer;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "mqtt.consumer.topic=test")
@DirtiesContext
public class MqttConsumerTests {

	static {
		Consumer<CreateContainerCmd> cmd = e -> e.withPortBindings(new PortBinding(Ports.Binding.bindPort(1883), new ExposedPort(1883)));
		GenericContainer mosquitto = new GenericContainer("eclipse-mosquitto")
				.withExposedPorts(1883)
				.withCreateContainerCmdModifier(cmd);
		mosquitto.start();
	}

	@Autowired
	private Consumer<Message<?>> mqttConsumer;

	@Autowired
	protected QueueChannel queue;

	@Test
	public void testMqttConsumer() {
		this.mqttConsumer.accept(MessageBuilder.withPayload("hello").build());
		Message<?> in = this.queue.receive(10000);
		assertThat(in).isNotNull();
		assertThat(in.getPayload()).isEqualTo("hello");
	}

	@SpringBootApplication
	static class TestApplication {

		@Autowired
		private MqttPahoClientFactory mqttClientFactory;

		@Bean
		public MqttPahoMessageDrivenChannelAdapter mqttInbound(BeanFactory beanFactory) {
			MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter("test",
					mqttClientFactory, "test");
			adapter.setQos(0);
			adapter.setConverter(pahoMessageConverter(beanFactory));
			adapter.setOutputChannelName("queue");
			return adapter;
		}

		public DefaultPahoMessageConverter pahoMessageConverter(BeanFactory beanFactory) {
			DefaultPahoMessageConverter converter = new DefaultPahoMessageConverter(1, true, "UTF-8");
			converter.setPayloadAsBytes(false);
			converter.setBeanFactory(beanFactory);
			return converter;
		}

		@Bean
		public QueueChannel queue() {
			return new QueueChannel();
		}
	}
}

/*
 * Copyright 2020-2020 the original author or authors.
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

package org.springframework.cloud.stream.app.source.mqtt;

import java.util.function.Consumer;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.fn.supplier.mqtt.MqttSupplierConfiguration;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class MqttSourceTests {

	static {
		Consumer<CreateContainerCmd> cmd = e -> e.withPortBindings(new PortBinding(Ports.Binding.bindPort(1883), new ExposedPort(1883)));
		GenericContainer mosquitto = new GenericContainer("eclipse-mosquitto")
				.withExposedPorts(1883)
				.withCreateContainerCmdModifier(cmd);
		mosquitto.start();
	}

	@Test
	public void testMqttSource() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
				TestChannelBinderConfiguration
						.getCompleteConfiguration(MqttSourceTestConfiguration.class))
				.web(WebApplicationType.NONE)
				.run("--mqtt.supplier.topics=test,fake", "--mqtt.supplier.qos=0,0")) {

			final MessageHandler mqttOutbound = context.getBean("mqttOutbound", MessageHandler.class);
			mqttOutbound.handleMessage(MessageBuilder.withPayload("hello").build());

			OutputDestination target = context.getBean(OutputDestination.class);
			Message<byte[]> sourceMessage = target.receive(10000);

			final String actual = new String(sourceMessage.getPayload());
			assertThat(actual).isEqualTo("hello");
		}
	}

	@EnableAutoConfiguration
	@Import(MqttSupplierConfiguration.class)
	public static class MqttSourceTestConfiguration {

		@Autowired
		private MqttPahoClientFactory mqttClientFactory;

		@Bean
		public MessageHandler mqttOutbound(BeanFactory beanFactory) {
			MqttPahoMessageHandler messageHandler = new MqttPahoMessageHandler("test", mqttClientFactory);
			messageHandler.setAsync(true);
			messageHandler.setDefaultTopic("test");
			messageHandler.setConverter(pahoMessageConverter1(beanFactory));
			return messageHandler;
		}

		public DefaultPahoMessageConverter pahoMessageConverter1(BeanFactory beanFactory) {
			final DefaultPahoMessageConverter pahoMessageConverter = new DefaultPahoMessageConverter(1, true, "UTF-8");
			pahoMessageConverter.setBeanFactory(beanFactory);
			return pahoMessageConverter;
		}
	}
}

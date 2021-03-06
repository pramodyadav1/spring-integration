/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.channel.reactive;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.isOneOf;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.MessageChannelReactiveUtils;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.channel.ReactiveChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import reactor.core.publisher.Flux;

/**
 * @author Artem Bilan
 * @since 5.0
 */
@RunWith(SpringRunner.class)
@DirtiesContext
public class ReactiveChannelTests {

	@Autowired
	private MessageChannel reactiveChannel;

	@Autowired
	private MessageChannel queueChannel;

	@Test
	@SuppressWarnings("unchecked")
	public void testReactiveMessageChannel() throws InterruptedException {
		QueueChannel replyChannel = new QueueChannel();

		for (int i = 0; i < 10; i++) {
			try {
				this.reactiveChannel.send(MessageBuilder.withPayload(i).setReplyChannel(replyChannel).build());
			}
			catch (Exception e) {
				assertThat(e, instanceOf(MessageDeliveryException.class));
				assertThat(e.getCause().getCause(), instanceOf(MessageHandlingException.class));
				assertThat(e.getCause().getCause().getCause(), instanceOf(IllegalStateException.class));
				assertThat(e.getMessage(), containsString("intentional"));
			}
		}

		for (int i = 0; i < 9; i++) {
			Message<?> receive = replyChannel.receive(10000);
			assertNotNull(receive);
			assertThat(receive.getPayload(), isOneOf("0", "1", "2", "3", "4", "6", "7", "8", "9"));
		}
	}

	@Test
	public void testMessageChannelReactiveAdaptation() throws InterruptedException {
		CountDownLatch done = new CountDownLatch(2);
		List<String> results = new ArrayList<>();

		Flux.from(MessageChannelReactiveUtils.<String>toPublisher(this.queueChannel))
				.map(Message::getPayload)
				.map(String::toUpperCase)
				.doOnNext(results::add)
				.subscribe(v -> done.countDown());

		this.queueChannel.send(new GenericMessage<>("foo"));
		this.queueChannel.send(new GenericMessage<>("bar"));

		assertTrue(done.await(10, TimeUnit.SECONDS));
		assertThat(results, contains("FOO", "BAR"));
	}

	@Configuration
	@EnableIntegration
	public static class TestConfiguration {

		@Bean
		public MessageChannel reactiveChannel() {
			return new ReactiveChannel();
		}

		@ServiceActivator(inputChannel = "reactiveChannel")
		public String handle(int payload) {
			if (payload == 5) {
				throw new IllegalStateException("intentional");
			}
			return "" + payload;
		}

		@Bean
		public MessageChannel queueChannel() {
			return new QueueChannel();
		}

	}

}

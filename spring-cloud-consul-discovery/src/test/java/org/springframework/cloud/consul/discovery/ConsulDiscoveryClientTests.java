/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.consul.discovery;

import java.util.List;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.consul.test.ConsulTestcontainers;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Spencer Gibb
 * @author Joe Athman
 * @author Chen Zhiguo
 */
@RunWith(SpringRunner.class)
@SpringBootTest(properties = { "spring.application.name=testConsulDiscovery",
		"spring.cloud.consul.discovery.prefer-ip-address=true", "spring.cloud.consul.discovery.metadata[foo]=bar" },
		classes = ConsulDiscoveryClientTests.MyTestConfig.class, webEnvironment = RANDOM_PORT)
@ContextConfiguration(initializers = ConsulTestcontainers.class)
public class ConsulDiscoveryClientTests {

	@Autowired
	private ConsulDiscoveryClient discoveryClient;

	@Autowired
	private ConsulClient consulClient;

	@Autowired
	private ConsulDiscoveryProperties properties;

	@Test
	public void getInstancesForServiceWorks() {
		List<ServiceInstance> instances = this.discoveryClient.getInstances("testConsulDiscovery");
		assertThat(instances).as("instances was null").isNotNull();
		assertThat(instances.isEmpty()).as("instances was empty").isFalse();

		ServiceInstance instance = instances.get(0);
		assertThat(instance.isSecure()).as("instance was secure (https)").isFalse();
		assertIpAddress(instance);
		assertThat(instance.getMetadata()).containsEntry("foo", "bar");
	}

	@Test
	public void getInstancesForServiceRespectsQueryParams() {
		Response<List<String>> catalogDatacenters = this.consulClient.getCatalogDatacenters();

		List<String> dataCenterList = catalogDatacenters.getValue();
		assertThat(dataCenterList.isEmpty()).as("no data centers found").isFalse();
		List<ServiceInstance> instances = this.discoveryClient.getInstances("testConsulDiscovery",
				new QueryParams(dataCenterList.get(0)));
		assertThat(instances.isEmpty()).as("instances was empty").isFalse();

		ServiceInstance instance = instances.get(0);
		assertIpAddress(instance);
	}

	@Test
	public void getInstancesFromBackup() {
		properties.setBackup(true);
		List<ServiceInstance> instances = this.discoveryClient.getInstances("testConsulDiscovery");
		assertThat(instances.isEmpty()).as("instances was empty").isFalse();
		// Close consul container
		ConsulTestcontainers.stop();
		List<ServiceInstance> backupInstances = this.discoveryClient.getInstances("testConsulDiscovery");
		assertThat(backupInstances.isEmpty()).as("instances was empty").isFalse();
		// Resume consul container
		ConsulTestcontainers.start();
	}

	@Test
	public void probeWorks() {
		discoveryClient.probe();
	}

	private void assertIpAddress(ServiceInstance instance) {
		assertThat(Character.isDigit(instance.getHost().charAt(0))).as("host isn't an ip address").isTrue();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@EnableDiscoveryClient
	public static class MyTestConfig {

	}

}

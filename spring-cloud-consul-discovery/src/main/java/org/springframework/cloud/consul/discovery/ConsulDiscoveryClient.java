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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.catalog.CatalogServicesRequest;
import com.ecwid.consul.v1.health.HealthServicesRequest;
import com.ecwid.consul.v1.health.model.HealthService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

/**
 * @author Spencer Gibb
 * @author Joe Athman
 * @author Tim Ysewyn
 * @author Chris Bono
 * @author Chen Zhiguo
 */
public class ConsulDiscoveryClient implements DiscoveryClient {

	private static final Logger logger = LoggerFactory.getLogger(ConsulDiscoveryClient.class);

	private final ConsulClient client;

	private final ConsulDiscoveryProperties properties;

	private final Map<String, List<ServiceInstance>> serviceInstanceBackup = new ConcurrentHashMap<>();

	public ConsulDiscoveryClient(ConsulClient client, ConsulDiscoveryProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	@Override
	public String description() {
		return "Spring Cloud Consul Discovery Client";
	}

	@Override
	public List<ServiceInstance> getInstances(final String serviceId) {
		return getInstances(serviceId, new QueryParams(this.properties.getConsistencyMode()));
	}

	public List<ServiceInstance> getInstances(final String serviceId, final QueryParams queryParams) {
		List<ServiceInstance> instances = new ArrayList<>();

		addInstancesToList(instances, serviceId, queryParams);

		return instances;
	}

	private void addInstancesToList(List<ServiceInstance> instances, String serviceId, QueryParams queryParams) {
		HealthServicesRequest.Builder requestBuilder = HealthServicesRequest.newBuilder()
				.setPassing(properties.isQueryPassing()).setQueryParams(queryParams).setToken(properties.getAclToken());
		String[] queryTags = properties.getQueryTagsForService(serviceId);
		if (queryTags != null) {
			requestBuilder.setTags(queryTags);
		}
		HealthServicesRequest request = requestBuilder.build();
		try {
			Response<List<HealthService>> services = this.client.getHealthServices(serviceId, request);

			for (HealthService service : services.getValue()) {
				instances.add(new ConsulServiceInstance(service, serviceId));
			}
			if (properties.isBackup()) {
				serviceInstanceBackup.put(serviceId, instances);
			}
		} catch (Exception exception) {
			logger.error("Error getting instances from Consul.", exception);
			if (properties.isBackup() && serviceInstanceBackup.containsKey(serviceId)) {
				instances.addAll(serviceInstanceBackup.get(serviceId));
			}
		}
	}

	public List<ServiceInstance> getAllInstances() {
		List<ServiceInstance> instances = new ArrayList<>();

		Response<Map<String, List<String>>> services = this.client
				.getCatalogServices(CatalogServicesRequest.newBuilder().setQueryParams(QueryParams.DEFAULT).build());
		for (String serviceId : services.getValue().keySet()) {
			addInstancesToList(instances, serviceId, QueryParams.DEFAULT);
		}
		return instances;
	}

	@Override
	public List<String> getServices() {
		CatalogServicesRequest request = CatalogServicesRequest.newBuilder().setQueryParams(QueryParams.DEFAULT)
				.setToken(this.properties.getAclToken()).build();
		return new ArrayList<>(this.client.getCatalogServices(request).getValue().keySet());
	}

	@Override
	public void probe() {
		this.client.getStatusLeader();
	}

	@Override
	public int getOrder() {
		return this.properties.getOrder();
	}

}

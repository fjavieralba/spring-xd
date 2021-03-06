/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.xd.dirt.server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.SourceFilteringListener;
import org.springframework.integration.monitor.IntegrationMBeanExporter;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.xd.dirt.container.ContainerMetadata;
import org.springframework.xd.dirt.server.options.ContainerOptions;
import org.springframework.xd.dirt.server.options.XDPropertyKeys;
import org.springframework.xd.dirt.util.BannerUtils;
import org.springframework.xd.dirt.util.ConfigLocations;
import org.springframework.xd.dirt.util.XdConfigLoggingInitializer;
import org.springframework.xd.dirt.zookeeper.EmbeddedZooKeeper;
import org.springframework.xd.dirt.zookeeper.ZooKeeperConnection;

/**
 * The boot application class for a Container server.
 * 
 * @author Mark Fisher
 * @author David Turanski
 */
@Configuration
@EnableAutoConfiguration
@Import(PropertyPlaceholderAutoConfiguration.class)
public class ContainerServerApplication {

	private static final Log log = LogFactory.getLog(ContainerServerApplication.class);

	private static final String MBEAN_EXPORTER_BEAN_NAME = "XDLauncherMBeanExporter";

	public static final String NODE_PROFILE = "node";

	private ConfigurableApplicationContext containerContext;

	public static void main(String[] args) {
		new ContainerServerApplication().run(args);
	}

	// TODO: Is this needed anymore?
	public ConfigurableApplicationContext getPluginContext() {
		if (this.containerContext != null) {
			return (ConfigurableApplicationContext) this.containerContext.getParent();
		}
		else {
			log.error("The container has not been initialized yet.");
			return null;
		}
	}

	public ConfigurableApplicationContext getContainerContext() {
		return this.containerContext;
	}

	public void dumpContextConfiguration() {
		ApplicationUtils.dumpContainerApplicationContextConfiguration(this.containerContext);
	}


	public ContainerServerApplication run(String... args) {
		System.out.println(BannerUtils.displayBanner(getClass().getSimpleName(), null));

		try {
			ContainerBootstrapContext bootstrapContext = new ContainerBootstrapContext(new ContainerOptions());

			this.containerContext = new SpringApplicationBuilder(ContainerOptions.class, ParentConfiguration.class)
					.profiles(NODE_PROFILE)
					.listeners(bootstrapContext.commandLineListener())
					.child(ContainerServerApplication.class)
					.listeners(
							ApplicationUtils.mergeApplicationListeners(bootstrapContext.commandLineListener(),
									bootstrapContext.orderedContextInitializers()))
					.child(ContainerConfiguration.class)
					.listeners(bootstrapContext.commandLineListener())
					.initializers(new IdInitializer())
					.run(args);
		}
		catch (Exception e) {
			handleErrors(e);
		}
		return this;
	}

	private void handleErrors(Exception e) {
		if (e.getCause() instanceof BindException) {
			BindException be = (BindException) e.getCause();
			for (FieldError error : be.getFieldErrors()) {
				System.err.println(String.format("the value '%s' is not allowed for property '%s'",
						error.getRejectedValue(),
						error.getField()));
				if (XDPropertyKeys.XD_CONTROL_TRANSPORT.equals(error.getField())) {
					System.err.println(
							String.format(
									"If not explicitly provided, the default value of '%s' assumes the value provided for '%s'",
									XDPropertyKeys.XD_CONTROL_TRANSPORT, XDPropertyKeys.XD_TRANSPORT));
				}
			}
		}
		else {
			e.printStackTrace();
		}
		System.exit(1);
	}


	@Configuration
	@Profile(SingleNodeApplication.SINGLE_PROFILE)
	protected static class SingleNodeZooKeeperConnectionConfiguration {

		@Value("${zk.client.connect:}")
		private String zkClientConnect;

		@Autowired(required = false)
		private EmbeddedZooKeeper server;

		@Bean
		public ZooKeeperConnection zooKeeperConnection() {
			// the embedded server accepts client connections on a dynamically determined port
			if (server != null) {
				zkClientConnect = "localhost:" + server.getClientPort();
			}
			return new ZooKeeperConnection(zkClientConnect);
		}
	}


	@Configuration
	@Profile("!" + SingleNodeApplication.SINGLE_PROFILE)
	protected static class DistributedZooKeeperConnectionConfiguration {

		@Value("${zk.client.connect:}")
		private String zkClientConnect;

		@Bean
		public ZooKeeperConnection zooKeeperConnection() {
			if (StringUtils.hasText(zkClientConnect)) {
				return new ZooKeeperConnection(zkClientConnect);
			}
			return new ZooKeeperConnection();
		}
	}


	@ConditionalOnExpression("${XD_JMX_ENABLED:false}")
	@EnableMBeanExport(defaultDomain = "xd.container")
	protected static class JmxConfiguration {

		@Bean(name = MBEAN_EXPORTER_BEAN_NAME)
		public IntegrationMBeanExporter integrationMBeanExporter() {
			IntegrationMBeanExporter exporter = new IntegrationMBeanExporter();
			exporter.setDefaultDomain("xd.container");
			return exporter;
		}
	}


	private class IdInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

		@Override
		public void initialize(ConfigurableApplicationContext applicationContext) {
			ContainerMetadata containerMetadata = applicationContext.getParent().getBean(ContainerMetadata.class);
			applicationContext.setId(containerMetadata.getId());
		}
	}
}


/**
 * Container Application Context
 * 
 * @author David Turanski
 */
@Configuration
@ImportResource({
	"classpath:" + ConfigLocations.XD_INTERNAL_CONFIG_ROOT + "container-server.xml",
})
@EnableAutoConfiguration
class ContainerConfiguration {

	@Autowired
	private ContainerMetadata containerMetadata;

	@Autowired
	private ZooKeeperConnection zooKeeperConnection;

	@Bean
	public ApplicationListener<?> xdInitializer(ApplicationContext context) {
		XdConfigLoggingInitializer delegate = new XdConfigLoggingInitializer(true);
		delegate.setEnvironment(context.getEnvironment());
		return new SourceFilteringListener(context, delegate);
	}

	@Bean
	public ContainerRegistrar containerRegistrar() {
		return new ContainerRegistrar(containerMetadata, zooKeeperConnection);
	}
}

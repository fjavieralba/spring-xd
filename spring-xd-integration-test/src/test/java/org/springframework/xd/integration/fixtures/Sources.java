/*
 * Copyright 2014 the original author or authors.
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

package org.springframework.xd.integration.fixtures;

import java.net.URL;
import java.util.List;

import org.springframework.shell.core.JLineShellComponent;
import org.springframework.xd.shell.command.fixtures.HttpSource;
import org.springframework.xd.shell.command.fixtures.TcpSource;


/**
 * 
 * @author renfrg
 */
public class Sources {

	private URL adminServer = null;

	private List<URL> containers = null;

	private JLineShellComponent shell = null;

	private HttpSource httpSource = null;

	private TcpSource tcpSource = null;

	private int httpPort = 9000;

	private static int TCP_SINK_PORT = 1234;

	public Sources(URL adminServer, List<URL> containers, JLineShellComponent shell, int httpPort) {
		this.adminServer = adminServer;
		this.containers = containers;
		this.shell = shell;
		this.httpPort = httpPort;
	}

	public HttpSource http() {
		if (httpSource == null) {
			httpSource = http(httpPort);
		}
		return httpSource;
	}

	public HttpSource http(int port) {
		return new HttpSource(shell, containers.get(0).getHost(), port);
	}

	public TcpSource tcp() {
		if (tcpSource == null) {
			tcpSource = tcp(TCP_SINK_PORT);
		}
		return tcpSource;
	}

	public TcpSource tcp(int port) {
		return new TcpSource(containers.get(0).getHost(), port);
	}

	public TailSource tail(int delay, String fileName) throws Exception {
		return new TailSource(delay, fileName);
	}

	public FileSource file(String dir, String fileName) throws Exception {
		return new FileSource(dir, fileName);
	}
}

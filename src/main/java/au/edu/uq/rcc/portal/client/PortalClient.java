/*
 * RCC Portals OpenID Client
 * https://github.com/UQ-RCC/portal-client
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2020 The University of Queensland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.edu.uq.rcc.portal.client;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.security.Security;
import java.util.ArrayList;
import java.util.List;

@ServletComponentScan
@SpringBootApplication
@EnableConfigurationProperties
public class PortalClient {
	public static int cliMain(String[] args) {
		ArgumentParser parser = ArgumentParsers.newArgumentParser("portal-client")
				.defaultHelp(true);

		if(args.length == 0) {
			args = new String[] { "run" };
		}

		Subparsers aa = parser.addSubparsers().dest("operation");

		Subparser db = aa.addParser("db")
				.help("Database commands");

		Subparsers dbop = db.addSubparsers().dest("dbop");
		dbop.addParser("init")
				.help("Initialise database");

		aa.addParser("run")
				.help("Run the application server");

		Namespace ns;
		List<String> springArgs = new ArrayList<>();
		try {
			ns = parser.parseKnownArgs(args, springArgs);
		} catch(ArgumentParserException e) {
			parser.handleError(e);
			return 2;
		}

		String op = ns.getString("operation");
		if(op == null) {
			return -1;
		}

		/*
		 * NB: This is a bit hacky. Use argparse to handle everything, but since
		 *  it doesn't support arbitrary --XXX arguments, gather all these "unrecognized" arguments
		 *  and give them to Spring directly.
		 */
		ConfigurableEnvironment env = new StandardEnvironment();
		env.getPropertySources().addFirst(new SimpleCommandLinePropertySource(springArgs.stream().toArray(String[]::new)));
		ConfigDataEnvironmentPostProcessor.applyTo(env);

		if("db".equals(op)) {
			return DbCli.withEnvironment(env).run(ns, System.out, System.err);
		} else if("run".equals(op)) {
			SpringApplication sapp = new SpringApplication(PortalClient.class);
			sapp.setEnvironment(env);
			sapp.run();
			return -1;
		}

		return 1;
	}

	public static void main(String[] args) {
		Security.addProvider(new BouncyCastleProvider());
		int r = cliMain(args);
		if(r >= 0) {
			System.exit(r);
		}

		/* Special-case, if cliMain() returns <0, then it's started the application. */
	}
}

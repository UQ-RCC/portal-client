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

import net.sourceforge.argparse4j.inf.Namespace;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public class DbCli {
    public static final String SCHEMA_SQL;

    static {
        try(InputStream is = DbCli.class.getResourceAsStream("schema.sql")) {
            SCHEMA_SQL = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private final Environment env;
    private final DataSource dataSource;

    private DbCli(Environment env) {
        this.env = Objects.requireNonNull(env, "environment");
        this.dataSource = DataSourceBuilder.create()
                .url(env.getRequiredProperty("spring.datasource.url"))
                .username(env.getRequiredProperty("spring.datasource.username"))
                .password(env.getRequiredProperty("spring.datasource.password"))
                .driverClassName(env.getRequiredProperty("spring.datasource.driver"))
                .build();
    }

    public int run(Namespace args, PrintStream out, PrintStream err) {
        Objects.requireNonNull(args, "args");

        /* For now */
        if(!"init".equals(args.getString("dbop"))) {
            throw new IllegalStateException();
        }

        try(Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try(Statement s = c.createStatement()) {
                s.executeUpdate(SCHEMA_SQL);
            }
            c.commit();
        } catch(SQLException e) {
            e.printStackTrace(err);
            return 1;
        }

        return 0;
    }

    public static DbCli withEnvironment(Environment env) {
        return new DbCli(env);
    }
}

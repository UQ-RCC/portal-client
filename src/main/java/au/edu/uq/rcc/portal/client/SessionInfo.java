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

import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

import javax.servlet.http.HttpSession;

@SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
public class SessionInfo {
	private final HttpSession session;

	private SessionInfo(HttpSession session) {
		this.session = session;
	}

	public static SessionInfo wrap(HttpSession session) {
		return new SessionInfo(session);
	}

	public String getId() {
		return session.getId();
	}

	public HttpSession getSession() {
		return session;
	}

	public OAuth2AccessToken getAccessToken(String provider) {
		return (OAuth2AccessToken)session.getAttribute(String.format("%s.access_token", provider));
	}

	public SessionInfo setAccessToken(String provider, OAuth2AccessToken token) {
		session.setAttribute(String.format("%s.access_token", provider), token);
		return this;
	}

	public OAuth2RefreshToken getRefreshToken(String provider) {
		return (OAuth2RefreshToken)session.getAttribute(String.format("%s.refresh_token", provider));
	}

	public SessionInfo setRefreshToken(String provider, OAuth2RefreshToken token) {
		session.setAttribute(String.format("%s.refresh_token", provider), token);
		return this;
	}

	public String getUid(String provider) {
		return (String)session.getAttribute(String.format("%s.uid", provider));
	}

	public SessionInfo setUid(String provider, String uid) {
		session.setAttribute(String.format("%s.uid", provider), uid);
		return this;
	}

	public String getUsername(String provider) {
		return (String)session.getAttribute(String.format("%s.username", provider));
	}

	public SessionInfo setUsername(String provider, String username) {
		session.setAttribute(String.format("%s.username", provider), username);
		return this;
	}

	public String getEmail(String provider) {
		return (String)session.getAttribute(String.format("%s.email", provider));
	}

	public SessionInfo setEmail(String provider, String email) {
		session.setAttribute(String.format("%s.email", provider), email);
		return this;
	}

}

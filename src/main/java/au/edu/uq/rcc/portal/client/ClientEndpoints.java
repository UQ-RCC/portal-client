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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.DefaultRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@RestController
public class ClientEndpoints {
	private static final Logger LOGGER = LoggerFactory.getLogger(ClientEndpoints.class);

	private final String successPage;

	public ClientEndpoints() throws IOException {
		try(InputStream is = ClientEndpoints.class.getResourceAsStream("success.html")) {
			successPage = new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Autowired
	private ObjectMapper mapper;

	@Autowired
	private OAuth2AuthorizedClientService authorizedClientService;

	@Autowired
	private InMemoryClientRegistrationRepository clientRegistrationRepository;

	@RequestMapping(value = "/login/success", produces = MediaType.TEXT_HTML_VALUE)
	public ResponseEntity<String> authCallback2(
			HttpServletRequest request,
			HttpServletResponse response
	) {
		OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken)SecurityContextHolder.getContext().getAuthentication();

		OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(authToken.getAuthorizedClientRegistrationId(), authToken.getName());

		OAuth2AccessToken accessToken = client.getAccessToken();
		OAuth2RefreshToken refreshToken = client.getRefreshToken();

		OAuth2User user = authToken.getPrincipal();
		String regId = authToken.getAuthorizedClientRegistrationId();

		SessionInfo.wrap(request.getSession(true))
				.setUid(regId, user.getName())
				.setUsername(regId, user.getAttribute("preferred_username"))
				.setEmail(regId, user.getAttribute("email"))
				.setAccessToken(regId, accessToken)
				.setRefreshToken(regId, refreshToken);

		LOGGER.info("User {} ({}) authenticated against {}.", user.getAttribute("preferred_username"), user.getName(), regId);
		return new ResponseEntity<>(successPage, HttpStatus.OK);
	}

	@RequestMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
	public ResponseEntity<String> login(HttpServletRequest request, HttpServletResponse response, OAuth2AuthenticationToken authToken) throws IOException {

		String service = request.getParameter("service");
		if(service == null || service.isEmpty()) {
			return new ResponseEntity<>(HttpStatus.UNPROCESSABLE_ENTITY);
		}

		ClientRegistration reg = clientRegistrationRepository.findByRegistrationId(service);
		if(reg == null) {
			return new ResponseEntity<>(HttpStatus.UNPROCESSABLE_ENTITY);
		}

		if(SessionInfo.wrap(request.getSession()).getAccessToken(service) != null) {
			return new ResponseEntity<>(successPage, HttpStatus.OK);
		}

		String path = request.getContextPath()
				+ OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI
				+ "/" + reg.getClientName();

		response.sendRedirect(path);
		return null;
	}

	@RequestMapping(value = "/api/end_session")
	public ResponseEntity<Void> endSession(HttpServletRequest request) {
		HttpSession ses = request.getSession(false);
		if(ses == null) {
			return new ResponseEntity<>(HttpStatus.OK);
		}

		ses.invalidate();
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequestMapping(value = "/api/session_info", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ObjectNode> sessionInfo(HttpServletRequest request, HttpServletResponse response) {
		String service = request.getParameter("service");

		SessionInfo si = SessionInfo.wrap(request.getSession(true));

		ObjectNode on = mapper.createObjectNode();
		on.put("session_id", si.getId());

		OAuth2AccessToken accessToken = null;
		String username = null;
		String email = null;

		ArrayNode providers = mapper.createArrayNode();
		for(ClientRegistration cr : clientRegistrationRepository) {
			OAuth2AccessToken at = si.getAccessToken(cr.getRegistrationId());
			if(cr.getRegistrationId().equals(service)) {
				accessToken = at;
				username = si.getUsername(cr.getRegistrationId());
				email = si.getEmail(cr.getRegistrationId());
			}

			if(at != null) {
				providers.add(cr.getRegistrationId());
			}
		}


		on.put("has_oauth_access_token", Boolean.toString(accessToken != null));
		on.put("uname", username == null ? "" : username);
		on.put("email", email == null ? "" : email);
		on.set("providers", providers);

		return new ResponseEntity<>(on, HttpStatus.OK);
	}

	@RequestMapping(value = "/api/access_token", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ObjectNode> accessToken(HttpServletRequest request, HttpServletResponse response) {
		String service = request.getParameter("service");
		HttpSession sess = request.getSession();

		if(service == null || sess == null || service.isEmpty()) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}

		SessionInfo si = SessionInfo.wrap(sess);

		OAuth2AccessToken at = si.getAccessToken(service);
		if(at == null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}

		OAuth2RefreshToken rt = si.getRefreshToken(service);

		Instant expiresAt = at.getExpiresAt();
		if(expiresAt != null && expiresAt.isBefore(Instant.now())) {
			LOGGER.info("Refreshing access token for user {} ({}) against {}.",
					si.getUsername(service),
					si.getUid(service),
					service
			);

			ClientRegistration reg = clientRegistrationRepository.findByRegistrationId(service);

			OAuth2RefreshTokenGrantRequest grq = new OAuth2RefreshTokenGrantRequest(reg, at, rt);
			OAuth2AccessTokenResponse nat = new DefaultRefreshTokenTokenResponseClient()
					.getTokenResponse(grq);

			at = nat.getAccessToken();
			si.setAccessToken(service, at);
			si.setRefreshToken(service, nat.getRefreshToken());
		}

		ObjectNode on = mapper.createObjectNode();
		on.put("uid", si.getUid(service));
		on.put("uname", si.getUsername(service));
		on.put("access_token", at.getTokenValue());

		return new ResponseEntity<>(on, HttpStatus.OK);
	}
}

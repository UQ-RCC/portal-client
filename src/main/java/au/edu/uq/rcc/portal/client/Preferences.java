package au.edu.uq.rcc.portal.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.FloatNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

@RestController
public class Preferences {
	private static final Logger LOGGER = LoggerFactory.getLogger(Preferences.class);

	private final JsonSchema prefsSchema;

	@Autowired
	private ObjectMapper mapper;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private InMemoryClientRegistrationRepository clientRegistrationRepository;

	public Preferences() throws IOException {
		try(InputStream is = Preferences.class.getResourceAsStream("preferences.schema.json")) {
			prefsSchema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(is);
		}
	}

	/*
	 * These should really be in another serivce as we can't properly validate access tokens,
	 * but until the frontend's fixed...
	 *
	 * Also CSRF should be re-enabled for this endpoint when moved.
	 */
	@RequestMapping(value = "/api/preference/{service}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> preferenceGet(HttpServletRequest request, HttpServletResponse response, @PathVariable("service") String service) {
		if(service.isEmpty()) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		ClientRegistration reg = clientRegistrationRepository.findByRegistrationId(service);
		if(reg == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		SessionInfo si = SessionInfo.wrap(request.getSession());
		String uid = si.getUid(service);
		if(uid == null || si.getAccessToken(service) == null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}

		JsonNode jn = mapper.createObjectNode();
		SqlRowSet rs = jdbc.queryForRowSet("SELECT id, data FROM preferences WHERE uid = ?", uid);
		if(rs.next()) {
			try {
				jn = mapper.readTree(rs.getString("data"));
			} catch(JsonProcessingException e) {
				LOGGER.error("Error Pg JSONB->Jackson conversion failed.", e);
			}
		}

		return new ResponseEntity<>(jn, HttpStatus.OK);
	}

	@RequestMapping(value = "/api/preference/{service}", method = RequestMethod.PUT, consumes = {MediaType.APPLICATION_JSON_VALUE})
	public ResponseEntity<Void> preferencePut(HttpServletRequest request, @PathVariable("service") String service, @RequestBody JsonNode data) {
		if(service.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		if(!data.isObject()) {
			return ResponseEntity.badRequest().build();
		}

		ClientRegistration reg = clientRegistrationRepository.findByRegistrationId(service);
		if(reg == null) {
			return ResponseEntity.notFound().build();
		}

		SessionInfo si = SessionInfo.wrap(request.getSession());
		String uid = si.getUid(service);
		if(uid == null || si.getAccessToken(service) == null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}

		/* Fix crap from older frontends. */
		patchPreferences((ObjectNode)data);

		/* Abort if we failed validation. */
		if(!prefsSchema.validateAndCollect(data).getValidationMessages().isEmpty()) {
			return ResponseEntity.badRequest().build();
		}

		jdbc.update("INSERT INTO preferences(uid, provider, data) VALUES(?, ?, ?::JSONB) ON CONFLICT(uid, provider) DO UPDATE SET data=EXCLUDED.data",
				uid,
				service,
				data.toString()
		);
		return ResponseEntity.ok().build();
	}

	private static void unstring(ObjectNode on, Function<String, JsonNode> proc, String... keys) {
		JsonNode tmp;
		for(String s : keys) {
			if((tmp = on.get(s)) != null && tmp.isTextual()) {
				try {
					on.set(s, proc.apply(tmp.asText()));
				} catch(NumberFormatException e) {
					/* nop */
				}
			}
		}
	}

	private void fixPrefEntry(JsonNode _pref) {
		if(!_pref.isObject())
			return;

		ObjectNode pref = (ObjectNode)_pref;

		JsonNode tmp;

		/* Some older, buggy frontends double-encoded things. */
		if((tmp = pref.get("files")) != null && tmp.isTextual()) {

			try {
				tmp = mapper.readTree(tmp.asText());
			} catch(JsonProcessingException e) {
				/* nop */
			}
			pref.set("files", tmp);

			if(tmp.isArray()) {
				for(JsonNode jn : tmp) {
					if(!jn.isObject()) {
						continue;
					}

					ObjectNode obn = (ObjectNode)jn;
					unstring(obn, s -> FloatNode.valueOf(Float.parseFloat(s)),
							"pixelD", "maxSizeM", "dr", "pixelW", "dz", "stddev",
							"pixelH", "threshold"
					);

					unstring(obn, s -> IntNode.valueOf(Integer.parseInt(s)),
							"c", "total", "t", "x", "y", "z"
					);

					if((tmp = obn.get("dz")) != null && tmp.isTextual() && "nan".equalsIgnoreCase(tmp.asText())) {
						obn.set("dz", FloatNode.valueOf(Float.NaN));
					}
				}
			}
		}

		if((tmp = pref.get("preference")) != null && tmp.isTextual()) {
			JsonNode jn;
			try {
				jn = mapper.readTree(tmp.asText());
			} catch(JsonProcessingException e) {
				return;
			}

			pref.set("preference", jn);

			if(!jn.isObject()) {
				return;
			}

			ObjectNode ojn = (ObjectNode)jn;

			unstring(ojn, s -> IntNode.valueOf(Integer.parseInt(s)),
					"axialSpacing", "threshold", "lateralSpacing"
			);
		}
	}

	/* Fix the preferences blob. Some older frontends mangled things a bit. */
	private ObjectNode patchPreferences(ObjectNode on) {
		JsonNode tmp;

		on.remove("psfType");

		if((tmp = on.get("pref")) != null && tmp.isArray()) {
			ArrayNode pref = (ArrayNode)tmp;
			pref.forEach(this::fixPrefEntry);
		}

		return on;
	}

	/* Old code to fix all at once, keeping for posterity. */
	private ResponseEntity<Void> fixprefs() {
		ObjectNode on;

		SqlRowSet rs = jdbc.queryForRowSet("SELECT * FROM preferences");
		while(rs.next()) {
			try {
				on = (ObjectNode)mapper.readTree(rs.getString("data"));
			} catch(JsonProcessingException e) {
				LOGGER.error("Error Pg JSONB->Jackson conversion failed.", e);
				continue;
			}

			patchPreferences(on);

			ValidationResult vr = prefsSchema.validateAndCollect(on);

			/* All good? Do nothing. */
			if(!vr.getValidationMessages().isEmpty()) {
				continue;
			}

			long id = rs.getLong("id");
			jdbc.update("UPDATE preferences SET data = ?::JSONB WHERE id = ?", on.toString(), id);
		}

		return ResponseEntity.ok().build();
	}
}

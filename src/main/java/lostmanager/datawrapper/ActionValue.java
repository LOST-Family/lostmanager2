package lostmanager.datawrapper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionValue {

	public static enum kind {
		type, reason, value, setting
	}

	public enum ACTIONVALUETYPE {
		FILLER, REMINDER, VALUE
	}

	private final kind saved;
	private ACTIONVALUETYPE type;
	private KickpointReason reason;
	private Long value;
	/**
	 * Name of the setting for {@link kind#setting} entries. Named settings are read
	 * by key instead of by position, so new options can be added without shifting
	 * the meaning of the positional {@link kind#value} entries of older events.
	 */
	private String key;

	@JsonCreator
	public ActionValue(
			@JsonProperty("saved") kind saved,
			@JsonProperty("type") ACTIONVALUETYPE type,
			@JsonProperty("reason") KickpointReason reason,
			@JsonProperty("value") Long value,
			@JsonProperty("key") String key) {
		this.saved = saved;
		this.type = type;
		this.reason = reason;
		this.value = value;
		this.key = key;
	}

	public ActionValue(ACTIONVALUETYPE type) {
		this.saved = kind.type;
		this.type = type;
	}

	public ActionValue(KickpointReason reason) {
		this.saved = kind.reason;
		this.reason = reason;
	}

	public ActionValue(Long value) {
		this.saved = kind.value;
		this.value = value;
	}

	/**
	 * Creates a named setting. Read back via
	 * {@link ListeningEvent#getSetting(String, Long)}.
	 */
	public ActionValue(String key, Long value) {
		this.saved = kind.setting;
		this.key = key;
		this.value = value;
	}

	public kind getSaved() {
		return saved;
	}

	public ACTIONVALUETYPE getType() {
		return type;
	}

	public KickpointReason getReason() {
		return reason;
	}

	public Long getValue() {
		return value;
	}

	public String getKey() {
		return key;
	}


	public void setValue(Long value) {
		this.value = value;
	}
}

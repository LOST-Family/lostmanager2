package datawrapper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ActionValue {

	public static enum kind {
		type, reason, value
	}

	public enum ACTIONVALUETYPE {
		FILLER, REMINDER
	}

	private kind saved;
	private ACTIONVALUETYPE type;
	private KickpointReason reason;
	private Long value;

	@JsonCreator
	public ActionValue(@JsonProperty("type") ACTIONVALUETYPE type, @JsonProperty("reason") KickpointReason reason,
			@JsonProperty("value") Long value) {
		if (type != null) {
			this.saved = kind.type;
			this.type = type;
		} else if (reason != null) {
			this.saved = kind.reason;
			this.reason = reason;
		} else if (value != null) {
			this.saved = kind.value;
			this.value = value;
		}
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

}

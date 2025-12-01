package datawrapper;

import java.sql.Timestamp;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AchievementData {

	public enum Type {
		WINS, CLANGAMES_POINTS
	};

	private Timestamp timeextracted;

	private Integer data;

	private Type type;

	@JsonCreator
	public AchievementData(@JsonProperty("time") @JsonAlias("timeExtracted") Timestamp timeextracted, @JsonProperty("data") Integer data,
			@JsonProperty("type") Type type) {
		this.timeextracted = timeextracted;
		this.data = data;
		this.type = type;
	}

	public Timestamp getTimeExtracted() {
		return timeextracted;
	}

	public Object getData() {
		return data;
	}

	public Type getType() {
		return type;
	}

}

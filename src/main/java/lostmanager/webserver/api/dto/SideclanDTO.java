package lostmanager.webserver.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for sideclans
 */
public class SideclanDTO {

    @JsonProperty("clan_tag")
    private String clanTag;

    @JsonProperty("name")
    private String name;

    @JsonProperty("belongs_to")
    private String belongsTo;

    public SideclanDTO() {
        // default for Jackson
    }

    public SideclanDTO(String clanTag, String name, String belongsTo) {
        this.clanTag = clanTag;
        this.name = name;
        this.belongsTo = belongsTo;
    }

    public String getClanTag() {
        return clanTag;
    }

    public void setClanTag(String clanTag) {
        this.clanTag = clanTag;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBelongsTo() {
        return belongsTo;
    }

    public void setBelongsTo(String belongsTo) {
        this.belongsTo = belongsTo;
    }
}

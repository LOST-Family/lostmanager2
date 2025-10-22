package datawrapper;

import java.util.ArrayList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import datautil.DBUtil;

public class ListeningEvent {

	public enum LISTENINGTYPE {
		CW, RAID, CWLDAY, CS
	}

	public enum ACTIONTYPE {
		INFOMESSAGE, CUSTOMMESSAGE, KICKPOINT, CWDONATOR
	}

	private Long id;
	private String clan_tag;
	private LISTENINGTYPE listeningtype;
	private Long durationuntilend; //in ms
	private ACTIONTYPE actiontype;
	private String channelid;
	private ArrayList<ActionValue> actionvalues;
	
	private Long timestamptofire;
	
	private Boolean fireStatus = false;
	
	public ListeningEvent refreshData() {
		clan_tag = null;
		listeningtype = null;
		durationuntilend = null;
		actiontype = null;
		channelid = null;
		actionvalues = null;
		timestamptofire = null;
		fireStatus = null;
		return this;
	}
	
	public ListeningEvent(long id) {
		this.id = id;
	}

	public ListeningEvent setClanTag(String clan_tag) {
		this.clan_tag = clan_tag;
		return this;
	}

	public ListeningEvent setListeningType(LISTENINGTYPE type) {
		this.listeningtype = type;
		return this;
	}

	public ListeningEvent setDurationUntilEnd(Long l) {
		this.durationuntilend = l;
		return this;
	}

	public ListeningEvent setActionType(ACTIONTYPE type) {
		this.actiontype = type;
		return this;
	}

	public ListeningEvent setChannelID(String channelid) {
		this.channelid = channelid;
		return this;
	}

	public ListeningEvent setActionValues(ArrayList<ActionValue> list) {
		this.actionvalues = list;
		return this;
	}
	
	public ListeningEvent setFireStatus(boolean b) {
		fireStatus = b;
		return this;
	}

	public long getID() {
		return id;
	}

	public String getClanTag() {
		if (clan_tag == null) {
			clan_tag = DBUtil.getValueFromSQL("SELECT clan_tag FROM listening_events WHERE id = ?", String.class,
					id);
		}
		return clan_tag;
	}

	public LISTENINGTYPE getListeningType() {
		if (listeningtype == null) {
			String type = DBUtil.getValueFromSQL("SELECT listeningtype FROM listening_events WHERE id = ?",
					String.class, id);
			listeningtype = type.equals("cw") ? LISTENINGTYPE.CW
					: type.equals("raid") ? LISTENINGTYPE.RAID
							: type.equals("cwl") ? LISTENINGTYPE.CWLDAY : type.equals("cs") ? LISTENINGTYPE.CS : null;
		}
		return listeningtype;
	}

	public long getDurationUntilEnd() {
		if (durationuntilend == null) {
			durationuntilend = DBUtil.getValueFromSQL("SELECT listeningvalue FROM listening_events WHERE id = ?",
					Long.class, id);
		}
		return durationuntilend;
	}

	public ACTIONTYPE getActionType() {
		if (actiontype == null) {
			String type = DBUtil.getValueFromSQL("SELECT actiontype FROM listening_events WHERE id = ?", String.class,
					id);
			actiontype = type.equals("infomessage") ? ACTIONTYPE.INFOMESSAGE
					: type.equals("custommessage") ? ACTIONTYPE.CUSTOMMESSAGE
							: type.equals("kickpoint") ? ACTIONTYPE.KICKPOINT : null;
		}
		return actiontype;
	}

	public String getChannelID() {
		if (channelid == null) {
			channelid = DBUtil.getValueFromSQL("SELECT channel_id FROM listening_events WHERE id = ?", String.class,
					id);
		}
		return channelid;
	}

	public ArrayList<ActionValue> getActionValues() {
		if (actionvalues == null) {
			String json = DBUtil.getValueFromSQL("SELECT actionvalues FROM listening_events WHERE id = ?", String.class,
					id);
			ObjectMapper mapper = new ObjectMapper();
			try {
				actionvalues = mapper.readValue(json, new TypeReference<ArrayList<ActionValue>>() {
				});
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}
		return actionvalues;
	}
	
	public Boolean hasFired() {
		return hasFired();
	}

	public Long getTimestamp() {
		Long timestamp = null;
		switch (getListeningType()) {
		case CS:
			
			break;
		case CW:
			
			break;
		case CWLDAY:
			
			break;
		case RAID:
			
			break;
		}
		return timestamp;
	}
	
}

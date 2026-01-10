package lostmanager.datawrapper;

import java.time.OffsetDateTime;

import lostmanager.dbutil.DBUtil;

public class Kickpoint {

	private Long id;
	private String description;
	private Long amount;
	private Player player;
	private OffsetDateTime kpdate;
	private OffsetDateTime givendate;
	private OffsetDateTime expirationdate;
	private User givenby;

	public Kickpoint(long id) {
		this.id = id;
	}

	public Kickpoint refreshData() {
		description = null;
		amount = null;
		player = null;
		kpdate = null;
		givendate = null;
		expirationdate = null;
		givenby = null;
		return this;
	}

	// all public getter Methods

	public long getID() {
		return id;
	}

	public String getDescription() {
		if (description == null) {
			description = DBUtil.getValueFromSQL("SELECT description FROM kickpoints WHERE id = ?", String.class, id);
		}
		return description;
	}

	public long getAmount() {
		if (amount == null) {
			amount = DBUtil.getValueFromSQL("SELECT amount FROM kickpoints WHERE id = ?", Long.class, id);
		}
		return amount;
	}

	public Player getPlayer() {
		if (player == null) {
			String value = DBUtil.getValueFromSQL("SELECT player_tag FROM kickpoints WHERE id = ?", String.class, id);
			player = value == null ? null : new Player(value);
		}
		return player;
	}

	public OffsetDateTime getDate() {
		if (kpdate == null) {
			kpdate = DBUtil.getDateFromSQL("SELECT date FROM kickpoints WHERE id = ?", id);
		}
		return kpdate;
	}

	public OffsetDateTime getGivenDate() {
		if (givendate == null) {
			givendate = DBUtil.getDateFromSQL("SELECT created_at FROM kickpoints WHERE id = ?", id);
		}
		return givendate;
	}

	public OffsetDateTime getExpirationDate() {
		if (expirationdate == null) {
			expirationdate = DBUtil.getDateFromSQL("SELECT expires_at FROM kickpoints WHERE id = ?", id);
		}
		return expirationdate;
	}

	public User getUserGivenBy() {
		if(givenby == null) {
			String value = DBUtil.getValueFromSQL("SELECT created_by_discord_id FROM kickpoints WHERE id = ?", String.class,
					id);
			givenby = value == null ? null : new User(value);
		}
		return givenby;
	}

}

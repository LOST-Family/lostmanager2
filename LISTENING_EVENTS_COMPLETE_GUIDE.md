# Listening Events - Complete Feature Review and Documentation

## Database Schema Recommendations

### Current State
- `achievements` table: Stores player tag (text pk) and data (text) - currently using JSON in text field
- `achievement_data` table: Appears to be used for historical tracking with player_tag, type, time, and data fields

### Recommended Schema Updates

#### 1. Achievement Data Table (for Clan Games tracking)
```sql
CREATE TABLE IF NOT EXISTS achievement_data (
    id BIGSERIAL PRIMARY KEY,
    player_tag TEXT NOT NULL,
    type TEXT NOT NULL,
    time TIMESTAMP NOT NULL,
    data JSONB NOT NULL,  -- Use JSONB for better performance and querying
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(player_tag, type, time)
);

-- Index for faster queries
CREATE INDEX IF NOT EXISTS idx_achievement_data_lookup ON achievement_data(player_tag, type, time);
CREATE INDEX IF NOT EXISTS idx_achievement_data_time ON achievement_data(time);

-- Auto-cleanup: Delete data older than 6 months
-- Run this periodically (e.g., daily via cron or scheduled task)
DELETE FROM achievement_data WHERE time < NOW() - INTERVAL '6 months';
```

#### 2. Achievements Table (current player state)
```sql
-- Keep current structure but change data type to JSONB
ALTER TABLE achievements ALTER COLUMN data TYPE JSONB USING data::jsonb;

-- Add created_at and updated_at for tracking
ALTER TABLE achievements ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
```

#### 3. Auto-Cleanup Strategy
Create a scheduled function or cron job:
```sql
-- Function to clean old data
CREATE OR REPLACE FUNCTION cleanup_old_achievement_data()
RETURNS void AS $$
BEGIN
    DELETE FROM achievement_data WHERE time < NOW() - INTERVAL '6 months';
    RAISE NOTICE 'Cleaned up achievement data older than 6 months';
END;
$$ LANGUAGE plpgsql;

-- Optional: Create a scheduled event (if using PostgreSQL with pg_cron extension)
-- SELECT cron.schedule('cleanup-achievements', '0 2 * * *', 'SELECT cleanup_old_achievement_data()');
```

## Complete Feature Documentation

## Listening Events System Overview

The Listening Events system automates monitoring and actions for Clash of Clans events across clans.

### Event Types (LISTENINGTYPE)

#### 1. **CS (Clan Games)**
- **When**: Between 22nd at 7:00 AM and 28th at 12:00 PM (hardcoded)
- **What it tracks**: Achievement points difference between start and end dates
- **Data source**: `achievement_data` table with type 'CLANGAMES_POINTS'
- **How it works**:
  1. Queries achievement points at start timestamp
  2. Queries achievement points at end timestamp
  3. Calculates difference
  4. Reports players below threshold (currently 4000 points)

**Compatible Action Types:**
- ✅ `infomessage` - Sends notification of low performers
- ✅ `kickpoint` - Automatically assigns kickpoints to low performers
- ✅ `custommessage` - Sends custom message about clan games status
- ❌ `cwdonator/filler` - Not applicable for Clan Games

#### 2. **CW (Clan War)**
- **When**: During war (preparation, inWar, or warEnded states)
- **What it tracks**: War participation and attacks
- **Data source**: CoC API `/clans/{tag}/currentwar`
- **Two modes based on timing and actiontype**:

##### Mode A: Filler Documentation (Preparation Phase)
- **When**: `duration` set to fire during preparation (e.g., 86400000ms = 24h before end)
- **What it does**: Documents members opted OUT of war
- **Actiontype**: `filler` or `cwdonator`
- **Process**:
  1. Queries war roster from API
  2. Compares with clan members in database
  3. Identifies members not in war
  4. Saves to `cw_fillers` table (clan_tag, player_tag, war_end_time)
  5. Posts list of opted-out members to channel

##### Mode B: Missed Attacks Tracking (War End)
- **When**: `duration` set to 0 (fires at war end)
- **What it does**: Reports members who didn't use all attacks
- **Process**:
  1. Queries war data from API
  2. Gets list of fillers from `cw_fillers` table for this war
  3. For each war member:
     - Checks attacks vs required attacks (uses API's `attacksPerMember` if not explicitly configured)
     - **Skips if player is a filler**
     - Reports if attacks < required attacks
  4. Cleans up filler records after reporting
- **Required Attacks Configuration**: 
  - Leave blank during event creation to automatically use the API's `attacksPerMember` value (recommended)
  - Or specify `1` or `2` to override with a fixed value

**Compatible Action Types:**
- ✅ `infomessage` - Reports missed attacks (excludes fillers)
- ✅ `kickpoint` - Assigns kickpoints for missed attacks (excludes fillers)
- ✅ `custommessage` - Custom war status message
- ✅ `filler` - Documents opted-out members at war start
- ✅ `cwdonator` - Same as filler (legacy support)

#### 3. **CWLDAY (CWL Day)**
- **When**: At the end of each CWL war day
- **What it tracks**: Daily CWL attacks (1 attack per member per day)
- **Data source**: 
  - `/clans/{tag}/currentwar/leaguegroup` - Gets rounds and war tags
  - `/clanwarleagues/wars/{warTag}` - Gets individual war data
- **How it works**:
  1. Fetches CWL league group data
  2. Iterates through rounds to find current/recent wars
  3. For each war involving the clan:
     - Checks each member's attack count
     - Reports members with < 1 attack

**Compatible Action Types:**
- ✅ `infomessage` - Reports members who didn't attack
- ✅ `kickpoint` - Assigns kickpoints for missed CWL attacks
- ✅ `custommessage` - Custom CWL status message
- ❌ `filler` - Not applicable (CWL doesn't use fillers the same way)

#### 4. **RAID (Raid Weekend)**
- **When**: During raid weekend
- **What it tracks**: Raid participation and attack completion
- **Data source**: CoC API `/clans/{tag}/capitalraidseasons`
- **How it works**:
  1. Checks if raid is active
  2. Gets raid member list from API
  3. Compares with clan database members
  4. Reports:
     - Members who didn't participate at all
     - Members with incomplete attacks (attacks < attackLimit + bonusAttackLimit)

**Compatible Action Types:**
- ✅ `infomessage` - Reports raid non-participants and incomplete attacks
- ✅ `kickpoint` - Assigns kickpoints for raid violations
- ✅ `custommessage` - Custom raid status message
- ❌ `filler` - Not applicable for raids

### Action Types (ACTIONTYPE)

#### 1. **infomessage**
- **Purpose**: Send informational notification only, no penalties
- **Behavior**: Posts message to configured channel listing violations
- **Use case**: Monitoring, awareness, reminders
- **Works with**: All listening types (CS, CW, CWLDAY, RAID)

#### 2. **kickpoint**
- **Purpose**: Automatically assign kickpoints to violators
- **Requirements**: Must specify `kickpoint_reason` parameter
- **Behavior**:
  - Posts message to channel with violators
  - Automatically creates kickpoint entries for each violator
  - Uses reason and amount from configured kickpoint reason
  - Kickpoints expire based on clan settings
- **Works with**: All listening types (CS, CW, CWLDAY, RAID)
- **Database impact**: Inserts into `kickpoints` table

#### 3. **custommessage**
- **Purpose**: Send custom user-defined message
- **Requirements**: Opens modal for message input (up to 2000 characters)
- **Behavior**:
  - User enters custom message in modal
  - Message sent to channel when event fires
  - Message stored in event's actionvalues
- **Works with**: All listening types
- **Use case**: Custom reminders, motivational messages, specific instructions

#### 4. **filler**
- **Purpose**: Document war roster at CW start, exclude from missed attacks
- **Requirements**: 
  - Only works with CW listening type
  - Duration should be set for preparation phase (e.g., 86400000ms)
- **Behavior**:
  - Documents members opted out of war
  - Saves to `cw_fillers` table
  - Posts list to channel
  - Later events automatically exclude these fillers from missed attack reports
- **Works with**: Only CW type
- **Database impact**: 
  - Inserts into `cw_fillers` table at war start
  - Auto-cleanup after war ends

#### 5. **cwdonator**
- **Purpose**: Legacy name for filler functionality
- **Behavior**: Identical to `filler`
- **Works with**: Only CW type
- **Note**: Kept for backward compatibility

## Complete Usage Examples

### Example 1: Clan Games Monitoring with Kickpoints
```
/listeningevent add
  clan: LOST F2P
  type: Clan Games
  duration: 0
  actiontype: kickpoint
  channel: #clan-games-violations
  kickpoint_reason: Clangames_unter_4000

Result:
- Fires at Clan Games end (28th 12:00 PM)
- Checks achievement_data table for points difference
- Reports players with < 4000 points
- Automatically adds kickpoints with "Clangames_unter_4000" reason
- Posts message with @mentions to #clan-games-violations
```

### Example 2: CW Filler Documentation
```
/listeningevent add
  clan: LOST 5
  type: Clan War
  duration: 86400000  # 24 hours before war end = during prep
  actiontype: filler
  channel: #war-roster

Result:
- Fires during preparation phase
- Checks which clan members are NOT in the war roster
- Saves their tags to cw_fillers table
- Posts message: "The following members are opted OUT of war:"
- Lists all opted-out members with @mentions
- These members will be excluded from missed attack tracking
```

### Example 3: CW Missed Attacks with Filler Exclusion
```
/listeningevent add
  clan: LOST 5
  type: Clan War
  duration: 0  # At war end
  actiontype: kickpoint
  channel: #war-violations
  kickpoint_reason: CW_Angriff_verpasst

Result:
- Fires when war ends
- Queries cw_fillers table for this specific war
- For each war member:
  - Checks attacks vs required attacks
  - SKIPS if member is a documented filler
  - Reports if attacks incomplete
- Adds kickpoints to non-fillers with missed attacks
- Posts message with @mentions
- Cleans up filler records for this war
```

### Example 4: CWL Daily Monitoring
```
/listeningevent add
  clan: LOST 3
  type: CWL Tag
  duration: 0
  actiontype: infomessage
  channel: #cwl-daily

Result:
- Fires at end of each CWL day
- Checks /leaguegroup for current round
- For each war involving the clan:
  - Identifies members with 0 attacks
  - Posts informational message
- No kickpoints assigned (infomessage only)
```

### Example 5: Raid Weekend Reminder
```
/listeningevent add
  clan: LOST F2P
  type: Raid
  duration: 3600000  # 1 hour before raid ends
  actiontype: custommessage
  channel: #raid-reminders

Modal opens:
"⚠️ RAID ENDS IN 1 HOUR! ⚠️
Make sure you've used all your attacks!
Check #raid-tracking for current status."

Result:
- Fires 1 hour before raid ends
- Posts the custom message to #raid-reminders
- Provides last-minute reminder for members
```

### Example 6: Raid Violations with Kickpoints
```
/listeningevent add
  clan: LOST 2
  type: Raid
  duration: 0
  actiontype: kickpoint
  channel: #raid-violations
  kickpoint_reason: Raid_nicht_vollständig

Result:
- Fires when raid ends
- Checks raid members and their attacks
- Reports two categories:
  1. Members who didn't raid at all
  2. Members with incomplete attacks (X/6 attacks)
- Assigns kickpoints to violators
- Posts with @mentions
```

## Action Type Compatibility Matrix

| Listening Type | infomessage | kickpoint | custommessage | filler | cwdonator |
|---------------|-------------|-----------|---------------|--------|-----------|
| **CS** (Clan Games) | ✅ | ✅ | ✅ | ❌ | ❌ |
| **CW** (Clan War) | ✅ | ✅ | ✅ | ✅ | ✅ |
| **CWLDAY** (CWL Day) | ✅ | ✅ | ✅ | ❌ | ❌ |
| **RAID** (Raid Weekend) | ✅ | ✅ | ✅ | ❌ | ❌ |

## Duration Timing Guide

### Clan Games (CS)
- **0**: At end (28th 12:00 PM)
- **Other values**: Before end (not commonly used)

### Clan War (CW)
- **0**: At war end (after 24h war period)
- **86400000** (24h): During preparation phase
- **43200000** (12h): 12 hours before war end
- **3600000** (1h): 1 hour before war end

**Tip**: Use `86400000` for filler documentation, `0` for missed attacks

### CWL Day (CWLDAY)
- **0**: At each day's war end
- **3600000** (1h): 1 hour before each day ends (for reminders)

### Raid Weekend (RAID)
- **0**: At raid end
- **3600000** (1h): 1 hour before raid ends (for reminders)
- **7200000** (2h): 2 hours before raid ends

## Database Tables Reference

### listening_events
```sql
id BIGSERIAL PRIMARY KEY
clan_tag TEXT
listeningtype TEXT (cs, cw, cwlday, raid)
listeningvalue BIGINT (duration in ms)
actiontype TEXT (infomessage, kickpoint, custommessage, filler, cwdonator)
channel_id TEXT (Discord channel ID)
actionvalues JSONB (configuration data)
```

### cw_fillers (Filler Tracking)
```sql
id BIGSERIAL PRIMARY KEY
clan_tag TEXT
player_tag TEXT
war_end_time TIMESTAMP (links fillers to specific wars)
created_at TIMESTAMP
UNIQUE(clan_tag, player_tag, war_end_time)
```

### achievement_data (Clan Games Tracking)
```sql
id BIGSERIAL PRIMARY KEY
player_tag TEXT
type TEXT (e.g., 'CLANGAMES_POINTS')
time TIMESTAMP (when data was recorded)
data INTEGER/JSONB (achievement value or full data)
UNIQUE(player_tag, type, time)
```

### kickpoints (Auto-Generated)
```sql
id BIGSERIAL PRIMARY KEY
player_tag TEXT
date TIMESTAMP
amount INTEGER
description TEXT
created_by_discord_id TEXT
created_at TIMESTAMP
expires_at TIMESTAMP
clan_tag TEXT
updated_at TIMESTAMP
```

## Best Practices

### 1. Clan Games Setup
```
# Monitor at end with info message first
/listeningevent add clan:YourClan type:cs duration:0 actiontype:infomessage channel:#monitoring

# After testing, switch to kickpoints
/listeningevent add clan:YourClan type:cs duration:0 actiontype:kickpoint channel:#violations kickpoint_reason:CG_Low
```

### 2. Complete CW Coverage
```
# Step 1: Document fillers at war start
/listeningevent add clan:YourClan type:cw duration:86400000 actiontype:filler channel:#war-roster

# Step 2: Track missed attacks at war end (fillers auto-excluded)
/listeningevent add clan:YourClan type:cw duration:0 actiontype:kickpoint channel:#war-violations kickpoint_reason:CW_Missed
```

### 3. CWL Daily Tracking
```
# Daily reminder 1h before
/listeningevent add clan:YourClan type:cwlday duration:3600000 actiontype:custommessage channel:#cwl-reminders

# Daily violations at end
/listeningevent add clan:YourClan type:cwlday duration:0 actiontype:kickpoint channel:#cwl-violations kickpoint_reason:CWL_NoAttack
```

### 4. Raid Weekend Monitoring
```
# Reminder before end
/listeningevent add clan:YourClan type:raid duration:7200000 actiontype:custommessage channel:#raid-reminders

# Violations at end
/listeningevent add clan:YourClan type:raid duration:0 actiontype:kickpoint channel:#raid-violations kickpoint_reason:Raid_Incomplete
```

## Maintenance and Monitoring

### View Active Events
```
/listeningevent list
/listeningevent list clan:LOST_F2P
```

### Remove Events
```
/listeningevent remove id:123
```

### Database Maintenance
```sql
-- Clean old achievement data (run monthly)
DELETE FROM achievement_data WHERE time < NOW() - INTERVAL '6 months';

-- Clean old fillers (run weekly - though auto-cleaned after wars)
DELETE FROM cw_fillers WHERE created_at < NOW() - INTERVAL '14 days';

-- Check event activity
SELECT clan_tag, listeningtype, actiontype, COUNT(*) 
FROM listening_events 
GROUP BY clan_tag, listeningtype, actiontype;
```

## Troubleshooting

### Event Not Firing
- Check event exists: `/listeningevent list`
- Verify bot has restarted since creation
- Check event type matches current game state
- Verify duration calculation is correct

### Fillers Not Excluded
- Ensure filler event fired during preparation phase
- Check `cw_fillers` table has entries for the war
- Verify war_end_time matches between filler and missed attack events
- Confirm both events use same clan_tag

### Achievement Data Missing
- Verify data is being saved to `achievement_data` table
- Check timestamps match hardcoded dates (22nd 7am, 28th 12pm)
- Ensure type is 'CLANGAMES_POINTS'
- Data must exist at both start and end timestamps

### Kickpoints Not Created
- Verify kickpoint_reason exists: `/kpinfo clan:YourClan`
- Check clan has kickpoint settings: `/clanconfig`
- Ensure actiontype is exactly "kickpoint"
- Verify user has proper permissions

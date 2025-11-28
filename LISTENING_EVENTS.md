# Listening Events - Automated Event Tracking

## Overview
The Listening Events feature automates tracking and notification for various Clash of Clans clan events, including Clan Games, Clan Wars, Clan War Leagues, and Raids.

## Command Usage

### `/listeningevent add`
Creates a new listening event for a clan with inline parameters (no modal required).

**Parameters:**
- `clan` (required): The clan to monitor (autocomplete enabled)
- `type` (required): Type of event to track (choice selection)
  - `Clan Games` - Clan Games event
  - `Clan War` - Clan War event
  - `CWL Tag` - CWL Day event
  - `Raid` - Raid Weekend event
- `duration` (required): Time in milliseconds before event end to trigger
  - Example: `3600000` for 1 hour before end
  - Example: `0` to trigger at event end
- `actiontype` (required): What action to take when event fires (choice selection)
  - `Info-Nachricht` - Send informational message only (available for all types)
  - `Kickpoint` - Automatically add kickpoints to violators (available for all types)
  - `Benutzerdefinierte Nachricht` - Send a custom message (available for all types)
  - `CW Donator` - CW donor selection (CW only)
  - `Filler` - Check war preferences / filler action (CW only)
  - `Raidfails` - District attack analysis / bad hits detection (Raid only)
- `channel` (required): Discord channel where messages are sent (channel picker)
- `kickpoint_reason` (optional): Kickpoint reason to use (autocomplete from clan's configured reasons)
  - **Required** when `actiontype` is `Kickpoint`
  - **Optional** for `Raidfails` - if provided, will add kickpoints
  - Autocomplete shows reasons configured for the selected clan

**Note on Action Type Availability:**
- Action types are filtered based on the selected event type
- `CW Donator` and `Filler` only appear when `type` is `Clan War`
- `Raidfails` only appears when `type` is `Raid`

**Example Usage:**
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:0 actiontype:Kickpoint channel:#war-logs kickpoint_reason:CW_Attack_vergessen
```

### `/listeningevent list`
Lists all configured listening events.

**Parameters:**
- `clan` (optional): Filter events by clan (autocomplete enabled)

**Output:**
- Event ID
- Clan name and tag
- Event type
- Action type
- Channel
- Time until event fires

### `/listeningevent remove`
Removes a listening event.

**Parameters:**
- `id` (required): The event ID to remove (from `/listeningevent list`)

## Event Types Explained

### Clan Games (CS)
Tracks player performance during Clan Games by comparing achievement points between start and end dates.

**Timing:**
- Start: 22nd of month at 7:00 AM (hardcoded)
- End: 28th of month at 12:00 PM (hardcoded)

**Behavior:**
- Compares points in `achievement_data` table at start vs end timestamps
- Reports players below threshold (e.g., < 4000 points)
- Can automatically add kickpoints for low performers

**Example Setup:**
```
/listeningevent add clan:LOST_F2P type:Clan_Games duration:0 actiontype:Kickpoint channel:#clan-logs kickpoint_reason:Clangames_unter_4000
```

### Clan War (CW)
Tracks clan war participation and attacks.

**Two Modes:**

1. **Filler Action (Start of War)**
   - Fires during preparation phase
   - Lists members opted OUT of war
   - Useful for checking war roster
   
   **Example Setup:**
   ```
   /listeningevent add clan:LOST_F2P type:Clan_War duration:86400000 actiontype:CW_Donator_(Filler) channel:#war-prep
   ```

2. **Missed Attacks (End of War)**
   - Fires when war ends
   - Reports members who didn't use all attacks
   - Can add kickpoints for missed attacks
   
   **Example Setup:**
   ```
   /listeningevent add clan:LOST_F2P type:Clan_War duration:0 actiontype:Kickpoint channel:#war-logs kickpoint_reason:CW_Angriff_verpasst
   ```

### CWL Day (CWLDAY)
Tracks missed attacks for each day of Clan War League.

**Behavior:**
- Fires at end of each CWL war day
- Reports members who didn't attack (CWL = 1 attack per day)
- Can add kickpoints for no-shows

**Example Setup:**
```
/listeningevent add clan:LOST_F2P type:CWL_Tag duration:0 actiontype:Kickpoint channel:#cwl-logs kickpoint_reason:CWL_Angriff_verpasst
```

### Raid Weekend (RAID)
Tracks raid participation, attacks, and district performance.

**Two Action Types:**

1. **Verpasste Hits (infomessage/kickpoint)**
   - Reports members who didn't complete all raid attacks
   - Reports members who didn't participate at all
   - Can add kickpoints for incomplete raids

   **Example Setup:**
   ```
   /listeningevent add clan:LOST_F2P type:Raid duration:0 actiontype:Kickpoint channel:#raid-logs kickpoint_reason:Raid_nicht_gemacht
   ```

2. **Raidfails (raidfails)**
   - Analyzes district attacks at raid end
   - Prompts for thresholds: max attacks on Capital Peak, max attacks on other districts
   - Identifies districts where total attacks exceeded threshold
   - Without kickpoint_reason: lists all attackers on over-attacked districts (info only)
   - With kickpoint_reason: penalizes player(s) with most attacks on over-attacked districts
   - Configurable tie-breaker: penalize both players or neither when tied

   **Example Setup (info only):**
   ```
   /listeningevent add clan:LOST_F2P type:Raid duration:0 actiontype:Raidfails channel:#raid-logs
   ```
   
   **Example Setup (with kickpoints):**
   ```
   /listeningevent add clan:LOST_F2P type:Raid duration:0 actiontype:Raidfails channel:#raid-logs kickpoint_reason:Raidfail_zuviele_Angriffe
   ```

## Action Types Explained

### infomessage
Sends an informational message to the configured channel without taking any action.

**Availability:** All event types

**Use Cases:**
- Simple notifications
- Tracking without penalties

**Usage:**
```
/listeningevent add ... actiontype:Info-Nachricht channel:#notifications
```

### kickpoint
Automatically adds kickpoints to players who violate rules.

**Availability:** All event types

**Configuration:**
- Must specify `kickpoint_reason` parameter
- Reason must be configured via `/kpaddreason` first
- Amount comes from the kickpoint reason configuration
- Kickpoints expire based on clan settings

**Usage:**
```
/listeningevent add ... actiontype:Kickpoint channel:#penalties kickpoint_reason:CW_Missed_Attack
```

### cwdonator
Special action type for Clan War donor selection.

**Availability:** CW (Clan War) events only

**Behavior:**
- Selects random players to donate during war
- Can use list-based distribution
- Can exclude leaders from selection

**Usage:**
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:start actiontype:CW_Donator channel:#war-prep
```

### filler
Special action type for checking war fillers (opted-out members).

**Availability:** CW (Clan War) events only

**Behavior:**
- Checks which clan members are opted OUT of war but still in the roster
- Lists them in the channel
- Does NOT add kickpoints
- Useful for roster management

**Usage:**
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:start actiontype:Filler channel:#war-prep
```

### raidfails
Special action type for analyzing district attacks during Raid Weekend.

**Availability:** Raid events only

**Behavior:**
- Analyzes attack distribution across districts
- Identifies districts where total attacks exceeded configured thresholds
- Without kickpoint_reason: lists all attackers on over-attacked districts (info only)
- With kickpoint_reason: penalizes player(s) with most attacks on over-attacked districts
- Configurable tie-breaker: penalize both players or neither when tied

**Configuration:**
- Prompts for Capital Peak max attacks threshold
- Prompts for other districts max attacks threshold
- Prompts for tie-breaker behavior

**Usage (info only):**
```
/listeningevent add clan:LOST_F2P type:Raid duration:0 actiontype:Raidfails channel:#raid-logs
```

**Usage (with kickpoints):**
```
/listeningevent add clan:LOST_F2P type:Raid duration:0 actiontype:Raidfails channel:#raid-logs kickpoint_reason:Raidfail_zuviele_Angriffe
```

## Action Values JSON Format

**Note:** With the updated command interface, you no longer need to manually configure action values JSON. The command automatically builds the appropriate action values based on your parameter selections:

- When `actiontype` is `CW Donator (Filler)`: Automatically adds FILLER type
- When `actiontype` is `Kickpoint`: Uses the selected `kickpoint_reason` to configure the kickpoint

### Legacy Information (for reference)

If you need to manually configure events in the database, action values use this JSON format:

### Type-based Actions
```json
[{"type":"FILLER"}]
```
Used for filler action (war preference check).

### Reason-based Actions (Kickpoints)
```json
[{"reason":{"name":"Missed CW Attack","clan_tag":"#ABCD1234"}}]
```
Specifies which kickpoint reason to use. Must be a reason configured via `/kpaddreason`.

### Value-based Actions
```json
[{"value":4000}]
```
Can be used for thresholds (implementation-dependent).

### Multiple Actions
```json
[{"type":"REMINDER"}, {"value":24}]
```
Combine multiple action values (implementation-dependent).

## Database Schema

Events are stored in the `listening_events` table:

```sql
CREATE TABLE listening_events (
    id BIGSERIAL PRIMARY KEY,
    clan_tag TEXT NOT NULL,
    listeningtype TEXT NOT NULL,
    listeningvalue BIGINT NOT NULL,
    actiontype TEXT NOT NULL,
    channel_id TEXT NOT NULL,
    actionvalues JSONB
);
```

## Event Scheduling

- Events are scheduled when bot starts via `Bot.restartAllEvents()`
- Events calculate fire time based on:
  - Event type (determines base timestamp)
  - Duration value (offset from base)
- After firing, events remain in database but don't auto-reschedule
- Manual recreation required for recurring events

## Tips and Best Practices

1. **Use descriptive kickpoint reasons**: Create clear reason names via `/kpaddreason` before using them in events

2. **Test with infomessage first**: Before adding kickpoints, test with `actiontype:Info-Nachricht` to verify behavior

3. **Use appropriate durations**:
   - Clan Games: `0` (fires at end)
   - CW Filler: `86400000` (24 hours before end = during prep)
   - CW Missed Attacks: `0` (at war end)
   - CWL: `0` (at each day end)
   - Raid: `0` (at raid end)

4. **Channel permissions**: Ensure bot has send message permissions in target channel

5. **Kickpoint reasons**: Configure reasons via `/kpaddreason` before using in events
   - Example: `/kpaddreason clan:LOST_F2P reason:CW_Angriff_verpasst amount:2`

6. **Monitor and adjust**: Use `/listeningevent list` to review active events

7. **Cleanup**: Remove old/test events with `/listeningevent remove`

8. **Autocomplete helps**: Use autocomplete for clans and kickpoint reasons to avoid typos

## Troubleshooting

**Event not firing:**
- Check event exists: `/listeningevent list`
- Verify fire time is in future
- Check bot has restarted since event creation
- Verify event type matches current game state (e.g., CW event needs active war)

**Messages not appearing:**
- Verify channel ID is correct
- Check bot permissions in channel
- Ensure bot is online

**Kickpoints not added:**
- Verify action type is `kickpoint`
- Check clan has kickpoint settings configured
- Verify players are in clan database

**API errors:**
- CoC API rate limits may cause issues
- War/raid must be active for related events
- Check API key is valid in bot configuration

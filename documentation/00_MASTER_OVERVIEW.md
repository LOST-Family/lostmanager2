# LostManager2 - Complete System Overview

## Table of Contents
1. [Bot Overview](#bot-overview)
2. [Architecture](#architecture)
3. [Commands Reference](#commands-reference)
4. [Data Structures](#data-structures)
5. [Database Schema](#database-schema)
6. [API Integration](#api-integration)
7. [Event System](#event-system)
8. [Configuration](#configuration)

---

## Bot Overview

**LostManager2** is a comprehensive Discord bot designed for managing Clash of Clans (CoC) clans within a Discord server. It is specifically built for the "Lost Family" clan organization but can be adapted for other clan families.

### Primary Purpose
The bot serves as a centralized management system for:
- **Player Verification**: Linking Discord accounts to Clash of Clans player tags
- **Clan Management**: Tracking members, roles, and clan configurations
- **Kickpoints System**: Automated penalty tracking for rule violations
- **Event Automation**: Automated monitoring of Clan Games, Wars, CWL, and Raids
- **Role Synchronization**: Keeping Discord roles in sync with in-game clan ranks
- **Statistics Tracking**: Historical data for wins, donations, and achievements

### Technology Stack
- **Language**: Java
- **Framework**: JDA (Java Discord API) 5.0.0-alpha.14
- **Database**: PostgreSQL 42.7.7
- **CoC API**: Clash of Clans official API
- **Build Tool**: Maven
- **JSON Processing**: org.json 20230227, Jackson 2.15.2

### Bot Version
Current version: **2.1.0**

---

## Architecture

### Package Structure
```
src/main/java/
├── lostmanager/          # Core bot initialization and event scheduling
│   └── Bot.java          # Main entry point, command registration, schedulers
├── commands/             # All Discord slash commands
│   ├── discord/          # Discord-specific commands
│   │   ├── admin/        # Admin commands (delete, restart, reactions)
│   │   └── util/         # Utility commands (checkreacts, teamcheck)
│   └── coc/              # Clash of Clans commands
│       ├── links/        # Account linking (verify, link, relink, unlink)
│       ├── memberlist/   # Member management (add, remove, edit, list, transfer)
│       ├── kickpoints/   # Kickpoint system (add, remove, edit, info)
│       └── util/         # CoC utilities (cwdonator, raidping, wins, setnick)
├── datawrapper/          # Data models and API wrappers
│   ├── Player.java       # Player data and CoC API integration
│   ├── Clan.java         # Clan data and CoC API integration
│   ├── User.java         # Discord user data
│   ├── Kickpoint.java    # Kickpoint records
│   ├── KickpointReason.java # Kickpoint reason templates
│   ├── ListeningEvent.java  # Automated event configuration
│   ├── AchievementData.java # Historical achievement tracking
│   └── ActionValue.java  # Event action configuration
├── dbutil/               # Database utilities
│   ├── Connection.java   # Database connection management
│   ├── DBUtil.java       # SQL query helpers
│   └── DBManager.java    # High-level database operations
└── util/                 # General utilities
    ├── MessageUtil.java  # Discord message formatting
    ├── SeasonUtil.java   # CoC season calculations
    ├── Tuple.java        # Generic tuple class
    └── Triplet.java      # Generic triplet class
```

### Core Components

#### 1. Bot Initialization (Bot.java)
- Loads environment variables for API keys and configuration
- Establishes database connection
- Registers all Discord slash commands
- Initializes event schedulers for:
  - Name updates (every 2 hours)
  - Clan Games tracking (22nd-28th monthly)
  - Season wins tracking (start/end of season)
  - Listening events polling (every 2 minutes)

#### 2. Command Handlers
Each command is implemented as a separate class extending `ListenerAdapter`:
- `onSlashCommandInteraction()`: Handles command execution
- `onCommandAutoCompleteInteraction()`: Provides autocomplete suggestions
- `onModalInteraction()`: Handles modal form submissions
- `onButtonInteraction()`: Handles button clicks

#### 3. Data Wrappers
Encapsulate both database and API data:
- **Lazy Loading**: Data fetched only when needed
- **Caching**: Results cached until `refreshData()` called
- **Dual Sources**: Database (DB) and API data available separately
- **Type Safety**: Strong typing with enums for roles, types, etc.

#### 4. Event Scheduling System
Three separate schedulers:
- **schedulernames**: Updates player names from API
- **schedulertasks**: Runs automated tasks (Clan Games, Wins, Events)
- **Event Polling**: Unified system checking all events every 2 minutes

---

## Commands Reference

### Discord Admin Commands
Located in: `commands/discord/admin/`

#### `/restart`
- **Purpose**: Restarts the bot
- **Permissions**: Admin only
- **Files**: `restart.java`

#### `/deletemessages`
- **Purpose**: Bulk delete messages in a channel
- **Parameters**: 
  - `amount` (integer): Number of messages to delete
- **Permissions**: Manage Messages
- **Files**: `deletemessages.java`

#### `/reactionsrole`
- **Purpose**: Give roles to users who reacted with specific emoji
- **Parameters**:
  - `messagelink` (string): Link to the message
  - `emoji` (string): The emoji to check
  - `role` (role): Role to assign
- **Permissions**: Manage Roles
- **Files**: `reactionsrole.java`

### Discord Utility Commands
Located in: `commands/discord/util/`

#### `/checkreacts`
- **Purpose**: Check which members of a role reacted to a message
- **Parameters**:
  - `role` (role): Role to check
  - `message_link` (string): Link to message
  - `emoji` (string): Emoji to check for
- **Files**: `checkreacts.java`

#### `/teamcheck`
- **Purpose**: Check team distribution of members across multiple team roles
- **Parameters**:
  - `memberrole` (role): Primary member role
  - `team_role_1` through `team_role_5` (roles): Team roles to check
  - `memberrole_2` (role, optional): Second member role

### CoC Linking Commands
Located in: `commands/coc/links/`

#### `/verify`
- **Purpose**: Link Discord account to CoC account (self-service)
- **Parameters**:
  - `tag` (string): Player tag
  - `apitoken` (string): API token from in-game settings
- **Process**:
  1. Validates API token with CoC API
  2. Creates player record in database
  3. Saves initial wins data
  4. Assigns verified role
  5. Sets nickname to player name
- **Files**: `verify.java`

#### `/link`
- **Purpose**: Link CoC account to Discord user (admin function)
- **Parameters**:
  - `tag` (string): Player tag
  - `user` (mentionable): Discord user to link
  - `userid` (string): Alternative to user mention
- **Permissions**: Co-Leader or higher
- **Files**: `link.java`

#### `/relink`
- **Purpose**: Change Discord account linked to existing player
- **Parameters**:
  - `tag` (string, autocomplete): Player tag
  - `user` or `userid`: New Discord account
- **Permissions**: Co-Leader or higher
- **Files**: `relink.java`

#### `/unlink`
- **Purpose**: Remove link between Discord and CoC account
- **Parameters**:
  - `tag` (string, autocomplete): Player tag to unlink
- **Permissions**: Co-Leader or higher
- **Files**: `unlink.java`

#### `/playerinfo`
- **Purpose**: Show information about a player or all accounts of a user
- **Parameters**:
  - `user` (mentionable, optional): Discord user to lookup
  - `player` (string, optional, autocomplete): Player tag to lookup
- **Output**:
  - Player name and tag
  - Linked Discord user
  - Current clan and role
  - Kickpoints
  - Wins statistics
- **Files**: `playerinfo.java`

### CoC Member Management Commands
Located in: `commands/coc/memberlist/`

#### `/addmember`
- **Purpose**: Add a player to a clan in the database
- **Parameters**:
  - `clan` (string, autocomplete): Target clan
  - `player` (string, autocomplete): Player to add
  - `role` (string, autocomplete): Clan role (Leader, Co-Leader, Elder, Member)
- **Permissions**: Co-Leader or higher
- **Files**: `addmember.java`

#### `/removemember`
- **Purpose**: Remove a player from their clan
- **Parameters**:
  - `player` (string, autocomplete): Player to remove
- **Permissions**: Co-Leader or higher
- **Notes**: Assigns "ex-member" role if configured
- **Files**: `removemember.java`

#### `/transfermember`
- **Purpose**: Move a player from one clan to another
- **Parameters**:
  - `player` (string, autocomplete): Player to transfer
  - `clan` (string, autocomplete): Destination clan
- **Permissions**: Co-Leader or higher
- **Files**: `transfermember.java`

#### `/editmember`
- **Purpose**: Change a player's clan role
- **Parameters**:
  - `player` (string, autocomplete): Player to edit
  - `role` (string, autocomplete): New role
- **Permissions**: Co-Leader or higher
- **Files**: `editmember.java`

#### `/listmembers`
- **Purpose**: List all members of a clan
- **Parameters**:
  - `clan` (string, autocomplete): Clan to list
- **Output**: Formatted list by role with player names and tags
- **Files**: `listmembers.java`

#### `/memberstatus`
- **Purpose**: Check synchronization between database and API
- **Parameters**:
  - `clan` (string, autocomplete): Clan to check
  - `disable_rolecheck` (string, optional): Set to "true" to skip role check
- **Output**:
  - Players in API but not DB
  - Players in DB but not API
  - Role mismatches
  - Missing Discord roles
- **Files**: `memberstatus.java`

#### `/cwlmemberstatus`
- **Purpose**: Check which members of a role are in a CWL roster
- **Parameters**:
  - `team_role` (role): Team role to check
  - `cwl_clan_tag` (string, autocomplete): CWL clan tag (can be side clan)
- **Output**: Lists members in/not in CWL roster
- **Files**: `cwlmemberstatus.java`

### CoC Kickpoints Commands
Located in: `commands/coc/kickpoints/`

The kickpoints system tracks violations and penalties for clan members.

#### `/kpaddreason`
- **Purpose**: Create a kickpoint reason template
- **Parameters**:
  - `clan` (string, autocomplete): Clan this reason applies to
  - `reason` (string): Reason name/description
  - `amount` (integer): Kickpoint amount
- **Permissions**: Co-Leader or higher
- **Files**: `kpaddreason.java`

#### `/kpremovereason`
- **Purpose**: Delete a kickpoint reason template
- **Parameters**:
  - `clan` (string, autocomplete): Clan
  - `reason` (string, autocomplete): Reason to remove
- **Permissions**: Co-Leader or higher
- **Files**: `kpremovereason.java`

#### `/kpeditreason`
- **Purpose**: Change the amount for a reason template
- **Parameters**:
  - `clan` (string, autocomplete): Clan
  - `reason` (string, autocomplete): Reason to edit
  - `amount` (integer): New kickpoint amount
- **Permissions**: Co-Leader or higher
- **Files**: `kpeditreason.java`

#### `/kpadd`
- **Purpose**: Add kickpoints to a player
- **Parameters**:
  - `player` (string, autocomplete): Player to penalize
  - `reason` (string, autocomplete, optional): Reason template
- **Process**:
  1. Opens modal for details (reason, amount, date, custom notes)
  2. If reason template provided, pre-fills fields
  3. Validates kickpoint limit
  4. Creates kickpoint record with expiration date
- **Permissions**: Co-Leader or higher of player's clan
- **Files**: `kpadd.java`

#### `/kpremove`
- **Purpose**: Delete a kickpoint record
- **Parameters**:
  - `id` (integer): Kickpoint ID (from `/kpmember`)
- **Permissions**: Co-Leader or higher of player's clan
- **Files**: `kpremove.java`

#### `/kpedit`
- **Purpose**: Edit an existing kickpoint
- **Parameters**:
  - `id` (integer): Kickpoint ID
- **Process**: Opens modal with current values to edit
- **Permissions**: Co-Leader or higher of player's clan
- **Files**: `kpedit.java`

#### `/kpmember`
- **Purpose**: View all kickpoints for a player
- **Parameters**:
  - `player` (string, autocomplete): Player to view
- **Output**:
  - Total active kickpoints
  - List of each kickpoint (ID, reason, amount, date, expires)
  - Warning if over limit
- **Files**: `kpmember.java`

#### `/kpclan`
- **Purpose**: View kickpoint summary for entire clan
- **Parameters**:
  - `clan` (string, autocomplete): Clan to view
- **Output**: List of all members with kickpoints, sorted by amount
- **Files**: `kpclan.java`

#### `/kpinfo`
- **Purpose**: List all kickpoint reason templates for a clan
- **Parameters**:
  - `clan` (string, autocomplete): Clan
- **Output**: All reasons with their amounts
- **Files**: `kpinfo.java`

#### `/clanconfig`
- **Purpose**: Configure clan settings
- **Parameters**:
  - `clan` (string, autocomplete): Clan to configure
- **Configuration Options**:
  - Max kickpoints allowed
  - Minimum season wins required
  - Days until kickpoints expire
  - Discord role IDs for each clan rank
- **Process**: Opens modal with current values
- **Permissions**: Leader only
- **Files**: `clanconfig.java`

### CoC Utility Commands
Located in: `commands/coc/util/`

#### `/cwdonator`
- **Purpose**: Randomly select war donors for a clan war
- **Parameters**:
  - `clan` (string, autocomplete): Clan in war
  - `exclude_leaders` (string, optional): "true" to exclude leaders/co-leaders
  - `use_lists` (string, optional): "true" for list-based distribution
- **Algorithm**: Uses war size to determine donor count (mappings for 5v5 to 50v50)
- **List Mode**: Tracks previous selections in database to ensure fair rotation
- **Files**: `cwdonator.java`, `cwdonator_lists_table.sql`

#### `/raidping`
- **Purpose**: Ping members who haven't completed raid attacks
- **Parameters**:
  - `clan` (string, autocomplete): Clan to check
- **Output**:
  - Lists members with incomplete raids
  - Mentions linked Discord users
  - Shows attacks used vs. available
- **Permissions**: Co-Leader or higher
- **Files**: `raidping.java`

#### `/checkroles`
- **Purpose**: Verify Discord roles match in-game clan ranks
- **Parameters**:
  - `clan` (string, autocomplete): Clan to check
- **Output**:
  - Statistics (linked/unlinked, correct/incorrect roles)
  - List of members with role issues
  - Refresh button to re-check
- **Permissions**: Co-Leader or higher
- **Files**: `checkroles.java`
- **Documentation**: `CHECKROLES_COMMAND.md`

#### `/setnick`
- **Purpose**: Set Discord nickname to in-game name
- **Parameters**:
  - `my_player` (string, autocomplete): Your player tag
  - `alias` (string, optional): Custom suffix (e.g., "| Red")
- **Format**: `<PlayerName> | <alias>` or just `<PlayerName>`
- **Files**: `setnick.java`

#### `/wins`
- **Purpose**: Show wins statistics for a player or clan
- **Parameters**:
  - `season` (string, autocomplete): Season (e.g., "2024-12")
  - `player` (string, optional, autocomplete): Specific player
  - `clan` (string, optional, autocomplete): Entire clan
- **Output**:
  - Player: Wins at start, end, and difference
  - Clan: All members sorted by wins gained
- **Files**: `wins.java`

#### `/listeningevent`
- **Purpose**: Manage automated event monitoring
- **Subcommands**:
  - `add`: Create new listening event
  - `list`: View all events
  - `remove`: Delete an event
- **Event Types**: Clan Games (CS), Clan War (CW), CWL Day (CWLDAY), Raid Weekend (RAID)
- **Action Types**: infomessage, kickpoint, cwdonator, filler, raidfails
- **Files**: `listeningevent.java`
- **Documentation**: `LISTENING_EVENTS.md`, `LISTENING_EVENTS_COMPLETE_GUIDE.md`, `IMPLEMENTATION_SUMMARY.md`

---

## Data Structures

### Player (Player.java)
Represents a Clash of Clans player with both database and API data.

**Key Fields:**
- `tag`: Player tag (primary identifier)
- `namedb` / `nameapi`: Name from database / API
- `clandb` / `clanapi`: Clan from database / API
- `roledb` / `roleapi`: Clan role from database / API
- `user`: Linked Discord user
- `kickpoints`: Active kickpoint records
- `kickpointstotal`: Sum of active kickpoints
- `warpreference`: Opted in/out of wars
- `currentRaidAttacks`: Raid attacks used
- `achievementDataAPI`: Current achievements from API
- `achievementDatasInDB`: Historical achievements from database

**Key Methods:**
- `getNameDB()` / `getNameAPI()`: Get name from source
- `getClanDB()` / `getClanAPI()`: Get clan from source
- `getRoleDB()` / `getRoleAPI()`: Get role from source
- `getUser()`: Get linked Discord user
- `IsLinked()`: Check if linked to Discord
- `AccExists()`: Check if player exists in database
- `getKickpoints()`: Get active kickpoint records
- `getKickpointsTotal()`: Get sum of active kickpoints
- `verifyCocTokenAPI()`: Verify API token for linking
- `addAchievementDataToDB()`: Save achievement snapshot
- `refreshData()`: Clear cache and reload on next access

**Enums:**
- `RoleType`: ADMIN, LEADER, COLEADER, ELDER, MEMBER, NOTINCLAN

### Clan (Clan.java)
Represents a Clash of Clans clan with configuration and event data.

**Key Fields:**
- `clan_tag`: Clan tag (primary identifier)
- `namedb` / `nameapi`: Name from database / API
- `playerlistdb` / `playerlistapi`: Member lists
- `cwactive`: Is clan war active
- `clanwarmembers`: Players in current war
- `CWEndTimeMillis`: War end timestamp
- `raidactive`: Is raid weekend active
- `raidmembers`: Players in raid
- `RaidEndTimeMillis`: Raid end timestamp
- `cwlactive`: Is CWL active
- `cwlmemberlist`: Players in CWL roster
- `CWLDayEndTimeMillis`: CWL day end timestamp
- `CGEndTimeMillis`: Clan Games end timestamp
- `max_kickpoints`: Max kickpoints before action
- `min_season_wins`: Minimum wins required per season
- `kickpoints_expire_after_days`: Kickpoint expiration period
- `kickpoint_reasons`: Available kickpoint reasons for clan

**Key Methods:**
- `getNameDB()` / `getNameAPI()`: Get name
- `getPlayersDB()` / `getPlayersAPI()`: Get member lists
- `isCWActive()`: Check if war active
- `getWarMemberList()`: Get war roster
- `getCWJson()`: Get raw war data from API
- `RaidActive()`: Check if raid active
- `getRaidMemberList()`: Get raid participants
- `getRaidJson()`: Get raw raid data from API
- `isCWLActive()`: Check if CWL active
- `getCWLMemberList()`: Get CWL roster
- `getCWLJson()` / `getCWLDayJson()`: Get CWL data from API
- `getMaxKickpoints()`: Get kickpoint limit
- `getMinSeasonWins()`: Get minimum wins requirement
- `getDaysKickpointsExpireAfter()`: Get expiration period
- `getKickpointReasons()`: Get reason templates
- `refreshData()`: Clear cache

**Enums:**
- `Role`: LEADER, COLEADER, ELDER, MEMBER

### User (User.java)
Represents a Discord user and their linked accounts.

**Key Fields:**
- `userid`: Discord user ID
- `linkedaccs`: List of linked player accounts
- `clanroles`: Map of clan tag to role type

**Key Methods:**
- `getUserID()`: Get Discord ID
- `getAllLinkedAccounts()`: Get all linked players
- `getMainAccount()`: Get primary linked account
- `getClanRoles()`: Get role in each clan
- `refreshData()`: Clear cache

### Kickpoint (Kickpoint.java)
Represents a single kickpoint record.

**Key Fields:**
- `id`: Unique kickpoint ID
- `playertag`: Player who received it
- `reason`: Why it was given
- `amount`: Point value
- `date`: When it was given
- `expires`: Expiration date

**Key Methods:**
- `getID()`: Get ID
- `getPlayerTag()`: Get player
- `getReason()`: Get reason text
- `getAmount()`: Get point value
- `getDate()`: Get creation date
- `getExpires()`: Get expiration date
- `isExpired()`: Check if expired

### KickpointReason (KickpointReason.java)
Represents a kickpoint reason template.

**Key Fields:**
- `name`: Reason name
- `amount`: Default kickpoint amount
- `clan_tag`: Clan this reason belongs to

**Key Methods:**
- `getName()`: Get reason name
- `getAmount()`: Get point value
- `getClanTag()`: Get clan

### ListeningEvent (ListeningEvent.java)
Represents an automated event monitoring configuration.

**Key Fields:**
- `id`: Event ID
- `clan_tag`: Clan to monitor
- `listeningtype`: Event type (CS, CW, CWLDAY, RAID, FIXTIMEINTERVAL)
- `listeningvalue`: Duration offset in milliseconds
- `actiontype`: Action to take (INFOMESSAGE, KICKPOINT, CWDONATOR, CUSTOMMESSAGE)
- `channel_id`: Discord channel for notifications
- `actionvalues`: JSON configuration for actions

**Key Methods:**
- `getID()`: Get event ID
- `getClanTag()`: Get clan
- `getListeningType()`: Get event type (enum)
- `getDurationUntilEnd()`: Get milliseconds until event fires
- `getTimestamp()`: Get calculated fire timestamp
- `getActionType()`: Get action type (enum)
- `getChannelID()`: Get Discord channel
- `getActionValues()`: Get action configuration list
- `fireEvent()`: Execute the event (called by scheduler)

**Enums:**
- `LISTENINGTYPE`: CS, CW, CWLDAY, RAID, FIXTIMEINTERVAL
- `ACTIONTYPE`: INFOMESSAGE, KICKPOINT, CWDONATOR, CUSTOMMESSAGE

**Fire Event Process:**
1. Validates event should fire (checks current game state)
2. Queries CoC API for event data
3. Processes data based on event type
4. Identifies violations/targets
5. Sends Discord message to channel
6. Applies actions (kickpoints, etc.) if configured

### AchievementData (AchievementData.java)
Represents a snapshot of player achievements at a point in time.

**Key Fields:**
- `player_tag`: Player
- `type`: Achievement type
- `time`: When snapshot taken
- `data`: Achievement data (JSON)

**Key Methods:**
- `getPlayerTag()`: Get player
- `getType()`: Get type
- `getTime()`: Get timestamp
- `getData()`: Get achievement data

**Enums:**
- `Type`: CLANGAMES_POINTS, WINS, DONATIONS, etc.

### ActionValue (ActionValue.java)
Represents configuration for a listening event action.

**Fields:**
- `type`: Action subtype (e.g., "FILLER")
- `reason`: Kickpoint reason to use
- `value`: Numeric threshold or configuration

---

## Database Schema

### Core Tables

#### `players`
Stores linked player accounts.
```sql
CREATE TABLE players (
    coc_tag TEXT PRIMARY KEY,
    discord_id TEXT,
    clan_tag TEXT,
    clan_role TEXT,
    name TEXT,
    FOREIGN KEY (clan_tag) REFERENCES clans(clan_tag)
);
```

#### `users`
Stores Discord users (minimal, mostly derived from players).
```sql
CREATE TABLE users (
    discord_id TEXT PRIMARY KEY
);
```

#### `clans`
Stores clan configurations.
```sql
CREATE TABLE clans (
    clan_tag TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    max_kickpoints BIGINT,
    min_season_wins BIGINT,
    kickpoints_expire_after_days INTEGER,
    leader_role_id TEXT,
    co_leader_role_id TEXT,
    elder_role_id TEXT,
    member_role_id TEXT
);
```

#### `kickpoints`
Stores individual kickpoint records.
```sql
CREATE TABLE kickpoints (
    id BIGSERIAL PRIMARY KEY,
    player_tag TEXT NOT NULL,
    reason TEXT NOT NULL,
    amount INTEGER NOT NULL,
    date TIMESTAMP NOT NULL,
    expires TIMESTAMP NOT NULL,
    FOREIGN KEY (player_tag) REFERENCES players(coc_tag)
);
```

#### `kickpoint_reasons`
Stores kickpoint reason templates.
```sql
CREATE TABLE kickpoint_reasons (
    name TEXT NOT NULL,
    clan_tag TEXT NOT NULL,
    amount INTEGER NOT NULL,
    PRIMARY KEY (name, clan_tag),
    FOREIGN KEY (clan_tag) REFERENCES clans(clan_tag)
);
```

#### `listening_events`
Stores automated event configurations.
```sql
CREATE TABLE listening_events (
    id BIGSERIAL PRIMARY KEY,
    clan_tag TEXT NOT NULL,
    listeningtype TEXT NOT NULL,
    listeningvalue BIGINT NOT NULL,
    actiontype TEXT NOT NULL,
    channel_id TEXT NOT NULL,
    actionvalues JSONB,
    FOREIGN KEY (clan_tag) REFERENCES clans(clan_tag)
);
```

#### `achievement_data`
Stores historical achievement snapshots.
```sql
CREATE TABLE achievement_data (
    id BIGSERIAL PRIMARY KEY,
    player_tag TEXT NOT NULL,
    type TEXT NOT NULL,
    time TIMESTAMP NOT NULL,
    data JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(player_tag, type, time)
);
CREATE INDEX idx_achievement_data_lookup ON achievement_data(player_tag, type, time);
```
See: `achievement_data_schema.sql`

### Auxiliary Tables

#### `cw_fillers`
Tracks players opted out of wars (fillers).
```sql
CREATE TABLE cw_fillers (
    id BIGSERIAL PRIMARY KEY,
    clan_tag TEXT NOT NULL,
    player_tag TEXT NOT NULL,
    war_end_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(clan_tag, player_tag, war_end_time)
);
```
See: `cw_fillers_table.sql`

#### `cwdonator_lists`
Tracks CW donor selections for fair rotation.
```sql
CREATE TABLE cwdonator_lists (
    clan_tag TEXT NOT NULL,
    player_tags TEXT[] NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    war_end_time TIMESTAMP,
    PRIMARY KEY (clan_tag, war_end_time)
);
```
See: `cwdonator_lists_table.sql`

#### `sideclans`
Stores side clans used for CWL.
```sql
CREATE TABLE sideclans (
    clan_tag TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    belongs_to TEXT
);
```
See: `sideclans_table.sql`

---

## API Integration

### Clash of Clans API

**Base URL**: `https://api.clashofclans.com/v1/`

**Authentication**: Bearer token via environment variable `LOST_MANAGER_API_KEY`

**Endpoints Used:**

1. **Player Information**
   - `GET /players/{playerTag}`
   - Returns: Name, clan, role, achievements, war preference

2. **Clan Information**
   - `GET /clans/{clanTag}`
   - Returns: Name, members list with roles

3. **Current War**
   - `GET /clans/{clanTag}/currentwar`
   - Returns: War state, members, attacks, end time

4. **CWL Group**
   - `GET /clans/{clanTag}/currentwar/leaguegroup`
   - Returns: CWL participants, rounds

5. **Capital Raid Seasons**
   - `GET /clans/{clanTag}/capitalraidseasons`
   - Returns: Raid participants, attacks, loot

6. **Token Verification**
   - `POST /players/{playerTag}/verifytoken`
   - Body: `{"token": "..."}`
   - Returns: Status (valid/invalid)

**Rate Limiting**: 
- CoC API has rate limits
- Bot implements retry logic with exponential backoff
- Event polling reduced to 2-minute intervals to minimize requests

**Error Handling**:
- Network errors: Retry with backoff
- 404 errors: Player/clan doesn't exist
- 403 errors: Private war log or invalid API key
- 503 errors: API maintenance, retry later

## Event System

### Scheduler Architecture

The bot uses Java's `ScheduledExecutorService` with three separate schedulers:

#### 1. Name Updates Scheduler (`schedulernames`)
- **Frequency**: Every 2 hours
- **Purpose**: Keep player names synchronized with CoC API
- **Process**:
  ```
  For each player in database:
    Fetch name from CoC API
    Update database if changed
  ```

#### 2. Tasks Scheduler (`schedulertasks`)
- **Purpose**: Runs all automated tasks and events
- **Tasks**:
  1. Clan Games tracking (start/end)
  2. Season wins tracking (start/end)
  3. Listening events polling
  4. Individual event executions

#### 3. Event Polling System
- **Frequency**: Every 2 minutes
- **Purpose**: Check all listening events and schedule upcoming ones
- **Process**:
  ```
  Every 2 minutes:
    Query all listening_events from database
    For each event:
      Calculate fire timestamp
      If within 5 minutes and not scheduled:
        Schedule event execution
      If overdue:
        Mark as scheduled (skip to prevent duplicates)
    
    For CW start events (duration = -1):
      Check clan war state
      If war just started:
        Fire all start events for clan
        Update clan state
      If war ended:
        Clear fired events tracker
  ```

### Event Types and Timing

#### Clan Games (CS)
- **Start**: 22nd of month at 7:00 AM (hardcoded)
- **End**: 28th of month at 12:00 PM (hardcoded)
- **Data Collection**: Snapshots taken at start and end
- **Fire Time**: Calculated from `getNext28thAt1pm()` minus duration
- **Validation**: Compares achievement_data for CLANGAMES_POINTS

**Example**:
```java
duration = 0 // Fire at end
fire_time = getNext28thAt1pm() - 0 = 28th at 1:00 PM
// (1 hour buffer after end for API propagation)
```

#### Clan War (CW)
- **Detection**: Queries `/currentwar` endpoint
- **Start Detection**: State changes from "notInWar"/"warEnded" to "preparation"/"inWar"
- **End Time**: From API `endTime` field
- **Fire Time**: 
  - `duration = -1` (start): Fires when war starts (state change detection)
  - `duration = 0` (end): Fires at war end
  - `duration = N`: Fires N milliseconds before war end
- **Validation**: Checks `isCWActive()` before firing

**Example**:
```java
// End event
duration = 0
fire_time = war.endTime - 0 = exactly at war end

// 1 hour before end
duration = 3600000
fire_time = war.endTime - 3600000 = 1 hour before end

// Start event
duration = -1
fire_time = triggered by state change detection
```

#### CWL Day (CWLDAY)
- **Detection**: Queries `/currentwar/leaguegroup`
- **Day End Time**: Each war day has individual end time
- **Fire Time**: Calculated from current CWL war end time minus duration
- **Validation**: Checks `isCWLActive()` before firing

**Example**:
```java
duration = 0
fire_time = cwl_day.endTime - 0 = at day end
```

#### Raid Weekend (RAID)
- **Detection**: Queries `/capitalraidseasons`
- **Start**: Friday at specific time (from API)
- **End**: Monday at specific time (from API)
- **Fire Time**: Calculated from raid end time minus duration
- **Validation**: Checks `RaidActive()` before firing

**Example**:
```java
duration = 0
fire_time = raid.endTime - 0 = at raid end
```

### Event Actions

When an event fires, the action type determines what happens:

#### INFOMESSAGE
- Sends informational message to channel
- Lists violations/status
- No automated actions

#### KICKPOINT
- Sends message listing violations
- Automatically adds kickpoints to violators
- Uses kickpoint reason from actionvalues
- Respects kickpoint expiration settings

#### CWDONATOR
- Randomly selects war donors
- Can use list-based rotation (tracks previous selections)
- Can exclude leaders/co-leaders
- Number of donors based on war size

#### FILLER (CW only)
- Lists members opted out of war
- Used during preparation phase
- Helps with roster management

#### RAIDFAILS (Raid only)
- Analyzes district attack distribution
- Identifies over-attacked districts
- Lists all attackers or assigns kickpoints to worst offenders
- Configurable thresholds and tie-breaker logic

### Event Execution Flow

```
1. Event Polling (every 2 minutes)
   ↓
2. Calculate Fire Time
   ↓
3. Is fire time within 5 minutes? → Yes
   ↓                                   ↓ No
4. Schedule execution              4. Skip (check again next poll)
   ↓
5. Wait until fire time
   ↓
6. Execute Event (with retry logic)
   ↓
7. Validate Should Fire (check game state)
   ↓
8. Query CoC API
   ↓
9. Process Data Based on Event Type
   ↓
10. Build Discord Message
   ↓
11. Send to Channel
   ↓
12. Execute Actions (kickpoints, etc.)
   ↓
13. Log Success/Failure
```

**Retry Logic**:
- Max 3 retries with exponential backoff (5s, 10s, 20s)
- Validates event should still fire before each attempt
- Logs failures for debugging

### State Management

**CW Start Events**:
- Tracks last known war state per clan (in-memory)
- Detects state transitions (notInWar → preparation/inWar)
- Fires all start events for clan when transition detected
- Tracks fired events per war to prevent duplicates
- Clears fired tracker when war ends

**Event Scheduling**:
- Tracks scheduled event IDs to prevent duplicate scheduling
- Cleans up old scheduled events after 1 hour
- Skips overdue events on bot restart to prevent spam

---

## Configuration

### Environment Variables

Required environment variables (loaded in `Bot.main()`):

```bash
# Discord
LOST_MANAGER_TOKEN=<Discord bot token>
DISCORD_GUILD_ID=<Discord server ID>
DISCORD_VERIFIED_ROLE_ID=<Role ID for verified members>
DISCORD_EX_MEMBER_ROLE_ID=<Role ID for ex-members>

# Database
LOST_MANAGER_DB_URL=<PostgreSQL connection URL>
LOST_MANAGER_DB_USER=<Database username>
LOST_MANAGER_DB_PASSWORD=<Database password>

# APIs
LOST_MANAGER_API_KEY=<Clash of Clans API key>
```

### Deployment

**Build**: Maven-based Java project
```bash
mvn clean package
```

**Run**:
```bash
java -jar target/lostcrmanager-0.0.1-SNAPSHOT.jar
```

**Dependencies** (from pom.xml):
- JDA 5.0.0-alpha.14 (Discord API)
- PostgreSQL 42.7.7 (Database driver)
- org.json 20230227 (JSON parsing)
- Jackson 2.15.2 (JSON serialization)

### Database Initialization

On startup, the bot:
1. Checks database connection
2. Runs `dbutil.Connection.tablesExists()` to create missing tables
3. Applies schema updates from .sql files if needed

**Manual Schema Updates**:
- `achievement_data_schema.sql`: Achievement tracking
- `cw_fillers_table.sql`: War fillers tracking
- `cwdonator_lists_table.sql`: Donor rotation tracking
- `sideclans_table.sql`: Side clans for CWL

---

## Special Features

### Kickpoint Expiration
- Configurable per clan (e.g., 30 days)
- Set via `/clanconfig`
- Calculated as: `expires = date + expire_days`
- Expired kickpoints excluded from total
- Not automatically deleted (kept for records)

### Dual Data Sources
Many data wrappers maintain both database and API data:
- **Database**: Authoritative for clan membership and links
- **API**: Real-time game data (war status, achievements, etc.)
- **Synchronization**: `/memberstatus` command identifies mismatches

### Autocomplete
Commands with autocomplete use `onCommandAutoCompleteInteraction()`:
- **Clans**: Lists clans from database
- **Players**: Lists players (filtered by clan if applicable)
- **Roles**: Lists available clan roles
- **Reasons**: Lists kickpoint reasons for selected clan
- **Seasons**: Lists recent seasons for wins tracking

### Role Synchronization
- **Verified Role**: Assigned on first account link
- **Ex-Member Role**: Assigned on clan removal (if configured)
- **Clan Roles**: Can be configured per clan in `/clanconfig`
  - Leader, Co-Leader, Elder, Member roles
  - `/checkroles` verifies synchronization
  - Manual role assignment may be needed after promotions/demotions

### Name Updates
- Player names updated from API every 2 hours
- Prevents stale names in database
- Used for Discord nicknames and display

### Achievement Tracking
Historical snapshots saved for:
- **Clan Games Points**: Start (22nd) and end (28th) of each month
- **Wins**: Start and end of each season
- Used for `/wins` command and Clan Games event tracking
- Auto-cleanup after 6 months (see `achievement_data_schema.sql`)

---

## Error Handling

### Command Errors
All commands implement error handling with user-friendly messages:
- Missing parameters: "Der Parameter ist erforderlich!"
- Permission denied: "Du musst mindestens Vize-Anführer sein..."
- Not found: "Dieser Spieler existiert nicht..."
- API errors: "Fehler beim Abrufen der API-Daten"

### Database Errors
- Connection retry logic in `DBUtil`
- Duplicate key violations caught and reported
- Prepared statements prevent SQL injection

### API Errors
- Network timeouts: Retry with backoff
- Rate limiting: Queued with delays
- Invalid responses: Logged and skipped

### Event Execution Errors
- Validation before firing (checks game state)
- Retry logic (up to 3 attempts)
- Detailed error logging
- Graceful degradation (skip failed events)

---

## Permissions Model

### Bot Permissions
- Co-Leader or higher of any clan: Most management commands
- Leader only: `/clanconfig`, critical settings
- Admin role: Special commands like `/restart`

**Permission Check Pattern**:
```java
User user = new User(event.getUser().getId());
HashMap<String, Player.RoleType> roles = user.getClanRoles();
boolean hasPermission = false;
for (String clanTag : roles.keySet()) {
    RoleType role = roles.get(clanTag);
    if (role == LEADER || role == COLEADER || role == ADMIN) {
        hasPermission = true;
        break;
    }
}
```

### Discord Permissions
Some commands require Discord permissions:
- `/deletemessages`: Manage Messages
- `/reactionsrole`: Manage Roles
- Bot needs: Send Messages, Embed Links, Manage Nicknames, Manage Roles

---

## Logging and Monitoring

### Console Output
- Startup messages: Database connection, event initialization
- Scheduled task execution: "Es werden alle Clanspieldaten übertragen..."
- Event firing: "Executing event {id} (attempt {n})"
- Errors: Full stack traces to stderr

### Event Logging
- Event scheduling: "Scheduling event {id} to fire in {n} minutes"
- Event validation: "{Type} event validation: {result}"
- Event success: "Event {id} executed successfully"
- Event failure: "Event {id} failed after {n} attempts"

### Database Logging
Some tables include audit fields:
- `created_at`: Timestamp of creation
- `updated_at`: Timestamp of last update (where applicable)

---

## Maintenance Tasks

### Regular Maintenance
1. **Achievement Data Cleanup**: Run every 6 months
   ```sql
   SELECT cleanup_old_achievement_data();
   ```

2. **CW Fillers Cleanup**: Run weekly
   ```sql
   DELETE FROM cw_fillers WHERE war_end_time < NOW() - INTERVAL '14 days';
   ```

3. **Expired Kickpoints**: Not auto-deleted, but excluded from totals

### Monitoring
- Check console logs for errors
- Monitor API rate limits
- Verify event execution success rates
- Check database connection stability

### Backups
- Regular PostgreSQL backups recommended
- Critical tables: `players`, `clans`, `kickpoints`, `achievement_data`

---

## Development Notes

### Code Style
- German language in user-facing messages
- PascalCase for class names
- camelCase for method names
- lowercase for package names (non-standard but consistent)

### Testing
- No formal test suite included
- Testing via Discord commands in development server
- Database should be separate for dev/prod

### Future Enhancements
Potential improvements mentioned in documentation:
- Recurring events (auto-recreation)
- Custom message templates
- Event statistics and reporting
- Webhook integration
- Mobile app notifications

---

## Troubleshooting Guide

### Bot Won't Start
1. Check environment variables are set
2. Verify database connection
3. Check PostgreSQL is running
4. Verify Discord token is valid

### Commands Not Responding
1. Check bot is online in Discord
2. Verify bot has required permissions
3. Check console for errors
4. Ensure guild_id matches your server

### Events Not Firing
1. Run `/listeningevent list` to verify event exists
2. Check fire time is in the future
3. Verify event polling is running (console logs)
4. Check game state (war/raid must be active)

### Database Errors
1. Check connection string in environment
2. Verify database credentials
3. Check PostgreSQL logs
4. Run table creation scripts manually if needed

### API Errors
1. Verify CoC API key is valid
2. Check rate limiting (reduce event frequency)
3. Check API status at https://developer.clashofclans.com/
4. Review error logs for specific issues

---

## Glossary

- **CoC**: Clash of Clans
- **CW**: Clan War
- **CWL**: Clan War League
- **CS**: Clan Games (German: Clanspiele)
- **KP**: Kickpoints
- **API**: Application Programming Interface
- **Tag**: Unique identifier for CoC players/clans (e.g., #ABCD1234)
- **Filler**: Player opted out of war but still in clan roster
- **Side Clan**: Additional clan used for CWL participation

---

## Version History

### 2.1.0 (Current)
- Unified event polling system
- CW start trigger detection
- Raid fails analysis
- Various bug fixes and improvements

### Previous Versions
Documentation for earlier versions not included in current codebase.

---

**End of Master Overview**

For detailed information on specific components, see the individual documentation files in this directory.

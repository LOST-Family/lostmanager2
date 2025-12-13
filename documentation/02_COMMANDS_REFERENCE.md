# Commands Reference - Complete Guide

This document provides comprehensive documentation for all Discord slash commands in LostManager2.

---

## Table of Contents

### Discord Commands
- [restart](#restart) - Restart the bot
- [deletemessages](#deletemessages) - Bulk delete messages
- [reactionsrole](#reactionsrole) - Give roles based on reactions
- [checkreacts](#checkreacts) - Check reactions by role
- [teamcheck](#teamcheck) - Check team distribution
- [lmagent](#lmagent) - AI assistant

### Player Linking
- [verify](#verify) - Self-service account linking
- [link](#link) - Admin account linking
- [relink](#relink) - Change linked Discord account
- [unlink](#unlink) - Remove account link
- [playerinfo](#playerinfo) - Show player/user information

### Member Management
- [addmember](#addmember) - Add player to clan
- [removemember](#removemember) - Remove player from clan
- [transfermember](#transfermember) - Transfer player between clans
- [editmember](#editmember) - Change player's clan role
- [listmembers](#listmembers) - List clan members
- [memberstatus](#memberstatus) - Check DB/API synchronization
- [cwlmemberstatus](#cwlmemberstatus) - Check CWL roster

### Kickpoints System
- [kpaddreason](#kpaddreason) - Create reason template
- [kpremovereason](#kpremovereason) - Delete reason template
- [kpeditreason](#kpeditreason) - Edit reason template
- [kpadd](#kpadd) - Add kickpoints to player
- [kpremove](#kpremove) - Remove kickpoint record
- [kpedit](#kpedit) - Edit kickpoint record
- [kpmember](#kpmember) - View player's kickpoints
- [kpclan](#kpclan) - View clan kickpoints summary
- [kpinfo](#kpinfo) - List reason templates
- [clanconfig](#clanconfig) - Configure clan settings

### Utility Commands
- [cwdonator](#cwdonator) - Random donor selection
- [raidping](#raidping) - Ping members with incomplete raids
- [checkroles](#checkroles) - Verify Discord roles
- [setnick](#setnick) - Set Discord nickname
- [wins](#wins) - Show wins statistics
- [listeningevent](#listeningevent) - Manage automated events

---

## Discord Admin Commands

### restart

**Purpose**: Restart the bot application.

**Permissions**: Admin only (restricted in code)

**Parameters**: None

**Implementation**: `commands/discord/admin/restart.java`

**Usage**:
```
/restart
```

**Process**:
1. Confirms restart request
2. Calls `System.exit(0)`
3. Requires external process manager to restart

**Notes**:
- Bot must be run with auto-restart (e.g., systemd, PM2)
- All scheduled tasks will be reinitialized on restart
- In-memory data (event states) will be reset

---

### deletemessages

**Purpose**: Bulk delete messages in the current channel.

**Permissions**: Requires "Manage Messages" permission

**Parameters**:
- `amount` (integer, required): Number of messages to delete (1-100)

**Implementation**: `commands/discord/admin/deletemessages.java`

**Usage**:
```
/deletemessages amount:50
```

**Process**:
1. Validates amount (1-100)
2. Fetches last N messages from channel
3. Deletes them in bulk (Discord API limit: 100 max)

**Limitations**:
- Cannot delete messages older than 14 days (Discord limitation)
- Maximum 100 messages per command
- Requires bot to have Manage Messages permission

**Error Messages**:
- "Amount must be between 1 and 100"
- "Cannot delete messages older than 14 days"
- "Missing permissions"

---

### reactionsrole

**Purpose**: Assign a Discord role to all users who reacted with a specific emoji on a message.

**Permissions**: Requires "Manage Roles" permission

**Parameters**:
- `messagelink` (string, required): Discord message link (right-click message → Copy Message Link)
- `emoji` (string, required): Emoji to check for (can be custom emoji)
- `role` (role, required): Discord role to assign

**Implementation**: `commands/discord/admin/reactionsrole.java`

**Usage**:
```
/reactionsrole messagelink:https://discord.com/channels/... emoji:✅ role:@Verified
```

**Process**:
1. Parses message link to extract channel ID and message ID
2. Fetches message and its reactions
3. Gets list of users who reacted with specified emoji
4. Assigns role to each user
5. Reports success/failure count

**Message Link Format**:
```
https://discord.com/channels/{guild_id}/{channel_id}/{message_id}
```

**Notes**:
- Bot must have permission to view channel and read message history
- Bot's role must be higher than the role being assigned
- Custom emojis must be in format `<:name:id>` or use emoji picker

---

## Discord Utility Commands

### checkreacts

**Purpose**: Check which members of a specific role reacted (or didn't react) to a message.

**Permissions**: Anyone can use

**Parameters**:
- `role` (role, required): Role to check
- `message_link` (string, required): Discord message link
- `emoji` (string, required): Emoji to check for

**Implementation**: `commands/discord/util/checkreacts.java`

**Usage**:
```
/checkreacts role:@Members message_link:https://... emoji:✅
```

**Output**:
- List of role members who DID react
- List of role members who DID NOT react
- Statistics (total, reacted, didn't react)

**Use Cases**:
- Attendance checking for events
- Poll participation tracking
- Sign-up verification

---

### teamcheck

**Purpose**: Check the distribution of members across multiple team roles.

**Permissions**: Anyone can use

**Parameters**:
- `memberrole` (role, required): Primary member role to check
- `team_role_1` (role, required): First team role
- `team_role_2` through `team_role_5` (roles, optional): Additional team roles
- `memberrole_2` (role, optional): Second member role to include

**Implementation**: `commands/discord/util/teamcheck.java`

**Usage**:
```
/teamcheck memberrole:@Members team_role_1:@Red team_role_2:@Blue team_role_3:@Green
```

**Output**:
- Count per team
- List of members in each team
- List of members with no team
- List of members with multiple teams

**Use Cases**:
- CWL team assignment verification
- Tournament team distribution
- Ensuring balanced teams

---

### lmagent

**Purpose**: AI assistant powered by Google Gemini. Can answer questions about the bot or general Clash of Clans topics.

**Permissions**: Anyone can use

**Parameters**:
- `prompt` (string, required): Your question or request

**Implementation**: `commands/discord/util/lmagent.java`

**Usage**:
```
/lmagent prompt:How do I add kickpoints to a player?
/lmagent prompt:Explain CWL roster requirements
```

**Process**:
1. Loads system instructions from `lost_manager/context.txt`
2. Sends prompt with context to Google Gemini API
3. Returns AI response to channel

**Context**: The AI has knowledge about:
- Bot commands and usage
- Kickpoints system
- Clash of Clans rules and mechanics
- General bot administration

**Notes**:
- Requires `GOOGLE_GENAI_API_KEY` environment variable
- Response time depends on API
- May have rate limits from Google

---

## Player Linking Commands

### verify

**Purpose**: Self-service account linking. Players verify ownership of their CoC account using an API token.

**Permissions**: Anyone can use (for their own account)

**Parameters**:
- `tag` (string, required): Player tag (with or without #)
- `apitoken` (string, required): API token from CoC game settings

**Implementation**: `commands/coc/links/verify.java`

**Usage**:
```
/verify tag:#ABC123 apitoken:abc123def456
```

**How to Get API Token**:
1. Open Clash of Clans game
2. Go to Settings
3. Scroll to bottom
4. Copy API token

**Process**:
1. Validates player tag format
2. Checks if player exists in CoC API
3. Verifies API token with CoC servers
4. Creates database entry linking Discord ID to player tag
5. Saves initial wins snapshot for tracking
6. Assigns verified role
7. Sets Discord nickname to player name (for first account)

**Success Output**:
```
Player [Name] (#TAG) was successfully linked to user @Username.
```

**Error Cases**:
- Player already linked → Shows current link, suggests `/relink`
- Invalid API token → "API token doesn't match account"
- Player doesn't exist → "Player not found"

**Notes**:
- API tokens expire after ~1 hour
- Users can link multiple accounts (all to same Discord)
- First linked account determines Discord nickname

---

### link

**Purpose**: Admin function to link a CoC account to a Discord user without requiring API token verification.

**Permissions**: Co-Leader or higher in any clan

**Parameters**:
- `tag` (string, required): Player tag
- `user` (mentionable, optional): Discord user to link to (either this or userid required)
- `userid` (string, optional): Discord user ID (either this or user required)

**Implementation**: `commands/coc/links/link.java`

**Usage**:
```
/link tag:#ABC123 user:@Username
/link tag:#ABC123 userid:123456789012345678
```

**Process**:
1. Validates player exists in CoC
2. Checks if player already linked
3. Creates database entry
4. Saves initial wins snapshot
5. Assigns verified role
6. Sets nickname (if first account for user)

**Use Cases**:
- Linking accounts for users who can't get API token
- Bulk linking during initial setup
- Fixing broken links

**Notes**:
- No ownership verification - admin responsibility
- Use `/relink` if player already linked

---

### relink

**Purpose**: Change which Discord account a CoC player is linked to.

**Permissions**: Co-Leader or higher

**Parameters**:
- `tag` (string with autocomplete, required): Player tag to relink
- `user` (mentionable, optional): New Discord user
- `userid` (string, optional): New Discord user ID

**Implementation**: `commands/coc/links/relink.java`

**Usage**:
```
/relink tag:#ABC123 user:@NewUser
```

**Process**:
1. Validates player exists in database
2. Updates discord_id in database
3. Updates Discord nickname and roles as needed

**Use Cases**:
- User changed Discord account
- Account was linked to wrong user
- Taking over account from departed member

**Autocomplete**: Shows all linked players, filtered by input

---

### unlink

**Purpose**: Remove the link between a CoC account and Discord.

**Permissions**: Co-Leader or higher

**Parameters**:
- `tag` (string with autocomplete, required): Player tag to unlink

**Implementation**: `commands/coc/links/unlink.java`

**Usage**:
```
/unlink tag:#ABC123
```

**Process**:
1. Validates player is linked
2. Sets discord_id to NULL in database
3. Player remains in database with clan membership
4. Only the Discord link is removed

**Use Cases**:
- Player left Discord server
- Account sold/transferred
- Preparing for relink

**Notes**:
- Does NOT remove player from clan database
- Does NOT remove from clans
- Only removes Discord association
- Use `/removemember` to fully remove player

---

### playerinfo

**Purpose**: Show comprehensive information about a player or all accounts of a Discord user.

**Permissions**: Anyone can use

**Parameters** (at least one required):
- `user` (mentionable, optional): Discord user to lookup
- `player` (string with autocomplete, optional): Player tag to lookup

**Implementation**: `commands/coc/links/playerinfo.java`

**Usage**:
```
/playerinfo user:@Username
/playerinfo player:#ABC123
/playerinfo
```
(No parameters = your own accounts)

**Output (Player Mode)**:
```
Player: [Emoji] Name (#TAG)
Clan: ClanName (#CLANTAG) - Role
Discord: @Username
Kickpoints: 5 KP
Season Wins: 150 (last season)
```

**Output (User Mode)**:
```
Discord User: @Username (ID: ...)

Linked Accounts:
1. [Emoji] Player1 (#TAG1) - Clan1 - Role
2. [Emoji] Player2 (#TAG2) - Clan2 - Role
3. [Emoji] Player3 (#TAG3) - No Clan

Total: 3 accounts
Kickpoints by Account: ...
```

**Includes**:
- Player name and tag
- Current clan and role
- Linked Discord user
- Total kickpoints
- Recent wins data (if available)

**Use Cases**:
- Check who owns an account
- See all accounts of a user
- Quick kickpoint check
- Verify clan membership

---

## Member Management Commands

### addmember

**Purpose**: Add a player to a clan in the database. Does NOT add them in-game.

**Permissions**: Co-Leader or higher

**Parameters**:
- `clan` (string with autocomplete, required): Target clan
- `player` (string with autocomplete, required): Player to add
- `role` (string with autocomplete, required): Clan role to assign

**Implementation**: `commands/coc/memberlist/addmember.java`

**Usage**:
```
/addmember clan:LOST_F2P player:#ABC123 role:Elder
```

**Clan Roles** (autocomplete):
- Leader
- Co-Leader
- Elder
- Member

**Process**:
1. Validates player exists (must be linked via `/verify` or `/link` first)
2. Checks if player already in a clan
3. Inserts/updates clan membership in database
4. Updates player's clan_tag and clan_role

**Use Cases**:
- Adding new clan member after in-game join
- Syncing database after clan changes
- Initial clan setup

**Notes**:
- Player must be linked to Discord first
- Does NOT affect in-game clan
- Use `/memberstatus` to identify who needs to be added
- Use `/transfermember` to move between clans

---

### removemember

**Purpose**: Remove a player from their clan in the database. Does NOT kick from in-game clan.

**Permissions**: Co-Leader or higher

**Parameters**:
- `player` (string with autocomplete, required): Player to remove

**Implementation**: `commands/coc/memberlist/removemember.java`

**Usage**:
```
/removemember player:#ABC123
```

**Process**:
1. Validates player is in a clan
2. Sets clan_tag and clan_role to NULL
3. Assigns "ex-member" role if configured (DISCORD_EX_MEMBER_ROLE_ID)
4. Player remains linked to Discord

**Use Cases**:
- Player kicked from clan in-game
- Player left clan
- Database cleanup

**Notes**:
- Does NOT unlink from Discord
- Does NOT remove from database
- Use `/unlink` to remove Discord link
- Use `/transfermember` to move to different clan

---

### transfermember

**Purpose**: Move a player from one clan to another in the database.

**Permissions**: Co-Leader or higher

**Parameters**:
- `player` (string with autocomplete, required): Player to transfer
- `clan` (string with autocomplete, required): Destination clan

**Implementation**: `commands/coc/memberlist/transfermember.java`

**Usage**:
```
/transfermember player:#ABC123 clan:LOST_Elite
```

**Process**:
1. Validates player is currently in a clan
2. Updates player's clan_tag to new clan
3. Keeps existing clan_role or resets if necessary
4. Updates Discord roles if configured

**Use Cases**:
- CWL rotations
- Permanent clan transfers
- Promotion to main clan from feeder

**Notes**:
- Preserves kickpoints (they remain tied to player)
- May need to manually set new role with `/editmember`
- Does NOT affect in-game clan

---

### editmember

**Purpose**: Change a player's clan role in the database.

**Permissions**: Co-Leader or higher

**Parameters**:
- `player` (string with autocomplete, required): Player to edit
- `role` (string with autocomplete, required): New clan role

**Implementation**: `commands/coc/memberlist/editmember.java`

**Usage**:
```
/editmember player:#ABC123 role:Co-Leader
```

**Available Roles**:
- Leader
- Co-Leader
- Elder
- Member

**Process**:
1. Validates player is in a clan
2. Updates clan_role in database
3. Updates Discord role if configured

**Use Cases**:
- After in-game promotion/demotion
- Syncing database with in-game roles
- Role corrections

**Notes**:
- Use `/memberstatus` to find role mismatches
- Does NOT change in-game role
- Elder role is stored as "admin" in database

---

### listmembers

**Purpose**: Display all members of a clan organized by role.

**Permissions**: Anyone can use

**Parameters**:
- `clan` (string with autocomplete, required): Clan to list

**Implementation**: `commands/coc/memberlist/listmembers.java`

**Usage**:
```
/listmembers clan:LOST_F2P
```

**Output Format**:
```
Clan: ClanName (#TAG)

🔰 Leader:
  • Player1 (#TAG1)

⚔️ Co-Leaders:
  • Player2 (#TAG2)
  • Player3 (#TAG3)

🛡️ Elders:
  • Player4 (#TAG4)
  • Player5 (#TAG5)

⚪ Members:
  • Player6 (#TAG6)
  • Player7 (#TAG7)

Total: 7 members
```

**Features**:
- Grouped by role with emoji indicators
- Shows player names and tags
- Counts per role
- Total member count

**Use Cases**:
- Quick clan roster view
- Checking current membership
- Preparing for role updates

---

### memberstatus

**Purpose**: Compare database clan members with in-game clan members. Identifies synchronization issues.

**Permissions**: Anyone can use

**Parameters**:
- `clan` (string with autocomplete, required): Clan to check
- `disable_rolecheck` (string, optional): Set to "true" to skip role verification

**Implementation**: `commands/coc/memberlist/memberstatus.java`

**Usage**:
```
/memberstatus clan:LOST_F2P
/memberstatus clan:LOST_F2P disable_rolecheck:true
```

**Output Sections**:

1. **Member in DB, not in clan (in-game)**:
   - Players in database but not in clan API
   - Possible causes: Left clan, kicked, database outdated

2. **Not member (in DB), but in clan (in-game)**:
   - Players in clan API but not in database
   - Need to run `/addmember` for these

3. **In clan, wrong role** (if rolecheck enabled):
   - Players where database role doesn't match API role
   - Shows both roles for comparison
   - Need to run `/editmember` to fix

**Example Output**:
```
Clan: LOST F2P (#ABC123)

Member, ingame nicht im Clan:
• PlayerA (#TAG1)
• PlayerB (#TAG2)

Kein Mitglied, ingame im Clan:
• PlayerC (#TAG3)

Im Clan, falsche Rolle:
• PlayerD (#TAG4):
  - Ingame: Co-Leader
  - Datenbank: Elder
```

**Features**:
- 🔁 Refresh button to recheck
- Timestamp of last check
- Hidden co-leaders automatically excluded

**Use Cases**:
- Daily/weekly membership audits
- After clan cleanup
- Before wars/CWL
- Identifying who needs adding/removing

**Notes**:
- API role is authoritative for in-game status
- Database is authoritative for bot features
- Hidden co-leaders don't appear as "missing"

**See Also**: `CHECKROLES_COMMAND.md` (similar but for Discord roles)

---

### cwlmemberstatus

**Purpose**: Check which members of a Discord role are in a CWL roster.

**Permissions**: Anyone can use

**Parameters**:
- `team_role` (role, required): Discord role to check
- `cwl_clan_tag` (string with autocomplete, required): CWL clan tag (can be main clan or side clan)

**Implementation**: `commands/coc/memberlist/cwlmemberstatus.java`

**Usage**:
```
/cwlmemberstatus team_role:@Red_Team cwl_clan_tag:#SIDECLAN1
```

**Process**:
1. Gets all members with the specified Discord role
2. Gets their linked CoC accounts
3. Queries CWL roster from CoC API
4. Compares and reports

**Output**:
```
Team Role: @Red_Team
CWL Clan: SideClan (#TAG)

Members IN CWL Roster:
✅ Player1 (#TAG1)
✅ Player2 (#TAG2)

Members NOT IN CWL Roster:
❌ Player3 (#TAG3)
❌ Player4 (#TAG4)

Statistics:
Total team members: 4
In roster: 2
Not in roster: 2
```

**Use Cases**:
- Verifying CWL team assignments
- Finding who needs to be rostered
- Checking side clan rosters
- CWL preparation

**Side Clans**:
- Side clans stored in `sideclans` table
- Manually added to database
- Used when main clan doesn't have enough spots

---

## Kickpoints System Commands

The kickpoints system tracks rule violations and penalties for clan members.

### kpaddreason

**Purpose**: Create a kickpoint reason template with a default point value for a clan.

**Permissions**: Co-Leader or higher

**Parameters**:
- `clan` (string with autocomplete, required): Clan for this reason
- `reason` (string, required): Reason name/description
- `amount` (integer, required): Default kickpoint value

**Implementation**: `commands/coc/kickpoints/kpaddreason.java`

**Usage**:
```
/kpaddreason clan:LOST_F2P reason:CW_Attack_vergessen amount:2
/kpaddreason clan:LOST_F2P reason:Raid_nicht_gemacht amount:1
```

**Naming Conventions** (suggested):
- Use underscores: `CW_Attack_vergessen`
- Be specific: `Raid_unter_3_Angriffe` vs `Raid_fail`
- Include context: `CWL_Day1_missed`

**Process**:
1. Validates clan exists
2. Checks if reason name already exists for clan
3. Creates reason template in database
4. Makes available for autocomplete in `/kpadd`

**Use Cases**:
- Initial clan setup
- Adding new rule categories
- Standardizing kickpoint amounts

**Notes**:
- Reason names are per-clan (same name can exist in multiple clans)
- Templates are suggestions - actual amount can be overridden
- Use descriptive names for easier autocomplete

---

### kpremovereason

**Purpose**: Delete a kickpoint reason template.

**Permissions**: Co-Leader or higher

**Parameters**:
- `clan` (string with autocomplete, required): Clan
- `reason` (string with autocomplete, required): Reason to remove

**Implementation**: `commands/coc/kickpoints/kpremovereason.java`

**Usage**:
```
/kpremovereason clan:LOST_F2P reason:CW_Attack_vergessen
```

**Process**:
1. Validates reason exists for clan
2. Deletes from kickpoint_reasons table
3. Does NOT delete existing kickpoints using this reason

**Warning**: Existing kickpoints with this reason remain in database - they just won't show the template name.

---

### kpeditreason

**Purpose**: Change the default kickpoint amount for a reason template.

**Permissions**: Co-Leader or higher

**Parameters**:
- `clan` (string with autocomplete, required): Clan
- `reason` (string with autocomplete, required): Reason to edit
- `amount` (integer, required): New default amount

**Implementation**: `commands/coc/kickpoints/kpeditreason.java`

**Usage**:
```
/kpeditreason clan:LOST_F2P reason:CW_Attack_vergessen amount:3
```

**Process**:
1. Validates reason exists
2. Updates amount in database
3. Does NOT affect existing kickpoints

**Notes**:
- Only affects future kickpoint additions
- Existing kickpoints keep their original amount
- Cannot rename reason (must delete and recreate)

---

### kpadd

**Purpose**: Add kickpoints to a player for a rule violation.

**Permissions**: Co-Leader or higher in player's clan

**Parameters**:
- `player` (string with autocomplete, required): Player to penalize
- `reason` (string with autocomplete, optional): Reason template to use

**Implementation**: `commands/coc/kickpoints/kpadd.java`

**Usage**:
```
/kpadd player:#ABC123 reason:CW_Attack_vergessen
/kpadd player:#ABC123
```

**Process**:
1. Validates player is in a clan
2. Checks user has Co-Leader+ in that clan
3. Opens modal with fields:
   - Reason (pre-filled if template selected)
   - Amount (pre-filled from template or empty)
   - Date (default: today)
   - Custom notes (optional)
4. Validates kickpoint won't exceed clan limit
5. Calculates expiration date (date + expire_days)
6. Creates kickpoint record

**Modal Fields**:
```
Reason: [CW Attack Missed]
Amount: [2]
Date: [2024-01-15] (format: yyyy-MM-dd)
Notes: [Optional additional details]
```

**Validation**:
- Amount must be positive integer
- Date must be valid format (yyyy-MM-dd)
- Total kickpoints must not exceed clan max
- Clan must have kickpoints configured

**Expiration Calculation**:
```
expires = date + clan.kickpoints_expire_after_days
```

**Example**:
```
Given:
- Date: 2024-01-15
- Clan expire setting: 30 days

Result:
- Expires: 2024-02-14
```

**Use Cases**:
- War attack missed
- Raid incomplete
- Clan Games below minimum
- Donation requirements
- Activity requirements

**Notes**:
- Custom reason text can be entered even if template selected
- Date can be backdated for past violations
- Use `/kpinfo` to see available reasons for clan

---

### kpremove

**Purpose**: Delete a kickpoint record completely.

**Permissions**: Co-Leader or higher in player's clan

**Parameters**:
- `id` (integer, required): Kickpoint ID (from `/kpmember`)

**Implementation**: `commands/coc/kickpoints/kpremove.java`

**Usage**:
```
/kpremove id:123
```

**Process**:
1. Gets kickpoint record
2. Gets player's clan
3. Validates user has Co-Leader+ in that clan
4. Deletes kickpoint from database
5. Confirms deletion

**Use Cases**:
- Kickpoint added by mistake
- Penalty pardoned
- Incorrect violation
- Appeal accepted

**Notes**:
- Cannot be undone
- Use `/kpedit` to modify instead of remove/re-add
- Kickpoint ID visible in `/kpmember` output

---

### kpedit

**Purpose**: Edit an existing kickpoint record.

**Permissions**: Co-Leader or higher in player's clan

**Parameters**:
- `id` (integer, required): Kickpoint ID to edit

**Implementation**: `commands/coc/kickpoints/kpedit.java`

**Usage**:
```
/kpedit id:123
```

**Process**:
1. Fetches existing kickpoint
2. Opens modal pre-filled with current values
3. User modifies as needed
4. Validates changes
5. Updates record

**Editable Fields**:
- Reason text
- Amount
- Date given
- Custom notes (if field exists)

**Recalculation**: Expiration date automatically recalculated if date is changed.

**Use Cases**:
- Correcting amount
- Updating reason text
- Fixing date mistakes
- Adding clarifications

---

### kpmember

**Purpose**: View all kickpoints for a specific player.

**Permissions**: Anyone can use

**Parameters**:
- `player` (string with autocomplete, required): Player to view

**Implementation**: `commands/coc/kickpoints/kpmember.java`

**Usage**:
```
/kpmember player:#ABC123
```

**Output Format**:
```
Kickpoints: Player Name (#TAG)
Clan: ClanName (#CLANTAG)

Total Active Kickpoints: 5 KP

Active Kickpoints:
─────────────────
ID: 123
Reason: CW Attack Missed
Amount: 2 KP
Given: 15.01.2024
Expires: 14.02.2024 (29 days left)
─────────────────
ID: 124
Reason: Raid Incomplete
Amount: 3 KP
Given: 20.01.2024
Expires: 19.02.2024 (34 days left)
─────────────────

⚠️ Warning: 5 / 10 KP (50% of limit)
```

**Includes**:
- Total active kickpoints
- Each kickpoint with full details
- Days until expiration
- Warning if approaching clan limit

**Warning Levels**:
- Below 50%: Green/Info
- 50-80%: Yellow/Warning
- Above 80%: Red/Danger

**Notes**:
- Only shows non-expired kickpoints
- Expired kickpoints not displayed but remain in database
- ID numbers used for `/kpremove` and `/kpedit`

---

### kpclan

**Purpose**: View kickpoint summary for all members of a clan.

**Permissions**: Anyone can use

**Parameters**:
- `clan` (string with autocomplete, required): Clan to view

**Implementation**: `commands/coc/kickpoints/kpclan.java`

**Usage**:
```
/kpclan clan:LOST_F2P
```

**Output Format**:
```
Kickpoints: LOST F2P (#CLANTAG)
Max Kickpoints: 10 KP
Expire After: 30 days

Members with Kickpoints:
────────────────────────
1. Player1 (#TAG1): 8 KP ⚠️
2. Player2 (#TAG2): 5 KP
3. Player3 (#TAG3): 3 KP
4. Player4 (#TAG4): 1 KP

Total: 4 members with kickpoints
Average: 4.25 KP per violator
```

**Sorting**: By kickpoint total (highest to lowest)

**Indicators**:
- ⚠️ : Approaching limit (>80%)
- 🔴 : Over limit (should be kicked)

**Use Cases**:
- Weekly kickpoint review
- Identifying problem members
- Preparing for cleanup
- Clan management meetings

---

### kpinfo

**Purpose**: List all kickpoint reason templates configured for a clan.

**Permissions**: Anyone can use

**Parameters**:
- `clan` (string with autocomplete, required): Clan to view

**Implementation**: `commands/coc/kickpoints/kpinfo.java`

**Usage**:
```
/kpinfo clan:LOST_F2P
```

**Output Format**:
```
Kickpoint Reasons: LOST F2P

Available Reasons:
──────────────────
• CW_Attack_vergessen: 2 KP
• Raid_nicht_gemacht: 1 KP
• Clangames_unter_4000: 3 KP
• CWL_Angriff_verpasst: 2 KP
• Inaktiv_3_Tage: 1 KP

Total: 5 reason templates
```

**Use Cases**:
- Checking available reasons before adding kickpoint
- Documenting clan rules
- New co-leader training

**Notes**:
- Reasons are templates only
- Actual amount can be different when applied
- Use `/kpaddreason` to add new reasons

---

### clanconfig

**Purpose**: Configure clan settings including kickpoints, roles, and requirements.

**Permissions**: Leader only

**Parameters**:
- `clan` (string with autocomplete, required): Clan to configure

**Implementation**: `commands/coc/kickpoints/clanconfig.java`

**Usage**:
```
/clanconfig clan:LOST_F2P
```

**Modal Configuration Fields**:

1. **Max Kickpoints** (integer)
   - Maximum kickpoints before kick/demotion
   - Example: 10

2. **Minimum Season Wins** (integer)
   - Required wins per season
   - Example: 50

3. **Kickpoints Expire After Days** (integer)
   - Days until kickpoints expire
   - Example: 30

4. **Leader Role ID** (Discord role ID)
   - Discord role for clan leaders
   - Example: 123456789012345678

5. **Co-Leader Role ID**
   - Discord role for co-leaders

6. **Elder Role ID**
   - Discord role for elders

7. **Member Role ID**
   - Discord role for regular members

**Example Values**:
```
Max Kickpoints: 10
Min Season Wins: 50
Expire After: 30
Leader Role: 123456789012345678
Co-Leader Role: 234567890123456789
Elder Role: 345678901234567890
Member Role: 456789012345678901
```

**Getting Role IDs**:
1. Enable Developer Mode in Discord settings
2. Right-click role → Copy ID
3. Paste into modal

**Use Cases**:
- Initial clan setup
- Changing kickpoint rules
- Updating Discord role assignments
- Adjusting season requirements

**Notes**:
- All fields optional - can configure partially
- Settings apply immediately
- Role IDs enable automatic role synchronization
- Use `/checkroles` after configuring roles

---

## Utility Commands

### cwdonator

**Purpose**: Randomly select war donors for a clan war. Can use list-based rotation for fairness.

**Permissions**: Co-Leader or higher

**Parameters**:
- `clan` (string with autocomplete, required): Clan in war
- `exclude_leaders` (string, optional): "true" to exclude leaders/co-leaders
- `use_lists` (string, optional): "true" for list-based fair rotation

**Implementation**: `commands/coc/util/cwdonator.java`

**Usage**:
```
/cwdonator clan:LOST_F2P
/cwdonator clan:LOST_F2P exclude_leaders:true
/cwdonator clan:LOST_F2P use_lists:true exclude_leaders:true
```

**Donor Count by War Size**:
```
 5v5 → 1 donor
10v10 → 2 donors
15v15 → 3 donors
20v20 → 4 donors
25v25 → 5 donors
30v30 → 6 donors
40v40 → 8 donors
50v50 → 10 donors
```

**Selection Methods**:

1. **Random Mode** (default):
   - Purely random selection from war roster
   - No tracking of previous selections

2. **List Mode** (`use_lists:true`):
   - Tracks previous selections in database
   - Ensures fair rotation
   - Won't select same person repeatedly
   - Stored in `cwdonator_lists` table by war

**Exclude Leaders**:
- When enabled, excludes Leader and Co-Leader ranks
- Useful for distributing responsibility to lower ranks

**Output**:
```
CW-Spender
Clan: LOST F2P (#TAG)
War Size: 30v30

Selected Donors (6):
🎯 Player1 (#TAG1)
🎯 Player2 (#TAG2)
🎯 Player3 (#TAG3)
🎯 Player4 (#TAG4)
🎯 Player5 (#TAG5)
🎯 Player6 (#TAG6)
```

**Use Cases**:
- Regular war donation assignment
- CWL preparation
- Fair work distribution

**Notes**:
- Must be run during preparation or war phase
- Works for both regular wars and CWL
- List mode only available for registered clans

---

### raidping

**Purpose**: Ping Discord users who haven't completed their raid attacks.

**Permissions**: Co-Leader or higher

**Parameters**:
- `clan` (string with autocomplete, required): Clan to check

**Implementation**: `commands/coc/util/raidping.java`

**Usage**:
```
/raidping clan:LOST_F2P
```

**Process**:
1. Checks if raid is active
2. Gets raid participants from API
3. Gets clan members from database
4. Identifies incomplete attacks
5. Pings linked Discord users

**Output Format**:
```
Raid-Ping
Executed by: @Admin

⚠️ No ping, raid not currently active.

Fehlende Raid Angriffe:

❌ Player1 (#TAG1) - @DiscordUser1
   Attacks: 4/6
   
❌ Player2 (#TAG2) - @DiscordUser2
   Attacks: 0/6 (Not participated)
   
❌ Player3 (#TAG3) - Not linked
   Attacks: 3/6
```

**Ping Behavior**:
- Only pings if raid is active
- Only pings linked Discord users
- Shows attack count for all

**Categories**:
- Incomplete: Started but didn't finish
- Not participated: 0 attacks
- Not linked: Can't ping (no Discord account)

**Use Cases**:
- Weekend reminders
- Last-day push
- Ensuring full participation

**Notes**:
- Raid must be active to ping
- Bonus attacks counted in limit
- Players with perfect attacks not listed

---

### checkroles

**Purpose**: Verify that clan members have correct Discord roles based on their in-game clan rank.

**Permissions**: Co-Leader or higher

**Parameters**:
- `clan` (string with autocomplete, required): Clan to check

**Implementation**: `commands/coc/util/checkroles.java`

**Full Documentation**: See `CHECKROLES_COMMAND.md`

**Usage**:
```
/checkroles clan:LOST_F2P
```

**Output Sections**:

1. **Statistics**:
   - Total members
   - Linked/unlinked count
   - Correct/incorrect roles

2. **Issues**:
   - Members not on server
   - Missing roles
   - Unconfigured roles

**Example Output**:
```
Role Check: LOST F2P (#TAG)

Statistics:
──────────
Total Members: 30
Linked: 25
Unlinked: 5
With Correct Roles: 22
With Incorrect Roles: 3

Issues Found:
─────────────
Player1 (#TAG1) @User1
Expected: Ältester
Issue: fehlt @Elder

Player2 (#TAG2) @User2
Expected: Mitglied
Issue: nicht auf dem Server

Player3 (#TAG3) @User3
Expected: Vize-Anführer
Issue: Rolle nicht konfiguriert

✅ Alle anderen haben die korrekte Rolle!

Last updated: 15.01.2024 um 14:30 Uhr
[🔁 Refresh]
```

**Role Mapping**:
```
Leader → Anführer
Co-Leader → Vize-Anführer
Elder → Ältester
Member → Mitglied
```

**Features**:
- �� Refresh button to re-check
- Timestamp
- Detailed issue descriptions

**Use Cases**:
- After promotions/demotions
- Regular maintenance (weekly)
- Before important events
- New member verification

**Notes**:
- Requires clan roles configured in `/clanconfig`
- Hidden co-leaders treated as co-leaders
- Unlinked members skipped

---

### setnick

**Purpose**: Set your Discord nickname to your in-game name with optional custom alias.

**Permissions**: Anyone can use (for own nickname)

**Parameters**:
- `my_player` (string with autocomplete, required): Your player tag
- `alias` (string, optional): Custom suffix/alias

**Implementation**: `commands/coc/util/setnick.java`

**Usage**:
```
/setnick my_player:#ABC123
/setnick my_player:#ABC123 alias:Red
```

**Nickname Format**:
```
Without alias: PlayerName
With alias: PlayerName | Red
```

**Examples**:
```
Player: Pixel
Alias: Main
Result: Pixel | Main

Player: Johannes
Alias: CWL
Result: Johannes | CWL
```

**Process**:
1. Validates player is linked to your Discord
2. Gets player name from API
3. Sets Discord nickname to: `Name | Alias` or just `Name`

**Use Cases**:
- Setting initial nickname
- Updating after name change
- Adding team indicator (Red, Blue, etc.)
- Adding role indicator (CWL, Farm, Main)

**Notes**:
- Can only set your own nickname
- Bot needs "Manage Nicknames" permission
- Bot cannot change server owner's nickname
- Nickname length limit: 32 characters

---

### wins

**Purpose**: Show wins statistics for a player or entire clan for a specific season.

**Permissions**: Anyone can use

**Parameters**:
- `season` (string with autocomplete, required): Season (format: yyyy-MM)
- `player` (string with autocomplete, optional): Specific player
- `clan` (string with autocomplete, optional): Entire clan

**Implementation**: `commands/coc/util/wins.java`

**Usage**:
```
/wins season:2024-01 player:#ABC123
/wins season:2024-01 clan:LOST_F2P
```

**Season Format**: `yyyy-MM` (e.g., 2024-01 for January 2024)

**Output (Player Mode)**:
```
Wins: Player Name (#TAG)
Season: Januar 2024

Wins at Season Start: 1000
Wins at Season End: 1150
Wins Gained: 150

✅ Above minimum (50)
```

**Output (Clan Mode)**:
```
Wins: LOST F2P (#TAG)
Season: Januar 2024

Ranking by Wins:
────────────────
1. Player1 (#TAG1): 200 Wins
2. Player2 (#TAG2): 175 Wins
3. Player3 (#TAG3): 150 Wins
4. Player4 (#TAG4): 140 Wins
...
```

**Data Source**:
- `achievement_data` table
- Snapshots taken at season start/end
- Calculated difference

**Autocomplete**:
- Shows recent seasons (last 6 months)
- Format: "Januar 2024", "Dezember 2023"

**Use Cases**:
- Checking player activity
- Clan performance tracking
- Identifying inactive members
- Season awards/recognition

**Notes**:
- Requires data snapshots (bot must have been running)
- Only shows linked/verified players
- Clan mode sorted by wins descending

---

### listeningevent

**Purpose**: Manage automated event monitoring for clan events (wars, raids, clan games).

**Permissions**: Co-Leader or higher

**Subcommands**:
- `add`: Create new listening event
- `list`: View all events (optionally filtered by clan)
- `remove`: Delete an event by ID

**Implementation**: `commands/coc/util/listeningevent.java`

**Full Documentation**: See `LISTENING_EVENTS.md` and `LISTENING_EVENTS_COMPLETE_GUIDE.md`

#### Subcommand: add

**Usage**:
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:0 actiontype:Kickpoint channel:#war-logs kickpoint_reason:CW_Attack_vergessen
```

**Parameters**:
- `clan` (autocomplete, required): Clan to monitor
- `type` (choice, required): Event type
  - Clan_Games
  - Clan_War
  - CWL_Tag (CWL day)
  - Raid
- `duration` (autocomplete, required): Time before end to trigger (milliseconds)
  - "start" / -1 = Fire at event start
  - 0 = Fire at event end
  - 3600000 = 1 hour before end
  - Custom milliseconds
- `actiontype` (choice, required, filtered by type): Action to take
  - Info-Nachricht: Send info message only
  - Kickpoint: Add kickpoints to violators
  - CW Donator (Filler): War donor selection
  - Filler: Check war fillers
  - Raidfails: Raid district analysis
- `channel` (channel picker, required): Discord channel for messages
- `kickpoint_reason` (autocomplete, optional): Kickpoint reason template
  - Required for actiontype=Kickpoint
  - Optional for actiontype=Raidfails

**Event Types**:

1. **Clan Games**:
   - Timing: 22nd-28th monthly (hardcoded)
   - Checks: Points gained < threshold
   - Actions: Info, Kickpoint

2. **Clan War**:
   - Timing: From API war end time
   - Checks: Missed attacks, fillers
   - Actions: Info, Kickpoint, Filler, CW Donator
   - Special: duration=-1 triggers on war START

3. **CWL Day**:
   - Timing: Each CWL war end
   - Checks: Didn't attack
   - Actions: Info, Kickpoint

4. **Raid**:
   - Timing: From API raid end time
   - Checks: Incomplete attacks, district fails
   - Actions: Info, Kickpoint, Raidfails

**Action Types**:

- **Info-Nachricht**: Lists violations, no automation
- **Kickpoint**: Adds kickpoints automatically using configured reason
- **CW Donator (Filler)**: Randomly selects war donors
- **Filler**: Lists players opted out of war
- **Raidfails**: Analyzes district over-attacks

**Example Setups**:

War End (Missed Attacks):
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:0 actiontype:Kickpoint channel:#war-logs kickpoint_reason:CW_Attack_vergessen
```

War Start (Filler Check):
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:start actiontype:Filler channel:#war-prep
```

Clan Games:
```
/listeningevent add clan:LOST_F2P type:Clan_Games duration:0 actiontype:Kickpoint channel:#clan-logs kickpoint_reason:Clangames_unter_4000
```

#### Subcommand: list

**Usage**:
```
/listeningevent list
/listeningevent list clan:LOST_F2P
```

**Output**:
```
Listening Events

Event ID: 123
Clan: LOST F2P (#TAG)
Type: Clan War
Duration: 0ms (at end)
Action: Kickpoint
Channel: #war-logs
Fire Time: 2024-01-20 14:30
─────────────────────

Event ID: 124
Clan: LOST F2P (#TAG)
Type: Raid Weekend
Duration: 0ms (at end)
Action: Info-Nachricht
Channel: #raid-logs
Fire Time: 2024-01-22 07:00
─────────────────────

Total: 2 events
```

#### Subcommand: remove

**Usage**:
```
/listeningevent remove id:123
```

**Process**:
1. Validates event exists
2. Deletes from database
3. Confirms deletion

**Notes**:
- Events are polled every 2 minutes
- Scheduled within 5 minutes of fire time
- Retry logic (3 attempts) on failure
- Validates game state before firing

---

**End of Commands Reference**

For event system details, see `LISTENING_EVENTS.md` and `IMPLEMENTATION_SUMMARY.md`.

For data structures, see `01_DATA_STRUCTURES.md`.

For architecture overview, see `00_MASTER_OVERVIEW.md`.

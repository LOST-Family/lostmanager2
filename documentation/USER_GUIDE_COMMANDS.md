# Command Guide - How to Use Each Command

This guide explains every bot command from a user's perspective. Each section shows you exactly what to type and what happens.

---

## Table of Contents

### Account Management
- [verify](#verify) - Link your Clash account to Discord
- [link](#link) - Admin: Link someone's account
- [relink](#relink) - Admin: Change who's linked to an account
- [unlink](#unlink) - Admin: Remove account link
- [playerinfo](#playerinfo) - Check player information
- [setnick](#setnick) - Set your Discord nickname

### Kickpoints
- [kpmember](#kpmember) - View someone's kickpoints
- [kpclan](#kpclan) - View all clan kickpoints
- [kpadd](#kpadd) - Add kickpoints (Co-Leader+)
- [kpremove](#kpremove) - Remove kickpoints (Co-Leader+)
- [kpedit](#kpedit) - Edit kickpoints (Co-Leader+)
- [kpinfo](#kpinfo) - List kickpoint reasons
- [kpaddreason](#kpaddreason) - Create kickpoint reason (Co-Leader+)
- [kpremovereason](#kpremovereason) - Delete kickpoint reason (Co-Leader+)
- [kpeditreason](#kpeditreason) - Edit kickpoint reason (Co-Leader+)

### Clan Management
- [addmember](#addmember) - Add player to clan (Co-Leader+)
- [removemember](#removemember) - Remove player from clan (Co-Leader+)
- [transfermember](#transfermember) - Move player between clans (Co-Leader+)
- [editmember](#editmember) - Change player's role (Co-Leader+)
- [listmembers](#listmembers) - List all clan members
- [memberstatus](#memberstatus) - Check sync between Discord and game (Co-Leader+)
- [cwlmemberstatus](#cwlmemberstatus) - Check CWL roster (Co-Leader+)
- [clanconfig](#clanconfig) - Configure clan settings (Leader only)

### Clan Events
- [raidping](#raidping) - Ping members with incomplete raids (Co-Leader+)
- [checkroles](#checkroles) - Check Discord role accuracy (Co-Leader+)
- [wins](#wins) - View season wins statistics
- [cwdonator](#cwdonator) - Select random war donors (Co-Leader+)
- [listeningevent](#listeningevent) - Manage automated events (Co-Leader+)

### Discord Admin
- [checkreacts](#checkreacts) - Check who reacted to a message
- [teamcheck](#teamcheck) - Check team distribution
- [deletemessages](#deletemessages) - Delete multiple messages
- [reactionsrole](#reactionsrole) - Give roles based on reactions
- [restart](#restart) - Restart the bot (Admin only)

---

## Account Management Commands

### verify

Link your Clash of Clans account to your Discord account.

**How to use:**

`/verify tag:YOUR_TAG apitoken:YOUR_TOKEN`

**Parameters:**
- `tag` - Your player tag (like `#YR8UVQQ8Q`)
- `apitoken` - API token from Clash of Clans settings

**What happens:**
1. Bot checks if your token is valid with Clash of Clans
2. Your account gets linked to your player tag
3. You get the "Verified" role
4. Your nickname changes to your in-game name
5. Bot saves your current wins for season tracking

**Example:**
```
/verify tag:#YR8UVQQ8Q apitoken:abcd123xyz
```

**You'll see:**
```
✅ Erfolgreich! Du bist jetzt als MaxPower verifiziert!
```

**How to get your API token:**
1. Open Clash of Clans
2. Tap Settings ⚙️
3. Scroll down to "Show my API token"
4. Copy the token

⚠️ **Important:** The token changes every time you view it, so get a fresh one when you use this command.

💡 **Tip:** You can only link one account yourself. If you have multiple accounts, ask a co-leader to use `/link` for the others.

---

### link

Admin command to link someone's Discord account to a Clash account.

**Permission needed:** Co-Leader or higher

**How to use:**

`/link tag:PLAYER_TAG user:@DiscordUser`

**Parameters:**
- `tag` - Player tag to link
- `user` - Discord user to link (mention them or type name)

**Alternative:**

`/link tag:PLAYER_TAG userid:123456789`

**What happens:**
1. Bot links the Discord user to the player tag
2. User gets "Verified" role (if first account)
3. User's nickname changes to in-game name

**Example:**
```
/link tag:#ABC123 user:@NewMember
```

**You'll see:**
```
✅ Player #ABC123 successfully linked to @NewMember
```

💡 **Use this when:** Someone can't use `/verify` themselves (like if they play on an emulator).

---

### relink

Change which Discord account is linked to a player tag.

**Permission needed:** Co-Leader or higher

**How to use:**

`/relink tag:PLAYER_TAG user:@NewDiscordUser`

**Parameters:**
- `tag` - Player tag (autocomplete shows existing players)
- `user` - New Discord user to link

**What happens:**
1. Old Discord link is removed
2. New Discord link is created
3. New user gets roles and nickname updated

**Example:**
```
/relink tag:#YR8UVQQ8Q user:@NewAccount
```

**You'll see:**
```
✅ Player #YR8UVQQ8Q relinked to @NewAccount
```

💡 **Use this when:** Someone switches Discord accounts or gets a new Discord account.

---

### unlink

Remove the link between a Discord account and a player tag.

**Permission needed:** Co-Leader or higher

**How to use:**

`/unlink tag:PLAYER_TAG`

**Parameters:**
- `tag` - Player tag to unlink (autocomplete available)

**What happens:**
1. Discord link is removed from the player
2. Player stays in the database but isn't linked anymore

**Example:**
```
/unlink tag:#YR8UVQQ8Q
```

**You'll see:**
```
✅ Player #YR8UVQQ8Q has been unlinked
```

⚠️ **Note:** This doesn't remove the player from the clan database, just removes the Discord connection.

---

### playerinfo

Check information about a player or all accounts linked to a Discord user.

**How to use:**

**Check your own info:**
```
/playerinfo
```

**Check someone else:**
```
/playerinfo user:@PlayerName
```

**Check a specific player tag:**
```
/playerinfo player:#YR8UVQQ8Q
```

**What you'll see:**
- Player name and tag
- Linked Discord user
- Current clan and role
- Total kickpoints
- Season wins statistics

**Example output:**
```
🎮 Player Information

MaxPower (#YR8UVQQ8Q)
Discord: @MaxPower#1234
Clan: LOST F2P
Role: Leader
Kickpoints: 3/10

📊 Season Wins (2024-12):
Start: 125 wins
Current: 156 wins
Gained: +31 wins this season
```

💡 **Tip:** If you check a Discord user with multiple accounts, you'll see all their linked players.

---

### setnick

Set your Discord nickname to match your in-game name.

**How to use:**

**Simple version:**
```
/setnick my_player:#YR8UVQQ8Q
```

**With alias:**
```
/setnick my_player:#YR8UVQQ8Q alias:Red
```

**Parameters:**
- `my_player` - Your player tag (autocomplete available)
- `alias` - (Optional) Custom suffix to add

**What happens:**
1. Bot gets your in-game name
2. Bot changes your Discord nickname
3. If you added an alias, format is: "PlayerName | Alias"

**Examples:**

Without alias:
```
/setnick my_player:#YR8UVQQ8Q
```
**Result:** Nickname = "MaxPower"

With alias:
```
/setnick my_player:#YR8UVQQ8Q alias:Red Team
```
**Result:** Nickname = "MaxPower | Red Team"

💡 **Use this when:** You change your in-game name or want to add a team tag.

---

## Kickpoints Commands

### kpmember

View all kickpoints for a player.

**How to use:**

`/kpmember player:PLAYER_NAME`

**Parameters:**
- `player` - Player name or tag (autocomplete available)

**What you'll see:**
- Total active kickpoints
- List of each kickpoint with:
  - ID number
  - Reason
  - Amount
  - Date given
  - Expiration date
- Warning if over the limit

**Example:**
```
/kpmember player:MaxPower
```

**Output:**
```
⚠️ Kickpoints for MaxPower (#YR8UVQQ8Q)
Total: 7/10 kickpoints

#1001 - CW Attack vergessen - 3 points
   Given: 2024-12-01
   Expires: 2024-12-31

#1015 - Raid unter 5 Angriffe - 2 points
   Given: 2024-12-10
   Expires: 2025-01-09

#1023 - Clangames unter 4000 - 2 points
   Given: 2024-12-14
   Expires: 2025-01-13

⚠️ Warning: Close to kickpoint limit!
```

💡 **Tip:** Kickpoints automatically expire after 30 days (or whatever your clan configured).

---

### kpclan

View kickpoint summary for the entire clan.

**How to use:**

`/kpclan clan:CLAN_NAME`

**Parameters:**
- `clan` - Clan name or tag (autocomplete available)

**What you'll see:**
- List of all members who have kickpoints
- Sorted from highest to lowest
- Each member shows total kickpoints

**Example:**
```
/kpclan clan:LOST_F2P
```

**Output:**
```
📊 Kickpoints Summary - LOST F2P

PlayerOne: 8/10 ⚠️
PlayerTwo: 6/10
PlayerThree: 3/10
PlayerFour: 2/10
PlayerFive: 1/10

Total: 5 members with kickpoints
```

💡 **Use this to:** See who's close to being kicked for too many penalties.

---

### kpadd

Add kickpoints to a player.

**Permission needed:** Co-Leader or higher (of the player's clan)

**How to use:**

**With a preset reason:**
```
/kpadd player:PLAYER_NAME reason:CW_Attack_vergessen
```

**Without preset reason:**
```
/kpadd player:PLAYER_NAME
```

**Parameters:**
- `player` - Player name or tag (autocomplete available)
- `reason` - (Optional) Preset reason template (autocomplete shows your clan's reasons)

**What happens:**
1. A popup form appears
2. If you picked a preset reason, amount is pre-filled
3. Fill in: reason text, amount, date, optional notes
4. Bot adds the kickpoint
5. Kickpoint expires automatically after your clan's expiration time (default 30 days)

**Example:**

Step 1:
```
/kpadd player:MaxPower reason:CW_Attack_vergessen
```

Step 2 (popup appears):
```
Reason: CW Attack vergessen (pre-filled)
Amount: 3 (pre-filled)
Date: 2024-12-14 (today's date)
Notes: [Optional - leave blank or add details]
```

Step 3 (you'll see):
```
✅ Kickpoint added to MaxPower
   Reason: CW Attack vergessen
   Amount: 3 points
   Expires: 2025-01-13
```

💡 **Tip:** Use preset reasons (created with `/kpaddreason`) to keep penalties consistent.

---

### kpremove

Remove a kickpoint record.

**Permission needed:** Co-Leader or higher (of the player's clan)

**How to use:**

`/kpremove id:KICKPOINT_ID`

**Parameters:**
- `id` - The kickpoint ID number (from `/kpmember`)

**What happens:**
1. Bot deletes the kickpoint from the database
2. Player's total kickpoints decrease

**Example:**

Check kickpoints first:
```
/kpmember player:MaxPower
```

Output shows:
```
#1001 - CW Attack vergessen - 3 points
```

Remove it:
```
/kpremove id:1001
```

You'll see:
```
✅ Kickpoint #1001 removed
```

⚠️ **Use carefully:** This permanently deletes the record. Consider if it should just be allowed to expire naturally.

---

### kpedit

Edit an existing kickpoint.

**Permission needed:** Co-Leader or higher (of the player's clan)

**How to use:**

`/kpedit id:KICKPOINT_ID`

**Parameters:**
- `id` - The kickpoint ID number (from `/kpmember`)

**What happens:**
1. A popup form appears with current values
2. You can change: reason, amount, date, notes
3. Bot updates the kickpoint

**Example:**

```
/kpedit id:1001
```

Popup shows:
```
Reason: CW Attack vergessen (current value)
Amount: 3 (current value)
Date: 2024-12-10 (current value)
Notes: [current notes]
```

Change the amount to 2 and update:
```
✅ Kickpoint #1001 updated
   New amount: 2 points
```

💡 **Use this to:** Fix mistakes or adjust penalties.

---

### kpinfo

List all kickpoint reason templates for a clan.

**How to use:**

`/kpinfo clan:CLAN_NAME`

**Parameters:**
- `clan` - Clan name or tag (autocomplete available)

**What you'll see:**
- All preset reasons configured for the clan
- Amount of kickpoints for each reason

**Example:**
```
/kpinfo clan:LOST_F2P
```

**Output:**
```
📋 Kickpoint Reasons - LOST F2P

CW Attack vergessen - 3 points
Raid unter 5 Angriffe - 2 points
Clangames unter 4000 - 2 points
CWL Attack vergessen - 3 points
Unhöfliches Verhalten - 5 points

Total: 5 preset reasons
```

💡 **Use this to:** See what reasons are available when adding kickpoints.

---

### kpaddreason

Create a kickpoint reason template.

**Permission needed:** Co-Leader or higher

**How to use:**

`/kpaddreason clan:CLAN_NAME reason:REASON_NAME amount:POINTS`

**Parameters:**
- `clan` - Clan name or tag (autocomplete available)
- `reason` - Name for this reason (like "CW Attack vergessen")
- `amount` - How many kickpoints for this violation

**What happens:**
1. Bot creates the reason template
2. This reason now appears in autocomplete for `/kpadd`

**Example:**
```
/kpaddreason clan:LOST_F2P reason:CW_Attack_vergessen amount:3
```

**You'll see:**
```
✅ Reason created for LOST F2P
   Name: CW Attack vergessen
   Amount: 3 kickpoints
```

💡 **Use this to:** Standardize penalties across your clan.

---

### kpremovereason

Delete a kickpoint reason template.

**Permission needed:** Co-Leader or higher

**How to use:**

`/kpremovereason clan:CLAN_NAME reason:REASON_NAME`

**Parameters:**
- `clan` - Clan name or tag (autocomplete available)
- `reason` - Reason to delete (autocomplete shows existing reasons)

**What happens:**
1. Bot deletes the reason template
2. Existing kickpoints with this reason are NOT deleted
3. This reason won't appear in autocomplete anymore

**Example:**
```
/kpremovereason clan:LOST_F2P reason:CW_Attack_vergessen
```

**You'll see:**
```
✅ Reason "CW Attack vergessen" removed from LOST F2P
```

⚠️ **Note:** This only removes the template. Existing kickpoints stay in place.

---

### kpeditreason

Change the amount for a kickpoint reason template.

**Permission needed:** Co-Leader or higher

**How to use:**

`/kpeditreason clan:CLAN_NAME reason:REASON_NAME amount:NEW_AMOUNT`

**Parameters:**
- `clan` - Clan name or tag (autocomplete available)
- `reason` - Reason to edit (autocomplete shows existing reasons)
- `amount` - New kickpoint amount

**What happens:**
1. Bot updates the reason template
2. Future kickpoints using this reason will have the new amount
3. Existing kickpoints are NOT changed

**Example:**
```
/kpeditreason clan:LOST_F2P reason:CW_Attack_vergessen amount:5
```

**You'll see:**
```
✅ Reason "CW Attack vergessen" updated
   Old amount: 3 kickpoints
   New amount: 5 kickpoints
```

⚠️ **Note:** This doesn't change existing kickpoints, only future ones.

---

## Clan Management Commands

### addmember

Add a player to a clan in the bot's database.

**Permission needed:** Co-Leader or higher

**How to use:**

`/addmember clan:CLAN_NAME player:PLAYER_TAG role:ROLE`

**Parameters:**
- `clan` - Clan name or tag (autocomplete available)
- `player` - Player tag or name (autocomplete shows unassigned players)
- `role` - Clan role (choices: Leader, Co-Leader, Elder, Member)

**What happens:**
1. Bot adds the player to the clan in the database
2. Player's role is set
3. If player is linked to Discord, their roles update

**Example:**
```
/addmember clan:LOST_F2P player:#ABC123 role:Elder
```

**You'll see:**
```
✅ Player MaxPower (#ABC123) added to LOST F2P as Elder
```

💡 **Use this when:** New member joins your clan and needs to be tracked in the bot.

---

### removemember

Remove a player from their clan.

**Permission needed:** Co-Leader or higher

**How to use:**

`/removemember player:PLAYER_NAME`

**Parameters:**
- `player` - Player name or tag (autocomplete available)

**What happens:**
1. Bot removes the player from their clan in the database
2. If configured, player gets "ex-member" role in Discord
3. Player's clan-specific Discord roles are removed

**Example:**
```
/removemember player:MaxPower
```

**You'll see:**
```
✅ Player MaxPower (#YR8UVQQ8Q) removed from LOST F2P
   Ex-member role assigned
```

💡 **Use this when:** Someone leaves the clan.

⚠️ **Note:** This doesn't delete the player entirely, just removes them from the clan. Their Discord link and kickpoint history remain.

---

### transfermember

Move a player from one clan to another.

**Permission needed:** Co-Leader or higher

**How to use:**

`/transfermember player:PLAYER_NAME clan:DESTINATION_CLAN`

**Parameters:**
- `player` - Player name or tag (autocomplete available)
- `clan` - Destination clan (autocomplete available)

**What happens:**
1. Bot updates the player's clan in the database
2. Player keeps their same role in the new clan
3. Discord roles update if configured

**Example:**
```
/transfermember player:MaxPower clan:LOST_MAIN
```

**You'll see:**
```
✅ MaxPower transferred
   From: LOST F2P
   To: LOST MAIN
   Role: Elder
```

💡 **Use this when:** Someone moves between clans in your family.

---

### editmember

Change a player's clan role.

**Permission needed:** Co-Leader or higher

**How to use:**

`/editmember player:PLAYER_NAME role:NEW_ROLE`

**Parameters:**
- `player` - Player name or tag (autocomplete available)
- `role` - New role (choices: Leader, Co-Leader, Elder, Member)

**What happens:**
1. Bot updates the player's role in the database
2. Discord roles update if configured
3. Player's permissions in the bot update

**Example:**
```
/editmember player:MaxPower role:Co-Leader
```

**You'll see:**
```
✅ MaxPower promoted
   Old role: Elder
   New role: Co-Leader
```

💡 **Use this when:** Someone gets promoted or demoted in the clan.

---

### listmembers

List all members of a clan.

**How to use:**

`/listmembers clan:CLAN_NAME`

**Parameters:**
- `clan` - Clan name or tag (autocomplete available)

**What you'll see:**
- All clan members organized by role
- Each member shows name and player tag
- Total member count

**Example:**
```
/listmembers clan:LOST_F2P
```

**Output:**
```
📋 Members of LOST F2P

👑 Leaders (1):
  - MaxPower (#YR8UVQQ8Q)

⭐ Co-Leaders (3):
  - PlayerTwo (#ABC123)
  - PlayerThree (#DEF456)
  - PlayerFour (#GHI789)

🛡️ Elders (5):
  - PlayerFive (#JKL012)
  - PlayerSix (#MNO345)
  ...

⚔️ Members (41):
  - PlayerSeven (#PQR678)
  ...

Total: 50 members
```

💡 **Use this to:** See everyone in your clan at a glance.

---

### memberstatus

Check synchronization between the bot's database and the game.

**Permission needed:** Co-Leader or higher

**How to use:**

`/memberstatus clan:CLAN_NAME`

**Parameters:**
- `clan` - Clan name or tag (autocomplete available)
- `disable_rolecheck` - (Optional) Set to "true" to skip Discord role checking

**What you'll see:**
- Players in game but not in bot database
- Players in bot database but not in game
- Players with mismatched roles between game and database
- Players with wrong Discord roles

**Example:**
```
/memberstatus clan:LOST_F2P
```

**Output:**
```
🔍 Member Status Check - LOST F2P

✅ In Sync: 45 members

⚠️ In Game But Not in Database (3):
  - NewPlayer (#NEW123) - Elder in game
  - AnotherNew (#NEW456) - Member in game
  - JustJoined (#NEW789) - Member in game

⚠️ In Database But Not in Game (2):
  - OldPlayer (#OLD123) - Was Elder
  - LeftPlayer (#OLD456) - Was Member

⚠️ Role Mismatches (1):
  - MaxPower (#YR8UVQQ8Q)
    Database: Elder
    Game: Co-Leader
    → Role needs update

🔄 Discord Role Issues (2):
  - PlayerTwo: Missing "Elder" role
  - PlayerThree: Has "Member" role but should have "Elder"

[Refresh Button]
```

💡 **Use this to:** Find issues after promotions/demotions or when members join/leave.

💡 **Tip:** Click the Refresh button to re-check after fixing issues.

---

### cwlmemberstatus

Check which members of a role are in a CWL roster.

**Permission needed:** Co-Leader or higher

**How to use:**

`/cwlmemberstatus team_role:@ROLE cwl_clan_tag:CLAN_TAG`

**Parameters:**
- `team_role` - Discord role to check
- `cwl_clan_tag` - CWL clan tag (can be main or side clan, autocomplete available)

**What you'll see:**
- Members in the CWL roster
- Members not in the CWL roster
- Total counts

**Example:**
```
/cwlmemberstatus team_role:@Red_Team cwl_clan_tag:#2PP
```

**Output:**
```
🏆 CWL Status Check - Red Team
Clan: LOST F2P (#2PP)

✅ In CWL Roster (12):
  - MaxPower
  - PlayerTwo
  - PlayerThree
  ...

❌ Not in CWL Roster (3):
  - PlayerSix
  - PlayerSeven
  - PlayerEight

Total: 12/15 team members in CWL
```

💡 **Use this during CWL signup to:** Make sure your team members are registered.

---

### clanconfig

Configure clan settings like kickpoint limits and Discord roles.

**Permission needed:** Leader only

**How to use:**

`/clanconfig clan:CLAN_NAME`

**Parameters:**
- `clan` - Clan name or tag (autocomplete available)

**What happens:**
1. A popup form appears with current settings
2. You can edit:
   - Maximum kickpoints allowed
   - Minimum season wins required
   - Days until kickpoints expire
   - Discord role IDs for each clan rank
3. Bot saves your changes

**Example:**

```
/clanconfig clan:LOST_F2P
```

**Popup shows:**
```
Max Kickpoints: 10
Min Season Wins: 50
Kickpoints Expire After (days): 30
Leader Role ID: 123456789
Co-Leader Role ID: 234567890
Elder Role ID: 345678901
Member Role ID: 456789012
```

Change max kickpoints to 8:
```
✅ LOST F2P configuration updated
   Max Kickpoints: 10 → 8
```

**How to get role IDs:**
1. Enable Developer Mode in Discord (Settings → Advanced)
2. Right-click the role → Copy ID
3. Paste the ID into the form

💡 **Settings explained:**
- **Max Kickpoints:** When someone reaches this, they should be kicked
- **Min Season Wins:** How many wins required per season
- **Expire Days:** How long kickpoints last before auto-expiring
- **Role IDs:** Discord roles to assign based on in-game rank

---

## Clan Events Commands

### raidping

Ping members who haven't completed their raid attacks.

**Permission needed:** Co-Leader or higher

**How to use:**

`/raidping clan:CLAN_NAME`

**Parameters:**
- `clan` - Clan name or tag (autocomplete available)

**What you'll see:**
- List of members with incomplete raids
- Discord mentions for linked players
- Number of attacks used vs. total available
- Total count of members who need to raid

**Example:**
```
/raidping clan:LOST_F2P
```

**Output:**
```
⚠️ Incomplete Raids - LOST F2P
Raid Weekend ends in 18 hours!

@MaxPower - 3/6 attacks used
@PlayerTwo - 0/6 attacks used
@PlayerThree - 4/5 attacks used
PlayerNotLinked (#ABC123) - 2/6 attacks used

Total: 4 members need to complete raids
```

💡 **Use this:** On Sunday to remind everyone to finish their raids.

💡 **Tip:** The bot checks actual attacks used from the game API, not just participation.

---

### checkroles

Verify that Discord roles match in-game clan ranks.

**Permission needed:** Co-Leader or higher

**How to use:**

`/checkroles clan:CLAN_NAME`

**Parameters:**
- `clan` - Clan name or tag (autocomplete available)

**What you'll see:**
- Statistics on linked vs. unlinked members
- Statistics on correct vs. incorrect Discord roles
- List of members with role issues
- Refresh button to re-check

**Example:**
```
/checkroles clan:LOST_F2P
```

**Output:**
```
🔍 Role Check - LOST F2P

📊 Link Statistics:
✅ Linked: 42 members
❌ Unlinked: 8 members

📊 Role Statistics (Linked Members):
✅ Correct Roles: 38 members
⚠️ Incorrect Roles: 4 members

⚠️ Members With Role Issues:

MaxPower (#YR8UVQQ8Q)
  Should have: Co-Leader role
  Actually has: Elder role

PlayerTwo (#ABC123)
  Should have: Elder role
  Actually has: Member role

PlayerThree (#DEF456)
  Should have: Member role
  Currently has: No clan role

PlayerFour (#GHI789)
  Not linked to Discord
  Game role: Elder

[Refresh Button]
```

💡 **Use this to:** Find and fix Discord role issues after promotions/demotions.

💡 **Tip:** Click Refresh after manually fixing roles to verify they're correct.

---

### wins

View season wins statistics for a player or entire clan.

**How to use:**

**Check your wins:**
```
/wins season:2024-12 player:YOUR_NAME
```

**Check clan wins:**
```
/wins season:2024-12 clan:CLAN_NAME
```

**Parameters:**
- `season` - Season in format YYYY-MM (autocomplete shows recent seasons)
- `player` - (Optional) Specific player to check
- `clan` - (Optional) Check entire clan

**What you'll see (player):**
- Wins at season start
- Current wins (or final if season ended)
- Total wins gained

**What you'll see (clan):**
- All members sorted by wins gained
- Each member shows start, end, and difference
- Total clan wins gained

**Example (player):**
```
/wins season:2024-12 player:MaxPower
```

**Output:**
```
📊 Season Wins - December 2024
MaxPower (#YR8UVQQ8Q)

Start: 125 wins (Dec 1)
Current: 156 wins (Dec 14)
Gained: +31 wins

✅ Above minimum (50 wins required)
```

**Example (clan):**
```
/wins season:2024-12 clan:LOST_F2P
```

**Output:**
```
📊 Clan Season Wins - LOST F2P
December 2024

Top Performers:
1. MaxPower: 125 → 156 (+31) ⭐
2. PlayerTwo: 200 → 228 (+28)
3. PlayerThree: 150 → 175 (+25)
...

Below Minimum (50 wins):
- PlayerSix: 15 → 35 (+20) ⚠️
- PlayerSeven: 50 → 65 (+15) ⚠️

Clan Total: +847 wins
Average per member: +17 wins
```

💡 **Use this to:** Check if members are meeting activity requirements.

---

### cwdonator

Randomly select war donors for a clan war.

**Permission needed:** Co-Leader or higher

**How to use:**

**Simple version:**
```
/cwdonator clan:CLAN_NAME
```

**Exclude leaders:**
```
/cwdonator clan:CLAN_NAME exclude_leaders:true
```

**Use fair rotation:**
```
/cwdonator clan:CLAN_NAME use_lists:true
```

**Parameters:**
- `clan` - Clan name or tag (autocomplete available)
- `exclude_leaders` - (Optional) Set to "true" to exclude leaders and co-leaders
- `use_lists` - (Optional) Set to "true" for fair rotation tracking

**What happens:**
1. Bot checks current war size
2. Bot determines how many donors needed (based on war size)
3. Bot randomly selects from eligible members
4. If using lists, bot tracks selections to ensure fairness

**Example:**
```
/cwdonator clan:LOST_F2P exclude_leaders:true use_lists:true
```

**Output:**
```
🎯 War Donors Selected - LOST F2P
War Size: 30v30 (6 donors needed)

Selected Donors:
1. MaxPower (#YR8UVQQ8Q)
2. PlayerTwo (#ABC123)
3. PlayerThree (#DEF456)
4. PlayerFour (#GHI789)
5. PlayerFive (#JKL012)
6. PlayerSix (#MNO345)

⚠️ @MaxPower @PlayerTwo @PlayerThree @PlayerFour @PlayerFive @PlayerSix
Please donate 5+ clan castle troops!

Excluded: 4 leaders/co-leaders
Using fair rotation: These players haven't donated recently
```

**Donor count by war size:**
- 5v5: 2 donors
- 10v10: 3 donors
- 15v15: 4 donors
- 20v20: 5 donors
- 25v25: 5 donors
- 30v30: 6 donors
- 40v40: 7 donors
- 50v50: 8 donors

💡 **What's "fair rotation"?** When `use_lists:true`, the bot remembers who donated last time and prioritizes people who haven't donated recently.

---

### listeningevent

Manage automated event monitoring for your clan.

This is a complex command with three subcommands.

#### /listeningevent add

Create a new automated event.

**Permission needed:** Co-Leader or higher

**How to use:**

`/listeningevent add clan:CLAN type:EVENT_TYPE duration:MILLISECONDS actiontype:ACTION channel:#CHANNEL`

**Parameters:**
- `clan` - Clan to monitor (autocomplete available)
- `type` - Event type (choices below)
- `duration` - Milliseconds before event end (or -1 for war start)
- `actiontype` - What to do when event fires (choices below)
- `channel` - Discord channel for messages
- `kickpoint_reason` - (Optional/Required) Kickpoint reason to use

**Event Types:**
- `Clan Games` - Monitor Clan Games (22nd-28th monthly)
- `Clan War` - Monitor Clan Wars
- `CWL Tag` - Monitor CWL daily attacks
- `Raid` - Monitor Raid Weekend

**Action Types:**
- `Info-Nachricht` - Just send a message (no penalties)
- `Kickpoint` - Send message AND add kickpoints
- `Benutzerdefinierte Nachricht` - Send custom message
- `CW Donator` - Select war donors (CW only)
- `Filler` - List war opt-outs (CW only)
- `Raidfails` - Analyze raid attacks (Raid only)

**Duration Examples:**
- `0` - Fire exactly at event end
- `3600000` - Fire 1 hour before end (3600 seconds × 1000)
- `86400000` - Fire 24 hours before end
- `-1` - Fire at war start (CW only)

**Example 1: Clan Games kickpoints**
```
/listeningevent add clan:LOST_F2P type:Clan_Games duration:0 actiontype:Kickpoint channel:#clan-logs kickpoint_reason:Clangames_unter_4000
```

**What happens:**
- When Clan Games end (28th at 1 PM)
- Bot checks everyone's score
- Bot sends message listing players under 4000 points
- Bot adds 2 kickpoints to each violator

**Example 2: War attack reminder**
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:3600000 actiontype:Info-Nachricht channel:#war-logs
```

**What happens:**
- 1 hour before war ends
- Bot checks who hasn't used all attacks
- Bot sends reminder message mentioning them
- No kickpoints added

**Example 3: War start fillers**
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:-1 actiontype:Filler channel:#war-prep
```

**What happens:**
- When war starts (preparation phase begins)
- Bot lists members opted OUT of war
- Helps leaders see who's not participating

**You'll see:**
```
✅ Listening event created!
   ID: #45
   Clan: LOST F2P
   Type: Clan Games
   Action: Add kickpoints
   Channel: #clan-logs
   Fires: At Clan Games end
```

#### /listeningevent list

View all configured events.

**How to use:**

**See all events:**
```
/listeningevent list
```

**Filter by clan:**
```
/listeningevent list clan:LOST_F2P
```

**What you'll see:**
- Event ID
- Clan name
- Event type
- Action type
- Target channel
- When it fires next

**Example:**
```
/listeningevent list clan:LOST_F2P
```

**Output:**
```
📋 Listening Events - LOST F2P

#45 - Clan Games
  Action: Add kickpoints (Clangames unter 4000)
  Channel: #clan-logs
  Next fire: Dec 28 at 1:00 PM (14 days)

#46 - Clan War
  Action: Info message
  Channel: #war-logs
  Next fire: Dec 15 at 4:00 PM (1 hour)

#47 - Raid Weekend
  Action: Add kickpoints (Raid unter 5 Angriffe)
  Channel: #raid-logs
  Next fire: Dec 16 at 7:00 AM (17 hours)

Total: 3 events
```

#### /listeningevent remove

Delete an automated event.

**Permission needed:** Co-Leader or higher

**How to use:**

`/listeningevent remove id:EVENT_ID`

**Parameters:**
- `id` - Event ID from `/listeningevent list`

**Example:**
```
/listeningevent remove id:45
```

**You'll see:**
```
✅ Listening event #45 removed
   This event will no longer fire
```

💡 **Tip:** Run `/listeningevent list` first to find the ID you want to remove.

---

## Discord Admin Commands

### checkreacts

Check which members of a role reacted to a message with a specific emoji.

**How to use:**

`/checkreacts role:@ROLE message_link:LINK emoji:EMOJI`

**Parameters:**
- `role` - Discord role to check
- `message_link` - Link to the message (right-click message → Copy Message Link)
- `emoji` - Emoji to check for

**What you'll see:**
- List of role members who reacted
- List of role members who didn't react
- Total counts

**Example:**
```
/checkreacts role:@RedTeam message_link:https://discord.com/channels/... emoji:✅
```

**Output:**
```
✅ Reacted (12 members):
  - @MaxPower
  - @PlayerTwo
  - @PlayerThree
  ...

❌ Didn't React (3 members):
  - @PlayerSix
  - @PlayerSeven
  - @PlayerEight

Total: 12/15 members reacted
```

💡 **Use this to:** Check who confirmed attendance for events or agreed to rules.

---

### teamcheck

Check distribution of members across multiple team roles.

**How to use:**

`/teamcheck memberrole:@ROLE team_role_1:@TEAM1 team_role_2:@TEAM2 ...`

**Parameters:**
- `memberrole` - Main member role to check
- `team_role_1` through `team_role_5` - Team roles to analyze
- `memberrole_2` - (Optional) Second member role to include

**What you'll see:**
- Count of members in each team
- List of members with no team
- List of members in multiple teams
- Balance statistics

**Example:**
```
/teamcheck memberrole:@ClanMember team_role_1:@RedTeam team_role_2:@BlueTeam team_role_3:@GreenTeam
```

**Output:**
```
📊 Team Distribution

Red Team: 15 members
Blue Team: 14 members
Green Team: 16 members

⚖️ Teams are balanced (max difference: 2)

❌ No Team (5 members):
  - @NewPlayer
  - @AnotherNew
  ...

⚠️ Multiple Teams (2 members):
  - @PlayerX (Red Team, Blue Team)
  - @PlayerY (Blue Team, Green Team)

Total members checked: 50
```

💡 **Use this to:** Ensure fair team distribution for events or competitions.

---

### deletemessages

Bulk delete messages in the current channel.

**Permission needed:** Manage Messages (Discord permission)

**How to use:**

`/deletemessages amount:NUMBER`

**Parameters:**
- `amount` - Number of messages to delete (1-100)

**What happens:**
1. Bot fetches the last N messages
2. Bot deletes them all at once
3. Bot confirms how many were deleted

**Example:**
```
/deletemessages amount:50
```

**You'll see:**
```
✅ Deleted 50 messages
```

**Limitations:**
- Maximum 100 messages per command
- Cannot delete messages older than 14 days (Discord limit)
- Bot needs "Manage Messages" permission

⚠️ **Use carefully:** Deleted messages cannot be recovered!

---

### reactionsrole

Give a Discord role to everyone who reacted with a specific emoji.

**Permission needed:** Manage Roles (Discord permission)

**How to use:**

`/reactionsrole messagelink:LINK emoji:EMOJI role:@ROLE`

**Parameters:**
- `messagelink` - Link to message (right-click → Copy Message Link)
- `emoji` - Emoji that users reacted with
- `role` - Role to assign

**What happens:**
1. Bot finds the message
2. Bot gets list of users who reacted with that emoji
3. Bot assigns the role to each user
4. Bot reports success count

**Example:**
```
/reactionsrole messagelink:https://discord.com/channels/... emoji:✅ role:@Verified
```

**You'll see:**
```
✅ Role @Verified assigned to 25 users
   Based on ✅ reactions
```

💡 **Use this to:** Assign roles based on event confirmations or rule acceptance.

**Requirements:**
- Bot must see the channel
- Bot's role must be higher than the role being assigned
- Bot needs "Manage Roles" permission

---

### restart

Restart the bot application.

**Permission needed:** Admin only

**How to use:**

`/restart`

**What happens:**
1. Bot confirms the restart
2. Bot shuts down
3. External process manager restarts it (if configured)

**Example:**
```
/restart
```

**You'll see:**
```
🔄 Restarting bot...
```

⚠️ **Note:** Bot must be run with an auto-restart system (like systemd, PM2, or Docker restart policy) for this to work properly.

💡 **Use this when:** Bot is behaving strangely or after configuration changes.

---

## Quick Command Reference

### Everyone Can Use:
- `/verify` - Link your account
- `/playerinfo` - Check your info
- `/kpmember` - View kickpoints
- `/setnick` - Change nickname
- `/wins` - Check wins
- `/listmembers` - See clan roster

### Co-Leader+:
- `/addmember`, `/removemember`, `/transfermember`, `/editmember` - Manage members
- `/kpadd`, `/kpremove`, `/kpedit` - Manage kickpoints
- `/kpaddreason`, `/kpremovereason`, `/kpeditreason` - Manage reasons
- `/memberstatus`, `/cwlmemberstatus`, `/checkroles` - Check status
- `/raidping` - Ping incomplete raids
- `/cwdonator` - Select donors
- `/listeningevent add/remove/list` - Manage events
- `/link`, `/relink`, `/unlink` - Link management

### Leader Only:
- `/clanconfig` - Configure clan settings

### Admin Only:
- `/restart` - Restart bot
- `/deletemessages` - Delete messages
- `/reactionsrole` - Assign roles by reactions

---

## Need More Help?

**For specific scenarios:** Check the "Getting Started" guide for common workflows.

**For technical details:** Check the other documentation files for developer-level information.

**For automated events:** See the complete Listening Events guide for in-depth examples.

**Having issues?** Ask your clan leader or check the troubleshooting section in the Getting Started guide.

# Common Scenarios & Workflows

Real-world examples of using Lost Manager 2 for various clan management tasks.

---

## Table of Contents

### New Member Workflows
- [New Member Joins Clan](#new-member-joins-clan)
- [Member Has Multiple Accounts](#member-has-multiple-accounts)
- [Member Switches Discord Accounts](#member-switches-discord-accounts)

### Clan Events
- [Preparing for Clan War](#preparing-for-clan-war)
- [After War Ends](#after-war-ends)
- [Managing Raid Weekend](#managing-raid-weekend)
- [Tracking Clan Games](#tracking-clan-games)
- [Setting Up CWL](#setting-up-cwl)

### Kickpoint Management
- [Adding Kickpoints Manually](#adding-kickpoints-manually)
- [Reviewing Member Kickpoints](#reviewing-member-kickpoints)
- [Handling Appeals](#handling-appeals)
- [End of Month Review](#end-of-month-review)

### Role Management
- [Member Gets Promoted](#member-gets-promoted)
- [Syncing All Roles](#syncing-all-roles)
- [New Elder Needs Bot Access](#new-elder-needs-bot-access)

### Clan Transitions
- [Member Transfers to Sister Clan](#member-transfers-to-sister-clan)
- [Member Leaves Clan](#member-leaves-clan)
- [Member Returns After Leave](#member-returns-after-leave)

### Event Automation
- [Setting Up Full Automation](#setting-up-full-automation)
- [Testing New Event Setup](#testing-new-event-setup)
- [Adjusting Penalties](#adjusting-penalties)

---

## New Member Workflows

### New Member Joins Clan

**Scenario:** PlayerX just joined your clan "LOST F2P" and needs to be set up in the bot.

**Steps:**

1. **Add member to bot database** (Co-Leader)
   ```
   /addmember clan:LOST_F2P player:#ABC123 role:Member
   ```

2. **Member links their Discord account** (Member)
   - Get API token from Clash of Clans game settings
   - Run verify command:
   ```
   /verify tag:#ABC123 apitoken:xyz789
   ```

3. **Check setup is correct** (Co-Leader)
   ```
   /playerinfo player:PlayerX
   ```
   
   **Expected output:**
   ```
   PlayerX (#ABC123)
   Discord: @PlayerX#1234
   Clan: LOST F2P (Member)
   Kickpoints: 0/10
   ✅ Verified and linked
   ```

4. **Assign Discord roles** (Co-Leader or Admin)
   - Bot should auto-assign "Member" role
   - Check with:
   ```
   /checkroles clan:LOST_F2P
   ```
   - Manually assign if needed

**Common Issues:**

**Problem:** Member can't find API token
- **Solution:** Guide them: CoC Settings → Show API Token
- Must be TH8+

**Problem:** API token invalid
- **Solution:** Token changes every time they view it. Get fresh token and use immediately.

**Problem:** Member doesn't get Discord role
- **Solution:** Check bot permissions and role hierarchy. Manually assign role if needed.

---

### Member Has Multiple Accounts

**Scenario:** PlayerX has 3 Clash accounts and wants them all linked to one Discord.

**Steps:**

1. **Member links first account themselves**
   ```
   /verify tag:#MAIN123 apitoken:token1
   ```

2. **Add other accounts to database** (Co-Leader)
   ```
   /addmember clan:LOST_F2P player:#ALT456 role:Member
   /addmember clan:LOST_MAIN player:#ALT789 role:Elder
   ```

3. **Link other accounts to same Discord** (Co-Leader)
   ```
   /link tag:#ALT456 user:@PlayerX
   /link tag:#ALT789 user:@PlayerX
   ```

4. **Verify all accounts linked** (Member or Co-Leader)
   ```
   /playerinfo user:@PlayerX
   ```
   
   **Expected output:**
   ```
   @PlayerX has 3 linked accounts:
   
   MainAccount (#MAIN123)
   Clan: LOST F2P (Member)
   Kickpoints: 0/10
   
   AltAccount1 (#ALT456)
   Clan: LOST F2P (Member)
   Kickpoints: 2/10
   
   AltAccount2 (#ALT789)
   Clan: LOST MAIN (Elder)
   Kickpoints: 0/10
   ```

**Tips:**
- Main account is first one linked
- Each account tracked separately
- Kickpoints don't combine across accounts
- Discord roles based on highest rank

---

### Member Switches Discord Accounts

**Scenario:** PlayerX switched from @OldDiscord to @NewDiscord and needs accounts relinked.

**Steps:**

1. **Find all accounts to transfer** (Co-Leader)
   ```
   /playerinfo user:@OldDiscord
   ```
   
   **Output shows:**
   ```
   Account1 (#ABC123)
   Account2 (#DEF456)
   ```

2. **Relink each account** (Co-Leader)
   ```
   /relink tag:#ABC123 user:@NewDiscord
   /relink tag:#DEF456 user:@NewDiscord
   ```

3. **Verify transfer complete**
   ```
   /playerinfo user:@NewDiscord
   ```
   
   **Should show both accounts now**

4. **Old Discord account cleanup** (Optional)
   ```
   /playerinfo user:@OldDiscord
   ```
   
   **Should show no linked accounts**

**What happens automatically:**
- Verified role moves to new account
- Clan roles assign to new account
- Old account loses clan roles

---

## Clan Events

### Preparing for Clan War

**Scenario:** War starts in 1 hour. You want to check roster and assign donors.

**Steps:**

1. **Check who's in war** (Co-Leader)
   - Wait for preparation phase to start
   - Check in-game roster

2. **Verify members are in bot**
   ```
   /memberstatus clan:LOST_F2P
   ```
   
   **Look for:**
   - Members in game but not bot
   - Add missing members if any

3. **Select war donors** (Co-Leader)
   ```
   /cwdonator clan:LOST_F2P exclude_leaders:true use_lists:true
   ```
   
   **Output:**
   ```
   🎯 War Donors Selected
   War Size: 30v30
   
   Donors (6 needed):
   @Player1
   @Player2
   @Player3
   @Player4
   @Player5
   @Player6
   
   Please donate clan castle troops!
   ```

4. **Send to war channel**
   - Copy donor list
   - Post in #war-prep or war channel
   - Pin message

**Automated Alternative:**

Set up event to do this automatically:
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:-1 actiontype:CW_Donator channel:#war-prep
```

Now donors auto-selected when war starts!

---

### After War Ends

**Scenario:** War just ended. You need to check who missed attacks and possibly add kickpoints.

**Steps:**

1. **Wait 5 minutes** (Important)
   - Clash API needs time to update
   - Don't check immediately

2. **Check war performance** (Manual method)
   - Look in game at attacks used
   - Note who missed attacks

3. **Add kickpoints if needed** (Co-Leader)
   
   **For each violator:**
   ```
   /kpadd player:PlayerWhoMissed reason:CW_Attack_vergessen
   ```
   
   **Popup appears:**
   ```
   Reason: CW Attack vergessen (pre-filled)
   Amount: 3 (pre-filled)
   Date: 2024-12-14 (today)
   Notes: [Optional - e.g., "0/2 attacks used"]
   ```

4. **Announce in war channel**
   ```
   War Results:
   
   ⚠️ Missed Attacks:
   @Player1 - 1/2 attacks (3 kickpoints)
   @Player2 - 0/2 attacks (3 kickpoints)
   
   Please use all attacks next war!
   ```

**Automated Alternative:**

Set up event for automatic checking:
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:0 actiontype:Kickpoint channel:#war-logs kickpoint_reason:CW_Attack_vergessen
```

**With reminder before war ends:**
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:3600000 actiontype:Info-Nachricht channel:#war-logs
```

Now you get:
- Reminder 1 hour before war ends
- Automatic kickpoints after war ends

---

### Managing Raid Weekend

**Scenario:** It's Sunday evening, raid ends Monday morning. You want to check progress.

**Steps:**

1. **Check incomplete raids** (Co-Leader)
   ```
   /raidping clan:LOST_F2P
   ```
   
   **Output:**
   ```
   ⚠️ Incomplete Raids - LOST F2P
   Raid ends in 8 hours!
   
   Need to Raid:
   @Player1 - 3/6 attacks used
   @Player2 - 0/6 attacks used
   @Player3 - 4/5 attacks used
   
   Total: 3 members need to complete raids
   ```

2. **Post reminder in raid channel**
   - Tag incomplete members
   - Remind about deadline

3. **After raid ends (Monday)** (Wait 15 minutes for API)
   ```
   /raidping clan:LOST_F2P
   ```
   
   **If still showing incomplete, add kickpoints:**
   ```
   /kpadd player:Player2 reason:Raid_unter_5_Angriffe
   ```

**Automated Setup:**

Set up automatic checking:
```
/listeningevent add clan:LOST_F2P type:Raid duration:0 actiontype:Kickpoint channel:#raid-logs kickpoint_reason:Raid_unter_5_Angriffe
```

**With raid fails analysis:**
```
/listeningevent add clan:LOST_F2P type:Raid duration:0 actiontype:Raidfails channel:#raid-logs kickpoint_reason:Raid_Fails
```

This tracks both incomplete AND bad hits!

---

### Tracking Clan Games

**Scenario:** Clan Games are running. You want to ensure everyone scores 4000+ points.

**Steps:**

1. **Wait for Clan Games to end** (28th at 12 PM)
   - No manual checking during event
   - Bot saves starting scores automatically

2. **One hour after end** (28th at 1 PM)
   - If automated: Bot sends results
   - If manual: Check in-game

3. **Manual checking** (if no automation)
   - Check each member's Clan Games score in-game
   - Note who scored under 4000
   - Add kickpoints:
   ```
   /kpadd player:LowScorer reason:Clangames_unter_4000
   ```

4. **Post results**
   ```
   📊 Clan Games Results
   
   ✅ Clan completed! 50,000 points
   
   ⚠️ Below 4000 points:
   @Player1 - 3,250 points (2 kickpoints)
   @Player2 - 1,500 points (2 kickpoints)
   @Player3 - 0 points (2 kickpoints)
   ```

**Automated Setup:**

Set up once, works every month:
```
/listeningevent add clan:LOST_F2P type:Clan_Games duration:0 actiontype:Kickpoint channel:#clan-logs kickpoint_reason:Clangames_unter_4000
```

**What happens automatically:**
- Bot saves scores on 22nd (start)
- Bot checks scores on 28th at 1 PM (end + 1 hour)
- Bot posts results
- Bot adds kickpoints to violators

---

### Setting Up CWL

**Scenario:** CWL starting tomorrow. You need to check roster and set up tracking.

**Steps:**

1. **Finalize CWL roster in-game**
   - Sign up main clan members
   - Add side clan members if needed

2. **Check Discord team in roster** (Co-Leader)
   ```
   /cwlmemberstatus team_role:@RedTeam cwl_clan_tag:#2PP
   ```
   
   **Output:**
   ```
   🏆 CWL Status - Red Team
   
   ✅ In Roster (12):
   @Player1
   @Player2
   ...
   
   ❌ Not in Roster (3):
   @Player10
   @Player11
   @Player12
   
   Total: 12/15 in CWL
   ```

3. **Contact missing members**
   - Ask if they want to be in CWL
   - Adjust roster accordingly

4. **Set up CWL tracking** (Optional)
   ```
   /listeningevent add clan:LOST_F2P type:CWL_Tag duration:0 actiontype:Info-Nachricht channel:#cwl-logs
   ```
   
   **Or with kickpoints:**
   ```
   /listeningevent add clan:LOST_F2P type:CWL_Tag duration:0 actiontype:Kickpoint channel:#cwl-logs kickpoint_reason:CWL_Attack_vergessen
   ```

**During CWL:**

Bot automatically posts after each day:
```
⚠️ CWL Day 1 Results - LOST F2P

Missed Attacks:
@Player1 - 0/1 attacks
@Player2 - 0/1 attacks
```

**After CWL ends:**

Review performance:
```
/kpclan clan:LOST_F2P
```

See who accumulated kickpoints during event.

---

## Kickpoint Management

### Adding Kickpoints Manually

**Scenario:** Player broke a clan rule (rude behavior, wrong donated troops, etc.)

**Steps:**

1. **Decide the penalty** (Leader/Co-Leader)
   - Check clan rules
   - Determine kickpoint amount
   - Ensure reason exists

2. **Check if reason template exists**
   ```
   /kpinfo clan:LOST_F2P
   ```
   
   **If reason doesn't exist, create it:**
   ```
   /kpaddreason clan:LOST_F2P reason:Unhöfliches_Verhalten amount:5
   ```

3. **Add the kickpoint**
   ```
   /kpadd player:RuleBreaker reason:Unhöfliches_Verhalten
   ```
   
   **Popup:**
   ```
   Reason: Unhöfliches Verhalten (pre-filled)
   Amount: 5 (pre-filled)
   Date: 2024-12-14 (today)
   Notes: [Add context: "Insulted clan member in chat"]
   ```

4. **Submit and notify member**
   - Message member in Discord
   - Explain what they did wrong
   - Mention kickpoints added
   - Remind of clan rules

**Example message:**
```
@RuleBreaker

You received 5 kickpoints for rude behavior in clan chat.

Reason: Insulted another member
Points: 5
Current total: 7/10
Expires: Jan 13, 2025

Please be respectful to all clan members. Further violations may result in removal.

Check your kickpoints: /kpmember player:RuleBreaker
```

---

### Reviewing Member Kickpoints

**Scenario:** You want to see who's close to the kickpoint limit.

**Steps:**

1. **Check clan-wide kickpoints** (Co-Leader)
   ```
   /kpclan clan:LOST_F2P
   ```
   
   **Output:**
   ```
   📊 Kickpoints - LOST F2P
   
   ⚠️ High (7+ points):
   Player1: 9/10 ⚠️⚠️
   Player2: 8/10 ⚠️
   Player3: 7/10 ⚠️
   
   Medium (4-6 points):
   Player4: 6/10
   Player5: 5/10
   Player6: 4/10
   
   Low (1-3 points):
   Player7: 3/10
   Player8: 2/10
   ...
   
   Total: 15 members with kickpoints
   ```

2. **Review individuals with high kickpoints**
   ```
   /kpmember player:Player1
   ```
   
   **Check:**
   - What violations they have
   - When they expire
   - If recent behavior improved

3. **Take action based on findings**
   
   **Option A: Talk to member**
   - Warn about kickpoint limit
   - Give chance to improve
   - Check again next week

   **Option B: Remove from clan**
   - If at/over limit
   - If continuing violations
   - If no improvement
   
   ```
   /removemember player:Player1
   ```

4. **Document decisions** (Optional)
   - Keep notes on who you warned
   - Track if behavior improves
   - Fair enforcement

**Weekly Review Workflow:**

Every Sunday:
1. Run `/kpclan` for your clan
2. Note members over 7 kickpoints
3. Check `/kpmember` for details
4. Message high-kickpoint members
5. Remove if at limit and no improvement

---

### Handling Appeals

**Scenario:** Member appeals a kickpoint they think was unfair.

**Steps:**

1. **Listen to their case**
   - What happened?
   - Do they have proof?
   - Valid reason for violation?

2. **Review the kickpoint**
   ```
   /kpmember player:AppealingMember
   ```
   
   **Check:**
   - Date of violation
   - Reason given
   - Amount
   - Any notes

3. **Verify the facts**
   - Check game logs
   - Ask other co-leaders
   - Review screenshots if provided

4. **Make decision**

   **If appeal valid:**
   ```
   /kpremove id:KICKPOINT_ID
   ```
   
   **Message member:**
   ```
   @Member
   
   Your appeal has been reviewed and accepted.
   
   Kickpoint #1234 has been removed.
   Reason: [Explain why it was removed]
   New total: X/10 kickpoints
   
   Thank you for bringing this to our attention.
   ```

   **If appeal denied:**
   ```
   @Member
   
   Your appeal has been reviewed.
   
   After checking the facts, the kickpoint stands.
   Reason: [Explain why it remains]
   Current total: X/10 kickpoints
   Expires: [Date]
   
   Please ensure you follow clan rules going forward.
   ```

5. **Document outcome**
   - Note in co-leader chat
   - Keeps records clear
   - Prevents repeat appeals

---

### End of Month Review

**Scenario:** Last day of month. You want to clean up and check expirations.

**Steps:**

1. **Check who has kickpoints** (Co-Leader)
   ```
   /kpclan clan:LOST_F2P
   ```

2. **Review individual expirations**
   ```
   /kpmember player:MemberName
   ```
   
   **Example output:**
   ```
   Player1 - Total: 5/10
   
   #1001 - Expires tomorrow ✅
   #1002 - Expires in 10 days
   #1003 - Expires in 20 days
   ```

3. **Note who's getting clean slate**
   - Kickpoints expiring soon
   - Members improving behavior
   - Consider second chances

4. **Check if anyone over limit**
   - Still over limit after expirations?
   - Time to enforce removal?

5. **Announce monthly report**
   ```
   📊 Monthly Kickpoint Report - December
   
   ✅ Clean Slate (expired):
   @Player1 - Now at 0/10
   @Player2 - Now at 2/10
   
   ⚠️ Still High:
   @Player3 - 8/10 (expires Jan 15)
   @Player4 - 7/10 (expires Jan 20)
   
   🎉 No Kickpoints:
   35 members with clean record!
   
   Great job following clan rules!
   ```

**Automated Reminder:**

Set calendar reminder for last day of each month to run this review.

---

## Role Management

### Member Gets Promoted

**Scenario:** PlayerX promoted from Elder to Co-Leader in-game. Need to update bot and Discord.

**Steps:**

1. **Update in bot** (Co-Leader or Leader)
   ```
   /editmember player:PlayerX role:Co-Leader
   ```
   
   **Output:**
   ```
   ✅ PlayerX promoted
   Old role: Elder
   New role: Co-Leader
   ```

2. **Update Discord role** (Admin or Bot)
   - Bot might auto-assign (if configured)
   - If not, manually assign "Co-Leader" Discord role
   - Remove old "Elder" Discord role

3. **Verify changes**
   ```
   /playerinfo player:PlayerX
   ```
   
   **Should show:**
   ```
   PlayerX (#ABC123)
   Discord: @PlayerX#1234
   Clan: LOST F2P (Co-Leader) ✅
   ```

4. **Test permissions**
   - Have PlayerX try a co-leader command
   - Example:
   ```
   /addmember clan:LOST_F2P player:#TEST123 role:Member
   ```
   
   - Should work without errors

5. **Announce promotion** (Optional)
   ```
   🎉 Congratulations @PlayerX!
   
   Promoted to Co-Leader!
   
   @PlayerX now has access to:
   - Member management commands
   - Kickpoint management
   - Event management
   - Role checking
   
   Welcome to the leadership team!
   ```

---

### Syncing All Roles

**Scenario:** Multiple promotions/demotions happened. Need to sync everything.

**Steps:**

1. **Run sync check** (Co-Leader)
   ```
   /memberstatus clan:LOST_F2P
   ```
   
   **Output shows:**
   ```
   🔍 Member Status Check
   
   ✅ In Sync: 42 members
   
   ⚠️ Role Mismatches (5):
   
   Player1 (#ABC123)
   - Database: Elder
   - Game: Co-Leader
   → Needs update
   
   Player2 (#DEF456)
   - Database: Member
   - Game: Elder
   → Needs update
   
   [3 more...]
   ```

2. **Update each mismatch**
   
   For each mismatch:
   ```
   /editmember player:Player1 role:Co-Leader
   /editmember player:Player2 role:Elder
   ```

3. **Check Discord roles**
   ```
   /checkroles clan:LOST_F2P
   ```
   
   **Output shows:**
   ```
   🔍 Role Check
   
   ⚠️ Incorrect Discord Roles (3):
   
   Player1: Should have Co-Leader role
   Player2: Should have Elder role
   Player3: Has old Member role
   ```

4. **Fix Discord roles**
   - Manually assign correct roles
   - Or configure bot role IDs:
   ```
   /clanconfig clan:LOST_F2P
   ```
   
   **Set role IDs for auto-assignment**

5. **Final verification**
   ```
   /memberstatus clan:LOST_F2P
   /checkroles clan:LOST_F2P
   ```
   
   **Should show all in sync**

**Best Practice:**

Run this check:
- After every batch of promotions/demotions
- Weekly as routine maintenance
- Before important events (CWL signups)

---

### New Elder Needs Bot Access

**Scenario:** You promoted someone to Elder, but they can't use bot commands.

**Important:** Elders don't get special bot permissions!

**Explanation:**
- In-game Elder = no bot management access
- Bot permissions require Co-Leader or higher
- Elders can only use view commands

**View Commands Elders Can Use:**
- `/playerinfo` - Check player info
- `/kpmember` - View kickpoints
- `/listmembers` - See clan roster
- `/wins` - Check wins
- `/kpclan` - View clan kickpoints
- `/kpinfo` - See kickpoint reasons

**Commands Elders CANNOT Use:**
- `/addmember`, `/removemember` - Member management
- `/kpadd`, `/kpremove` - Kickpoint management
- `/listeningevent` - Event management
- Other management commands

**Solution:**

If Elder needs management access:
1. Promote to Co-Leader in-game
2. Update bot:
   ```
   /editmember player:ElderName role:Co-Leader
   ```
3. Now they have full bot access

**Alternative:**

If staying Elder:
- They help by viewing information
- Report issues to Co-Leaders
- Co-Leaders make the changes

---

## Clan Transitions

### Member Transfers to Sister Clan

**Scenario:** PlayerX moving from "LOST F2P" to "LOST MAIN" in clan family.

**Steps:**

1. **Transfer in bot** (Co-Leader)
   ```
   /transfermember player:PlayerX clan:LOST_MAIN
   ```
   
   **Output:**
   ```
   ✅ PlayerX transferred
   From: LOST F2P (Elder)
   To: LOST MAIN (Elder)
   ```

2. **Verify transfer**
   ```
   /playerinfo player:PlayerX
   ```
   
   **Should show:**
   ```
   PlayerX (#ABC123)
   Discord: @PlayerX#1234
   Clan: LOST MAIN (Elder) ✅
   ```

3. **Update Discord roles**
   - Remove LOST F2P clan roles
   - Add LOST MAIN clan roles
   - Keep same rank role (Elder stays Elder)

4. **Update role if changing**
   
   **If promoted during transfer:**
   ```
   /editmember player:PlayerX role:Co-Leader
   ```

5. **Announce in both clans**
   
   **In LOST F2P:**
   ```
   @PlayerX is transferring to LOST MAIN
   Good luck and thanks for your service! 🎉
   ```
   
   **In LOST MAIN:**
   ```
   Welcome @PlayerX to LOST MAIN!
   Transferring from LOST F2P as Elder
   ```

**What Transfers:**
- Discord link (stays connected)
- Kickpoint history (all clans visible)
- Season wins (tracked per account)

**What Doesn't Transfer:**
- Kickpoints tied to old clan (stay with F2P clan)
- Clan-specific Discord roles
- Old clan chat access

---

### Member Leaves Clan

**Scenario:** PlayerX left the clan. Need to clean up bot records.

**Steps:**

1. **Remove from bot** (Co-Leader)
   ```
   /removemember player:PlayerX
   ```
   
   **Output:**
   ```
   ✅ PlayerX removed from LOST F2P
   Ex-member role assigned (if configured)
   ```

2. **Verify removal**
   ```
   /playerinfo player:PlayerX
   ```
   
   **Should show:**
   ```
   PlayerX (#ABC123)
   Discord: @PlayerX#1234
   Clan: Not in clan
   ```

3. **Update Discord roles**
   - Remove all clan-specific roles
   - Add "Ex-Member" role (if configured)
   - Keep verified role

4. **Check if they have kickpoints**
   ```
   /kpmember player:PlayerX
   ```
   
   **Kickpoints remain in database for records**

**What Happens:**
- Player stays in bot database
- Discord link remains (can return later)
- Kickpoint history preserved
- Clan assignment removed
- Can rejoin anytime

**If they ask for removal entirely:**
- Use `/unlink` to remove Discord connection
- Player record stays for history

---

### Member Returns After Leave

**Scenario:** PlayerX left months ago, now wants to return.

**Steps:**

1. **Check if still in bot**
   ```
   /playerinfo player:PlayerX
   ```
   
   **If found:**
   ```
   PlayerX (#ABC123)
   Discord: @PlayerX#1234
   Clan: Not in clan
   Kickpoints: 3/10 (from before)
   ```

2. **Review their history** (Co-Leader discussion)
   - Why did they leave?
   - Any kickpoints from before?
   - Did kickpoints expire?
   - Good standing?

3. **Add back to clan** (if approved)
   ```
   /addmember clan:LOST_F2P player:#ABC123 role:Member
   ```
   
   **Or transfer if coming from elsewhere:**
   ```
   /transfermember player:PlayerX clan:LOST_F2P
   ```

4. **Check kickpoint status**
   ```
   /kpmember player:PlayerX
   ```
   
   **Decide if old kickpoints still apply:**
   - If expired: Clean slate
   - If valid: Start with existing points
   - If cleared: Remove old ones:
     ```
     /kpremove id:OLD_KP_ID
     ```

5. **Update Discord roles**
   - Remove "Ex-Member" role
   - Add appropriate clan role
   - Update nickname

6. **Welcome back message**
   ```
   Welcome back @PlayerX! 🎉
   
   You've been added to LOST F2P as Member
   Current kickpoints: 0/10 (clean slate)
   
   Please review clan rules: [rules link]
   ```

---

## Event Automation

### Setting Up Full Automation

**Scenario:** New clan wants complete automation for all events.

**Steps:**

**1. Create kickpoint reasons** (Leader/Co-Leader)

```
/kpaddreason clan:NEW_CLAN reason:CW_Attack_vergessen amount:3
/kpaddreason clan:NEW_CLAN reason:Clangames_unter_4000 amount:2
/kpaddreason clan:NEW_CLAN reason:Raid_unter_5_Angriffe amount:2
/kpaddreason clan:NEW_CLAN reason:CWL_Attack_vergessen amount:3
/kpaddreason clan:NEW_CLAN reason:Raid_Fails amount:2
```

**2. Verify reasons created**

```
/kpinfo clan:NEW_CLAN
```

**Output:**
```
CW Attack vergessen - 3 points
Clangames unter 4000 - 2 points
Raid unter 5 Angriffe - 2 points
CWL Attack vergessen - 3 points
Raid Fails - 2 points
```

**3. Create Clan Games event**

```
/listeningevent add clan:NEW_CLAN type:Clan_Games duration:0 actiontype:Kickpoint channel:#clan-logs kickpoint_reason:Clangames_unter_4000
```

**4. Create War events**

**War start - fillers:**
```
/listeningevent add clan:NEW_CLAN type:Clan_War duration:-1 actiontype:Filler channel:#war-prep
```

**War start - donors:**
```
/listeningevent add clan:NEW_CLAN type:Clan_War duration:-1 actiontype:CW_Donator channel:#war-prep
```

**War reminder (1 hour before):**
```
/listeningevent add clan:NEW_CLAN type:Clan_War duration:3600000 actiontype:Info-Nachricht channel:#war-logs
```

**War end - kickpoints:**
```
/listeningevent add clan:NEW_CLAN type:Clan_War duration:0 actiontype:Kickpoint channel:#war-logs kickpoint_reason:CW_Attack_vergessen
```

**5. Create Raid events**

**Raid incomplete:**
```
/listeningevent add clan:NEW_CLAN type:Raid duration:0 actiontype:Kickpoint channel:#raid-logs kickpoint_reason:Raid_unter_5_Angriffe
```

**Raid fails:**
```
/listeningevent add clan:NEW_CLAN type:Raid duration:0 actiontype:Raidfails channel:#raid-logs kickpoint_reason:Raid_Fails
```

**6. Create CWL event**

```
/listeningevent add clan:NEW_CLAN type:CWL_Tag duration:0 actiontype:Kickpoint channel:#cwl-logs kickpoint_reason:CWL_Attack_vergessen
```

**7. Verify all events created**

```
/listeningevent list clan:NEW_CLAN
```

**Should show all 9 events**

**8. Announce to clan**

```
🤖 Automated Events Now Active!

The bot will now automatically:

✅ Track Clan Games (monthly)
✅ Select war donors
✅ Remind before wars end
✅ Check war attacks
✅ Monitor raid participation
✅ Analyze raid coordination
✅ Track CWL daily

Kickpoints added automatically for violations.
Check #war-logs, #raid-logs, #clan-logs for updates.

Questions? Ask your co-leaders!
```

**Now fully automated!**

---

### Testing New Event Setup

**Scenario:** Want to test event before enabling automatic kickpoints.

**Steps:**

**1. Create test event with info-only**

```
/listeningevent add clan:TEST_CLAN type:Clan_War duration:0 actiontype:Info-Nachricht channel:#test-logs
```

**2. Wait for event to fire**

- Start a war
- Wait for war to end
- Check #test-logs

**3. Review output**

**Example output:**
```
⚠️ Missed War Attacks - TEST_CLAN

@Player1 - 1/2 attacks used
@Player2 - 0/2 attacks used
@Player3 - 1/2 attacks used

3 members missed attacks
```

**4. Verify accuracy**

- Check in-game
- Confirm these players actually missed
- Verify no false positives

**5. If correct, upgrade to kickpoints**

**Remove test event:**
```
/listeningevent list clan:TEST_CLAN
```

Get ID, then:
```
/listeningevent remove id:EVENT_ID
```

**Create real event:**
```
/listeningevent add clan:TEST_CLAN type:Clan_War duration:0 actiontype:Kickpoint channel:#war-logs kickpoint_reason:CW_Attack_vergessen
```

**6. Announce to clan**

```
⚠️ Automated War Tracking Now Active

From now on:
- Bot checks attacks after every war
- Missed attacks = 3 kickpoints automatically
- No excuses, no manual tracking

Make sure to use all attacks!
```

---

### Adjusting Penalties

**Scenario:** Kickpoint amounts too harsh/lenient. Need to adjust.

**Steps:**

**1. Review current penalties**

```
/kpinfo clan:YOUR_CLAN
```

**Current:**
```
CW Attack vergessen - 3 points
Raid unter 5 Angriffe - 2 points
Clangames unter 4000 - 2 points
```

**2. Discuss with leadership**

- Are 3 points for war too much?
- Should Clan Games be more?
- Getting feedback from clan

**3. Update reason amounts**

```
/kpeditreason clan:YOUR_CLAN reason:CW_Attack_vergessen amount:2
/kpeditreason clan:YOUR_CLAN reason:Clangames_unter_4000 amount:3
```

**4. Verify changes**

```
/kpinfo clan:YOUR_CLAN
```

**New:**
```
CW Attack vergessen - 2 points ✅
Raid unter 5 Angriffe - 2 points
Clangames unter 4000 - 3 points ✅
```

**5. Events auto-use new amounts**

- No need to recreate events
- Next violation uses new amount
- Old kickpoints stay at old amount

**6. Announce changes**

```
📊 Kickpoint Adjustments

New penalty amounts:
- War missed attack: 3 → 2 points
- Clan Games under 4000: 2 → 3 points

Changes effective immediately.
Reason: [Explain why changed]
```

**Note:** This only affects FUTURE kickpoints. Existing ones keep their original amounts.

---

## Additional Scenarios

Want more scenarios? Check these other guides:

- **Getting Started Guide** - First-time setup
- **Commands Guide** - Detailed command usage
- **Automated Events Guide** - Event deep dive
- **FAQ Guide** - Common problems and solutions

**Have a scenario not covered?** Ask your clan leaders or check the technical documentation.

---

**This guide is based on real clan management workflows. Adapt to your clan's specific needs!**

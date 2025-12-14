# Frequently Asked Questions & Troubleshooting

Quick answers to common questions about using Lost Manager 2.

---

## Table of Contents

### Account Linking
- [How do I link my account?](#how-do-i-link-my-account)
- [I can't find my API token](#i-cant-find-my-api-token)
- [API token says "invalid"](#api-token-says-invalid)
- [Can I link multiple accounts?](#can-i-link-multiple-accounts)
- [How do I unlink an account?](#how-do-i-unlink-an-account)
- [Can I link someone else's account?](#can-i-link-someone-elses-account)

### Kickpoints
- [What are kickpoints?](#what-are-kickpoints)
- [How do I check my kickpoints?](#how-do-i-check-my-kickpoints)
- [When do kickpoints expire?](#when-do-kickpoints-expire)
- [Can kickpoints be removed?](#can-kickpoints-be-removed)
- [What happens at max kickpoints?](#what-happens-at-max-kickpoints)
- [Why did I get kickpoints automatically?](#why-did-i-get-kickpoints-automatically)

### Discord Roles
- [Why don't I have the right Discord role?](#why-dont-i-have-the-right-discord-role)
- [How do Discord roles work?](#how-do-discord-roles-work)
- [Can I keep my custom nickname?](#can-i-keep-my-custom-nickname)
- [Bot won't change my nickname](#bot-wont-change-my-nickname)

### Automated Events
- [Why didn't I get notified?](#why-didnt-i-get-notified)
- [Event fired but I didn't violate rules](#event-fired-but-i-didnt-violate-rules)
- [How do I stop getting kickpoints automatically?](#how-do-i-stop-getting-kickpoints-automatically)
- [Can events be paused?](#can-events-be-paused)

### Clan Management
- [I'm not showing in the clan](#im-not-showing-in-the-clan)
- [My role is wrong](#my-role-is-wrong)
- [How do I add a new member?](#how-do-i-add-a-new-member)
- [Member left but still shows in bot](#member-left-but-still-shows-in-bot)

### Permissions
- [What can I do as a member?](#what-can-i-do-as-a-member)
- [What can I do as an elder?](#what-can-i-do-as-an-elder)
- [What can I do as co-leader?](#what-can-i-do-as-co-leader)
- [Command says I don't have permission](#command-says-i-dont-have-permission)

### Technical Issues
- [Bot not responding](#bot-not-responding)
- [Commands not appearing](#commands-not-appearing)
- [Bot showing as offline](#bot-showing-as-offline)
- [Information is outdated](#information-is-outdated)

---

## Account Linking

### How do I link my account?

Use the `/verify` command with your player tag and API token:

```
/verify tag:#YR8UVQQ8Q apitoken:abc123xyz
```

**Steps:**
1. Get your API token from Clash of Clans settings
2. Run the command with your tag and token
3. Bot verifies and links your account
4. You get the "Verified" role

**Need help?** See the [Getting Started Guide](USER_GUIDE_GETTING_STARTED.md#step-1-link-your-account) for detailed instructions.

---

### I can't find my API token

**Where to find it:**
1. Open Clash of Clans on your device
2. Tap Settings ⚙️ (gear icon)
3. Scroll down past all settings
4. Tap "Show my API token"
5. Copy the token that appears

**Important notes:**
- Token looks like: `abc123xyz` (random letters/numbers)
- Token changes EVERY time you view it
- Old tokens become invalid immediately (security feature to prevent sharing)
- Get a fresh token each time you use `/verify`

⚠️ **Can't see the option?** You need Town Hall level 8 or higher.

---

### API token says "invalid"

**Common reasons:**

1. **Token already used**
   - Tokens can only be used once
   - Get a new token and try again

2. **Token expired**
   - Tokens change when you view them in-game
   - Get a fresh token

3. **Copied wrong**
   - Make sure you copied the entire token
   - No spaces before or after
   - Case-sensitive

4. **Wrong player tag**
   - Make sure tag matches the account you got token from
   - Tag should start with #

**Solution:** Get a fresh API token from Clash of Clans and try again immediately.

---

### Can I link multiple accounts?

**Yes, but:**
- You can only self-link ONE account with `/verify`
- For additional accounts, ask a co-leader to use `/link`

**Example:**
```
Main account: You use /verify
Second account: Co-leader uses /link tag:#YOURTAG user:@You
Third account: Co-leader uses /link tag:#YOURTAG user:@You
```

**Check all your accounts:**
```
/playerinfo
```

This shows all accounts linked to your Discord.

---

### How do I unlink an account?

**You can't unlink yourself.** Ask a co-leader to use:

```
/unlink tag:#YOURTAG
```

This removes the Discord link but keeps the account in the database.

⚠️ **Note:** Unlinking removes your verified role if it was your only linked account.

---

### Can I link someone else's account?

**No, for security reasons.**

- Only the account owner can use `/verify` with their API token
- Co-leaders can use `/link`, but should verify identity first
- Never share your API token with others

---

## Kickpoints

### What are kickpoints?

Kickpoints are penalties given when you break clan rules. Think of them like "strikes" in baseball.

**Common reasons:**
- Missed war attacks
- Low Clan Games score (under 4000)
- Incomplete raid attacks
- Other clan rule violations

**How they work:**
- Each violation = certain number of kickpoints
- Kickpoints expire automatically (usually after 30 days)
- Reach max kickpoints = time to leave clan

---

### How do I check my kickpoints?

Use the `/kpmember` command:

```
/kpmember player:YourName
```

**You'll see:**
- Total active kickpoints
- Each violation listed separately
- When each expires
- Warning if close to limit

**Example output:**
```
MaxPower - Total: 5/10 kickpoints

#1234 - CW Attack vergessen - 3 points
   Given: 2024-12-01
   Expires: 2024-12-31

#1235 - Raid unter 5 Angriffe - 2 points
   Given: 2024-12-10
   Expires: 2025-01-09
```

---

### When do kickpoints expire?

**Default:** 30 days after they're given

**Example:**
- Given: December 1st
- Expires: December 31st

**Your clan's setting:** Ask your leader or check `/clanconfig`

**Automatic expiration:**
- Expired kickpoints don't count toward your total
- They're not deleted, just don't count anymore
- Check `/kpmember` to see expiration dates

💡 **Tip:** Stay clean for 30 days and your kickpoints will clear automatically!

---

### Can kickpoints be removed?

**Yes, co-leaders can remove them:**

First, get the kickpoint ID:
```
/kpmember player:YourName
```

Output shows ID numbers like `#1234`

Co-leader removes it:
```
/kpremove id:1234
```

**When are they removed:**
- Kickpoint was added by mistake
- You made a valid appeal
- Special circumstances

💡 **Note:** Most clans let kickpoints expire naturally rather than removing them.

---

### What happens at max kickpoints?

**It depends on your clan's rules.**

Most clans:
- Max is 10 kickpoints
- Reaching 10 = required to leave or face kick

**Bot doesn't auto-kick you.** Leaders decide what to do.

**Check your limit:**
```
/playerinfo
```

Shows your kickpoints as: `5/10` (5 current, 10 max)

---

### Why did I get kickpoints automatically?

Your clan has automated events set up.

**Common automatic kickpoints:**
- Clan Games under 4000 points
- Missed war attacks
- Incomplete raid attacks (under 5-6)
- Missed CWL attacks

**How to avoid:**
- Complete all war/raid attacks
- Score 4000+ in Clan Games
- Follow all clan rules

**Check what happened:**
- Look in your clan's event channels (#war-logs, #raid-logs, etc.)
- Messages explain why kickpoints were added

**Not fair?** Talk to your co-leaders about the automated event settings.

---

## Discord Roles

### Why don't I have the right Discord role?

**Common reasons:**

1. **Account not linked**
   - Use `/verify` to link your account
   - Check `/playerinfo` to confirm link

2. **Promoted/demoted in game**
   - Bot doesn't auto-update roles
   - Co-leader needs to run `/checkroles` and fix

3. **Role not configured**
   - Your clan might not have Discord roles set up
   - Ask leader to use `/clanconfig`

4. **Bot permission issue**
   - Bot's role must be higher than clan roles
   - Ask admin to check Discord role hierarchy

**To fix:**
1. Tell a co-leader
2. They run: `/checkroles clan:YourClan`
3. They manually assign correct role
4. Or they update database with `/editmember`

---

### How do Discord roles work?

**The bot manages clan rank roles:**

| In-Game Rank | Discord Role |
|-------------|-------------|
| Leader | Leader role |
| Co-Leader | Co-Leader role |
| Elder | Elder role |
| Member | Member role |

**Plus:**
- Verified role when you link first account
- Ex-member role when removed from clan

**Automatic vs. Manual:**
- Bot doesn't auto-update roles when you're promoted
- Co-leaders run `/checkroles` to find issues
- Then manually fix roles or use `/editmember`

---

### Can I keep my custom nickname?

**Partially.**

When you link your account:
- Bot sets your nickname to your in-game name
- You can add an alias with `/setnick`

**Example:**
```
/setnick my_player:#YOURTAG alias:Red Team
```

**Result:** Nickname becomes "PlayerName | Red Team"

**Can I use something else entirely?**
- Not recommended, causes confusion
- Bot might override it
- Talk to clan leaders if you have special circumstances

---

### Bot won't change my nickname

**Common causes:**

1. **Permission issue**
   - Bot needs "Manage Nicknames" permission
   - Ask server admin to check

2. **Role hierarchy**
   - Bot's role must be higher than yours
   - Ask admin to move bot role up

3. **Server owner**
   - Bots can't change owner's nickname (Discord limitation)
   - Owner must change manually

4. **Bot offline**
   - Check if bot shows online
   - Wait for it to come back

**Manual workaround:**
- Change your nickname manually to match in-game name
- Use `/playerinfo` to see what it should be

---

## Automated Events

### Why didn't I get notified?

**Check these:**

1. **Event exists?**
   Ask co-leader to run:
   ```
   /listeningevent list clan:YourClan
   ```

2. **Right channel?**
   - Events send to specific channels
   - Check #war-logs, #raid-logs, #clan-logs, etc.
   - You might have notifications muted

3. **You weren't violated?**
   - If you completed all attacks, you won't be mentioned
   - Only violators get mentioned

4. **Event didn't fire yet?**
   - Check "Next fire" time in event list
   - Wars end early sometimes (3-star victory)

5. **Bot was offline?**
   - If bot was down when event should fire, it might be skipped
   - Bot doesn't catch up on very old events

---

### Event fired but I didn't violate rules

**Possible reasons:**

1. **API delay**
   - Clash API can take time to update
   - Your attacks might not show immediately
   - Usually resolves within an hour

2. **Different account**
   - Make sure bot is checking the right account
   - If you have multiple accounts, verify which one

3. **Event timing**
   - Clan Games: Bot checks points GAINED during event
   - Wars: Attack must complete before war end
   - Raids: Must use 5+ attacks (or 6 if available)

4. **Event misconfigured**
   - Wrong threshold
   - Wrong action type
   - Ask co-leader to check event setup

**What to do:**
1. Show your game screenshot proving completion
2. Ask co-leader to remove kickpoint: `/kpremove id:NUMBER`
3. If recurring, ask to review event configuration

---

### How do I stop getting kickpoints automatically?

**You can't stop it yourself.** Your clan leaders control automated events.

**To avoid automatic kickpoints:**
1. Complete all attacks in wars/raids
2. Score 4000+ in Clan Games
3. Follow all clan rules

**If events are too strict:**
- Talk to your leaders
- They can change action from "Kickpoint" to "Info-Nachricht"
- Or remove events entirely

**Leaders can check:**
```
/listeningevent list clan:YourClan
```

And remove/modify events as needed.

---

### Can events be paused?

**Not directly**, but leaders can:

1. **Remove events temporarily:**
   ```
   /listeningevent remove id:EVENT_ID
   ```
   Then recreate later

2. **Change to info-only:**
   - Remove event
   - Recreate with "Info-Nachricht" instead of "Kickpoint"
   - Notifications continue but no penalties

**Use cases:**
- Clan on vacation/break
- Testing new rules
- Special circumstances

---

## Clan Management

### I'm not showing in the clan

**Likely reasons:**

1. **Not added to database**
   - You're in clan in-game
   - But not added to bot yet
   - Co-leader needs to use `/addmember`

2. **Recently joined**
   - Bot doesn't auto-detect new members
   - Someone must add you manually

**To fix:**
Ask co-leader to run:
```
/addmember clan:ClanName player:#YOURTAG role:YourRole
```

**Check if you're in bot:**
```
/listmembers clan:ClanName
```

Look for your name in the list.

---

### My role is wrong

**In bot but wrong role?**

Ask co-leader to fix:
```
/editmember player:YourName role:CorrectRole
```

**Example:**
You got promoted to Elder in-game:
```
/editmember player:MaxPower role:Elder
```

**Or co-leader can check sync:**
```
/memberstatus clan:YourClan
```

This shows all role mismatches.

---

### How do I add a new member?

**Permission needed:** Co-Leader or higher

**Steps:**
1. Member joins clan in Clash of Clans
2. Run the command:
   ```
   /addmember clan:ClanName player:#PLAYERTAG role:Member
   ```

3. If they have Discord, they can now link:
   ```
   /verify tag:#THEIRTAG apitoken:THEIRTOKEN
   ```

**Example:**
```
/addmember clan:LOST_F2P player:#ABC123 role:Member
```

---

### Member left but still shows in bot

**This is normal.** When someone leaves:
1. They're still in bot database
2. Just not assigned to a clan anymore
3. Keeps their history (kickpoints, wins, etc.)

**To mark them as removed:**
```
/removemember player:TheirName
```

**What this does:**
- Removes clan assignment
- Gives them "ex-member" role (if configured)
- Keeps their data for records

**Check clan status:**
```
/memberstatus clan:YourClan
```

Shows people in database but not in game.

---

## Permissions

### What can I do as a member?

**As a regular member, you can:**
- `/verify` - Link your account
- `/playerinfo` - Check your information
- `/kpmember` - View your kickpoints
- `/setnick` - Change your nickname
- `/wins` - Check season wins
- `/listmembers` - See clan roster

**You cannot:**
- Add/remove members
- Add/remove kickpoints
- Change clan settings
- Manage automated events

---

### What can I do as an elder?

**Elders have same permissions as members:**
- View commands only
- Can't modify anything
- No special bot permissions

**In-game elder ≠ Bot permissions**

Bot permissions are based on:
- Co-Leader or higher = full access
- Below Co-Leader = view only

---

### What can I do as co-leader?

**Co-Leaders can do almost everything:**

**Member Management:**
- Add/remove/transfer members
- Change member roles
- Check sync status

**Kickpoints:**
- Add/remove/edit kickpoints
- Create/edit/remove reasons
- View all kickpoints

**Events:**
- Create automated events
- Remove events
- View all events

**Utilities:**
- Check roles
- Ping for raids
- Select war donors

**You cannot:**
- Change clan configuration (Leader only)
- Restart bot (Admin only)

---

### Command says I don't have permission

**Check:**

1. **Your clan role:**
   ```
   /playerinfo
   ```
   Shows your role in each clan

2. **Command requirements:**
   - Most commands need Co-Leader+
   - `/clanconfig` needs Leader
   - Some need Discord permissions

3. **Right clan:**
   - You might be Co-Leader of Clan A
   - But trying to manage Clan B
   - Permissions are per-clan

4. **Account linked:**
   - Must have linked account for clan permissions
   - Use `/verify` if not linked

**Still issues?** Ask your clan leader to:
1. Check your role: `/playerinfo user:@You`
2. Update if wrong: `/editmember player:You role:CorrectRole`

---

## Technical Issues

### Bot not responding

**Quick checks:**

1. **Bot online?**
   - Check member list
   - Should show green "online" status

2. **Bot can read channel?**
   - Check channel permissions
   - Bot needs "Read Messages" permission

3. **Using slash commands?**
   - Type `/` and bot commands should appear
   - Don't use text commands (like `!command`)

4. **Discord lag?**
   - Sometimes Discord is slow
   - Wait a minute and try again

**If bot is offline:**
- Wait for server admin to restart
- Check announcements for downtime notices

---

### Commands not appearing

**When you type `/`, bot commands don't show?**

1. **Bot has slash command permission?**
   - Admin needs to check bot permissions
   - "Use Application Commands" must be enabled

2. **Bot recently added/updated?**
   - Discord can take up to 1 hour to sync commands
   - Try in a different channel
   - Try restarting Discord app

3. **Channel specific issue?**
   - Try in a different channel
   - Some channels might block commands

**Workaround:**
- Type the full command manually
- Example: `/playerinfo` then press Enter

---

### Bot showing as offline

**Possible reasons:**

1. **Server maintenance**
   - Bot server might be restarting
   - Usually back in 1-5 minutes

2. **Bot crashed**
   - Admin needs to restart
   - Check with server admins

3. **Hosting issue**
   - Server hosting bot might be down
   - Wait for admin to fix

**What you can do:**
- Nothing, wait for bot to come back online
- Check announcements for updates
- Report to admins if offline for >10 minutes

---

### Information is outdated

**Bot shows old information?**

1. **Name outdated:**
   - Bot updates names every 2 hours
   - Wait for next update
   - Or ask admin to restart bot

2. **Clan members outdated:**
   - Bot doesn't auto-sync
   - Co-leader needs to use `/memberstatus`
   - Then manually fix with `/addmember` or `/removemember`

3. **War/raid data outdated:**
   - Clash API can be slow
   - Wait 5-10 minutes after event ends
   - Then data should be correct

4. **Role outdated:**
   - Bot doesn't auto-update roles
   - Co-leader uses `/checkroles` to find issues
   - Manually fix roles or use `/editmember`

**Force refresh:** Ask co-leader to run relevant check command.

---

## Still Need Help?

### For Account Issues
- Talk to your clan co-leaders
- They can use `/link`, `/relink`, or `/unlink`
- Provide your player tag

### For Kickpoint Questions
- Ask co-leaders to explain clan rules
- Request details on why kickpoints were given
- Check event messages in clan channels

### For Technical Problems
- Report to server admins
- Provide details: what command, what error, screenshots
- Check if others have same issue

### For Feature Requests
- Talk to clan leaders
- They can set up events or adjust settings
- Or contact bot developer

---

## Quick Troubleshooting Checklist

**Bot not responding?**
- ✅ Bot online?
- ✅ Channel permissions?
- ✅ Using `/` commands?

**Can't link account?**
- ✅ Fresh API token?
- ✅ Correct player tag?
- ✅ TH8 or higher?

**Wrong kickpoints?**
- ✅ Check event timing
- ✅ Check actual performance
- ✅ Talk to co-leaders

**Permission error?**
- ✅ Account linked?
- ✅ Co-Leader or higher?
- ✅ Right clan?

**Wrong role?**
- ✅ Account linked?
- ✅ Co-leader ran `/checkroles`?
- ✅ Roles manually updated?

---

**This FAQ is updated regularly. Bookmark for quick reference!**

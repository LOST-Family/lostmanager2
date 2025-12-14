# Getting Started with Lost Manager 2

Welcome! This guide will help you start using Lost Manager 2 to manage your Clash of Clans clan on Discord.

---

## What is Lost Manager 2?

Lost Manager 2 is a Discord bot that helps manage your Clash of Clans clan by:
- Linking Discord accounts to Clash of Clans players
- Tracking penalties (kickpoints) for rule violations
- Automatically monitoring clan events (Wars, Raids, Clan Games, CWL)
- Keeping Discord roles synced with in-game clan ranks

---

## First Steps

### Step 1: Link Your Account

To start using the bot, you need to link your Discord account to your Clash of Clans player tag.

**Self-Service Linking:**

`/verify tag:YOUR_TAG apitoken:YOUR_TOKEN`

**How to get your API token:**
1. Open Clash of Clans on your device
2. Go to Settings ⚙️
3. Scroll to "Show my API token"
4. Copy the token (it looks like: `abcd123`)
5. Use it in the `/verify` command

**What happens:**
1. The bot checks if your token is valid
2. Your Discord account gets linked to your player tag
3. You get the "Verified" role automatically
4. Your Discord nickname changes to match your in-game name

**Example:**
```
/verify tag:#YR8UVQQ8Q apitoken:abcd123
```

✅ **Success:** "Erfolgreich! Du bist jetzt als [PlayerName] verifiziert!"

💡 **Tip:** The API token changes every time you view it in-game, so you can only use it once.

---

### Step 2: Check Your Info

After linking, you can check your information:

`/playerinfo`

**What you'll see:**
- Your player name and tag
- Your current clan and role
- Your kickpoints (if any)
- Your season wins statistics

**Example:**
```
/playerinfo
```

**Output:**
```
Player: MaxPower (#YR8UVQQ8Q)
Discord: @MaxPower#1234
Clan: LOST F2P (Leader)
Kickpoints: 0
Season Wins: 125 → 156 (+31 this season)
```

---

### Step 3: Understand Kickpoints

Kickpoints are penalties given when you break clan rules. Common reasons:
- Missed war attacks
- Low Clan Games score
- Missing raid attacks

**View your kickpoints:**

`/kpmember player:YOUR_NAME`

**What you'll see:**
- Total active kickpoints
- Each kickpoint with reason, amount, date, and expiration
- Warning if you're over the limit

**Example:**
```
/kpmember player:MaxPower
```

**Output:**
```
MaxPower - Total Kickpoints: 3

#1234 - CW Attack vergessen - 3 points
   Given: 2024-12-10
   Expires: 2025-01-09

Total: 3/10 kickpoints
```

💡 **Tip:** Kickpoints expire automatically after 30 days (default setting).

---

## Common Tasks

### Set Your Nickname

Change your Discord nickname to match your in-game name:

`/setnick my_player:YOUR_TAG`

**Add an alias (optional):**

`/setnick my_player:YOUR_TAG alias:Red`

**Result:** Your nickname becomes "MaxPower | Red"

**Example:**
```
/setnick my_player:#YR8UVQQ8Q alias:Red
```

✅ **Result:** Nickname changed to "MaxPower | Red"

---

### Check Who Needs to Raid

During Raid Weekend, you can see who hasn't finished their attacks:

`/raidping clan:YOUR_CLAN`

**What you'll see:**
- List of members who haven't used all 5-6 attacks
- Discord mentions for linked players
- Number of attacks used vs. total available

**Example:**
```
/raidping clan:LOST_F2P
```

**Output:**
```
⚠️ Incomplete Raids:

@MaxPower - 3/6 attacks used
@Player2 - 0/6 attacks used
PlayerNotLinked (#ABC123) - 2/5 attacks used

Total: 3 members need to raid
```

---

### Check Your Season Wins

See how many wins you gained this season:

`/wins season:2024-12 player:YOUR_NAME`

**What you'll see:**
- Wins at season start
- Current wins
- Total wins gained this season

**Example:**
```
/wins season:2024-12 player:MaxPower
```

**Output:**
```
MaxPower (#YR8UVQQ8Q)
Season: December 2024

Start: 125 wins
Current: 156 wins
Gained: +31 wins
```

💡 **Tip:** You can also check entire clan wins with `clan:CLAN_NAME` instead of `player:`.

---

## Understanding Automated Events

The bot can automatically check clan events and notify you or add kickpoints.

### What Events Can Be Tracked?

1. **Clan Games (cs)** - Checks if you scored enough points (usually 4000)
2. **Clan War (cw)** - Checks if you used all war attacks
3. **CWL Day (cwlday)** - Checks daily CWL attacks
4. **Raid Weekend (raid)** - Checks raid attacks

### How Do Events Work?

Your clan leaders set up events that automatically:
- Check player performance when the event ends
- Send a message to a Discord channel
- Optionally add kickpoints to violators

**Example Event:**
```
Type: Clan War
Timing: At war end (0 hours before)
Action: Add 3 kickpoints for missed attacks
Channel: #war-logs
```

**What happens:**
1. War ends at 5:00 PM
2. Bot waits 5 minutes for API to update
3. Bot checks who missed attacks
4. Bot sends message: "⚠️ These players missed attacks: @Player1, @Player2"
5. Bot adds 3 kickpoints to each violator

💡 **Tip:** You'll be mentioned in Discord when an event affects you.

---

## Role Permissions

Different clan roles have different permissions:

### Leader
- All commands available
- Configure clan settings (`/clanconfig`)
- Change kickpoint expiration times

### Co-Leader
- Most commands available
- Add/remove members
- Add/remove kickpoints
- Check member status

### Elder
- View information commands
- Cannot modify members or kickpoints

### Member
- Link your own account
- View your own information
- Set your nickname

---

## Troubleshooting

### "You don't have permission"

This means you need a higher clan role. Ask your leader or co-leader for help.

### "Player not found"

The player hasn't been added to the database yet. A co-leader needs to use `/addmember` first.

### "Clan not found"

The clan hasn't been set up in the bot yet. Contact your clan leader.

### Kickpoints Not Showing

Kickpoints might have expired. They automatically expire after 30 days (default).

### Nickname Won't Change

The bot needs "Manage Nicknames" permission, and its role must be higher than yours in the server.

---

## Getting Help

### Check Your Permissions

Not sure what you can do?

`/playerinfo`

**What you'll see:** Your clan role and permissions

### Ask Your Leader

If something's not working, your clan leader can:
- Check bot settings with `/clanconfig`
- Verify your account is linked properly
- Fix role synchronization with `/checkroles`

---

## Quick Reference Card

### For Members:
- `/verify` - Link your account
- `/playerinfo` - Check your info
- `/kpmember` - View your kickpoints
- `/setnick` - Change your nickname
- `/wins` - Check your wins

### For Co-Leaders:
- `/addmember` - Add player to clan
- `/removemember` - Remove player from clan
- `/kpadd` - Add kickpoints
- `/kpmember` - View member kickpoints
- `/raidping` - Check raid progress

### For Leaders:
- `/clanconfig` - Configure clan settings
- `/listeningevent add` - Create automated events
- `/kpaddreason` - Create kickpoint reasons

---

## Tips for Success

💡 **Link all your accounts:** If you have multiple Clash accounts, ask a co-leader to use `/link` to connect them all to your Discord.

💡 **Check kickpoints regularly:** Use `/kpmember` to see your current penalties and when they expire.

💡 **Keep your nickname updated:** Use `/setnick` if you change your in-game name.

💡 **Monitor event messages:** Pay attention to automated event notifications in your clan channels.

⚠️ **Don't share your API token:** Each token can only be used once. Get a fresh one each time you need to verify.

✅ **Stay active:** Complete all your attacks in wars, raids, and Clan Games to avoid kickpoints!

---

**Need more help?** Ask your clan leaders or check the other documentation files for detailed command information.

# Automated Events - Complete User Guide

This guide explains how automated events work and how to set them up for your clan.

---

## What Are Automated Events?

Automated events let the bot automatically monitor your clan activities and take action when events end. Instead of manually checking who missed attacks or scored low in Clan Games, the bot does it for you.

**What the bot can monitor:**
- Clan Games (monthly)
- Clan Wars
- CWL (Clan War League) days
- Raid Weekends

**What the bot can do:**
- Send informational messages
- Add kickpoints to rule violators
- Select random war donors
- Track war fillers (opt-outs)
- Analyze raid district attacks

---

## How Automated Events Work

### The Basic Flow

1. **You create an event** using `/listeningevent add`
2. **Bot monitors the game** by checking the Clash of Clans API
3. **When the event ends**, bot automatically:
   - Checks player performance
   - Sends a message to your Discord channel
   - Takes action (like adding kickpoints)

### Example Scenario

You set up: "Check Clan Games, add 2 kickpoints to anyone under 4000 points"

**What happens:**
- Clan Games start on the 22nd at 7 AM
- Bot saves everyone's starting score
- Clan Games end on the 28th at 12 PM
- At 1 PM (1 hour buffer), bot checks final scores
- Bot compares: final score - starting score
- Bot sends message: "These players scored under 4000: @Player1, @Player2"
- Bot adds 2 kickpoints to each violator

---

## Event Types Explained

### Clan Games Events

**What it monitors:** Player points scored during Clan Games

**Timing:**
- Start: 22nd of month at 7:00 AM (automatic)
- End: 28th of month at 12:00 PM (automatic)
- Fire time: 28th at 1:00 PM (1 hour buffer for API)

**Common setup:**
- Duration: `0` (fires at end)
- Action: Add kickpoints
- Threshold: 4000 points (hardcoded)

**Example:**
```
/listeningevent add clan:LOST_F2P type:Clan_Games duration:0 actiontype:Kickpoint channel:#clan-logs kickpoint_reason:Clangames_unter_4000
```

**What you'll see on Dec 28 at 1 PM:**
```
⚠️ Clan Games Results - LOST F2P
Threshold: 4000 points

Below Threshold:
- @MaxPower: 3,250 points (750 short) - 2 kickpoints added
- @PlayerTwo: 2,100 points (1,900 short) - 2 kickpoints added
- @PlayerThree: 0 points (4,000 short) - 2 kickpoints added

3 members received kickpoints
```

💡 **Tip:** The bot looks at points GAINED during Clan Games, not lifetime points. If someone had 5000 points before and gained 3000 during, they'll be flagged.

---

### Clan War Events

Clan Wars have two different monitoring modes:

#### Mode 1: Filler Check (War Start)

**What it monitors:** Who opted OUT of war

**Timing:** When war preparation phase starts

**How to set up:**
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:-1 actiontype:Filler channel:#war-prep
```

**Duration:** `-1` (special value meaning "at war start")

**What you'll see when war starts:**
```
📋 War Fillers - LOST F2P
Preparation Phase

Opted Out of War (5 members):
- MaxPower (#YR8UVQQ8Q)
- PlayerTwo (#ABC123)
- PlayerThree (#DEF456)
- PlayerFour (#GHI789)
- PlayerFive (#JKL012)

War Size: 30v30
In War: 30 members
Opted Out: 5 members
```

💡 **Use this to:** See who's not participating before you finalize the war roster.

#### Mode 2: Missed Attacks (War End)

**What it monitors:** Who didn't use all their attacks

**Timing:** When war ends (battle day finishes)

**How to set up:**

**Info message only:**
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:0 actiontype:Info-Nachricht channel:#war-logs
```

**Add kickpoints:**
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:0 actiontype:Kickpoint channel:#war-logs kickpoint_reason:CW_Attack_vergessen
```

**Duration:** `0` = at war end

**What you'll see when war ends:**
```
⚠️ Missed War Attacks - LOST F2P
War ended at 5:00 PM

Missed Attacks:
- @MaxPower: 1/2 attacks used (1 missed) - 3 kickpoints added
- @PlayerTwo: 0/2 attacks used (2 missed) - 3 kickpoints added

2 members missed attacks
Stars earned: 87/90 possible
```

💡 **Tip:** Set duration to `3600000` (1 hour before end) to send a reminder message before war ends.

#### Mode 3: Reminder Before End

**What it monitors:** Same as Mode 2, but earlier

**Timing:** X hours before war ends

**How to set up (1 hour before):**
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:3600000 actiontype:Info-Nachricht channel:#war-logs
```

**Duration:** `3600000` milliseconds = 1 hour

**What you'll see 1 hour before war ends:**
```
⏰ War Reminder - LOST F2P
War ends in 1 hour!

Still Need to Attack:
- @MaxPower: 1/2 attacks used
- @PlayerTwo: 0/2 attacks used
- @PlayerThree: 1/2 attacks used

3 members still need to attack!
```

💡 **Use this to:** Give people one last chance to attack before war ends.

**Duration Conversion Helper:**
- 1 hour = 3600000 (3600 seconds × 1000)
- 2 hours = 7200000
- 6 hours = 21600000
- 12 hours = 43200000
- 24 hours = 86400000

---

### CWL Day Events

**What it monitors:** Daily CWL attack completion

**Timing:** Each CWL war day ends individually

**How to set up:**
```
/listeningevent add clan:LOST_F2P type:CWL_Tag duration:0 actiontype:Kickpoint channel:#cwl-logs kickpoint_reason:CWL_Attack_vergessen
```

**What you'll see after each CWL day:**
```
⚠️ CWL Day Results - LOST F2P
Day 3 of 7

Missed Attacks:
- @MaxPower: 0/1 attacks used - 3 kickpoints added
- @PlayerTwo: 0/1 attacks used - 3 kickpoints added

2 members missed attacks today
```

💡 **Important:** This fires for EACH day of CWL, not just once. If CWL has 7 days, you'll get 7 messages.

💡 **Tip:** CWL roster can include members from side clans. The bot checks everyone in the CWL roster, not just main clan members.

---

### Raid Weekend Events

Raids have two modes:

#### Mode 1: Simple Attack Check

**What it monitors:** Who didn't use all 5-6 attacks

**Timing:** When raid weekend ends (Monday morning)

**How to set up:**
```
/listeningevent add clan:LOST_F2P type:Raid duration:0 actiontype:Kickpoint channel:#raid-logs kickpoint_reason:Raid_unter_5_Angriffe
```

**What you'll see Monday morning:**
```
⚠️ Incomplete Raids - LOST F2P
Raid Weekend ended at 7:00 AM

Below 5 Attacks:
- @MaxPower: 3/6 attacks used (2 short) - 2 kickpoints added
- @PlayerTwo: 0/6 attacks used (5 short) - 2 kickpoints added
- @PlayerThree: 4/5 attacks used (1 short) - 2 kickpoints added

3 members received kickpoints
```

#### Mode 2: Raid Fails Analysis

**What it monitors:** Bad raid attacks (over-hitting districts)

**Timing:** When raid weekend ends

**How to set up (info only):**
```
/listeningevent add clan:LOST_F2P type:Raid duration:0 actiontype:Raidfails channel:#raid-logs
```

**How to set up (with kickpoints):**
```
/listeningevent add clan:LOST_F2P type:Raid duration:0 actiontype:Raidfails channel:#raid-logs kickpoint_reason:Raid_Fails
```

**What it does:**
1. Analyzes each district (Wizard Valley, Barbarian Camp, etc.)
2. Identifies over-attacked districts (districts that got too many attacks)
3. Lists all attackers or assigns kickpoints to worst offenders

**Example output (info only):**
```
⚠️ Raid District Analysis - LOST F2P

Over-attacked Districts:

Wizard Valley (Barbarian):
  Needed: 4 attacks
  Received: 7 attacks (+3 extra)
  
  Attackers:
  - @MaxPower (2 attacks)
  - @PlayerTwo (2 attacks)
  - @PlayerThree (1 attack)
  - @PlayerFour (1 attack)
  - @PlayerFive (1 attack)

Balloon Lagoon (Builder):
  Needed: 3 attacks
  Received: 6 attacks (+3 extra)
  
  Attackers: [list continues...]

Total wasted attacks: 6
```

**Example output (with kickpoints):**
```
⚠️ Raid Fails - LOST F2P

Wizard Valley over-attacked:
- @MaxPower: 2 attacks (worst offender) - 2 kickpoints added
- @PlayerTwo: 2 attacks (tied) - 2 kickpoints added

Balloon Lagoon over-attacked:
- @PlayerThree: 2 attacks (worst offender) - 2 kickpoints added

3 members received kickpoints for bad hits
```

💡 **How "worst offender" works:**
- If multiple people hit a district, whoever hit it THE MOST gets kickpoints
- If tied, ALL tied players get kickpoints
- This encourages coordination and checking before attacking

---

## Action Types Explained

### Info Message

**What it does:** Sends a message listing violations, no penalties

**When to use:**
- First warnings
- Reminders before events end
- Testing event setup
- Clans with no kickpoint system

**Example:**
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:3600000 actiontype:Info-Nachricht channel:#war-logs
```

**Result:** Message only, no kickpoints

---

### Kickpoint

**What it does:** Sends message AND adds kickpoints automatically

**When to use:**
- Enforcing clan rules
- Automatic penalties for violations
- When you trust the bot's detection

**Requirements:**
- Must specify `kickpoint_reason` parameter
- Reason must exist in your clan (create with `/kpaddreason`)

**Example:**
```
/listeningevent add clan:LOST_F2P type:Raid duration:0 actiontype:Kickpoint channel:#raid-logs kickpoint_reason:Raid_unter_5_Angriffe
```

**Result:** Message + automatic kickpoints

⚠️ **Important:** Bot adds kickpoints immediately and automatically. Make sure your reason amounts are correct!

---

### CW Donator (War Only)

**What it does:** Randomly selects war donors

**When to use:**
- At war start to assign donor duty
- Fair rotation of donor responsibilities

**How it works:**
1. Bot checks war size (5v5, 10v10, etc.)
2. Bot determines donor count needed
3. Bot randomly selects from war participants
4. Bot sends message mentioning selected players

**Example:**
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:-1 actiontype:CW_Donator channel:#war-prep
```

**What you'll see at war start:**
```
🎯 War Donors Selected - LOST F2P
War Size: 30v30

Selected Donors (6 needed):
1. @MaxPower
2. @PlayerTwo
3. @PlayerThree
4. @PlayerFour
5. @PlayerFive
6. @PlayerSix

Please donate clan castle troops!
```

💡 **Tip:** You can also use the `/cwdonator` command manually instead of automating it.

---

### Filler (War Only)

**What it does:** Lists members who opted OUT of war

**When to use:**
- At war start (preparation phase)
- To see who's not participating

**Example:**
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:-1 actiontype:Filler channel:#war-prep
```

**Result:** List of opted-out members

---

### Raid Fails (Raid Only)

**What it does:** Analyzes district over-attacking

**When to use:**
- Teaching raid coordination
- Penalizing careless attacks
- Reviewing raid strategy

**Modes:**
- Without kickpoint_reason: Info message with all attackers
- With kickpoint_reason: Kickpoints to worst offenders only

**Example (info):**
```
/listeningevent add clan:LOST_F2P type:Raid duration:0 actiontype:Raidfails channel:#raid-logs
```

**Example (kickpoints):**
```
/listeningevent add clan:LOST_F2P type:Raid duration:0 actiontype:Raidfails channel:#raid-logs kickpoint_reason:Raid_Fails
```

---

## Setting Up Events - Step by Step

### Step 1: Create Kickpoint Reasons

Before setting up events with kickpoints, create your reason templates:

```
/kpaddreason clan:LOST_F2P reason:CW_Attack_vergessen amount:3
/kpaddreason clan:LOST_F2P reason:Clangames_unter_4000 amount:2
/kpaddreason clan:LOST_F2P reason:Raid_unter_5_Angriffe amount:2
/kpaddreason clan:LOST_F2P reason:CWL_Attack_vergessen amount:3
/kpaddreason clan:LOST_F2P reason:Raid_Fails amount:2
```

Check your reasons:
```
/kpinfo clan:LOST_F2P
```

---

### Step 2: Choose Your Channel

Decide where messages should go:
- `#clan-logs` - General clan violations
- `#war-logs` - War-related events
- `#raid-logs` - Raid events
- `#cwl-logs` - CWL events

Make sure the bot can send messages in this channel!

---

### Step 3: Create the Event

Use `/listeningevent add` with your chosen settings.

**Example: Basic Clan Games setup**
```
/listeningevent add clan:LOST_F2P type:Clan_Games duration:0 actiontype:Kickpoint channel:#clan-logs kickpoint_reason:Clangames_unter_4000
```

---

### Step 4: Verify It's Created

List your events:
```
/listeningevent list clan:LOST_F2P
```

You should see:
```
#123 - Clan Games
  Action: Add kickpoints (Clangames unter 4000)
  Channel: #clan-logs
  Next fire: Jan 28 at 1:00 PM
```

---

### Step 5: Wait for Event to Fire

The bot automatically monitors and fires at the right time. You don't need to do anything!

When the event fires, you'll see a message in your chosen channel.

---

## Common Event Setups

### Setup 1: Complete Automation

**Goal:** Automatically penalize all violations

**Events to create:**

1. Clan Games violations:
```
/listeningevent add clan:LOST_F2P type:Clan_Games duration:0 actiontype:Kickpoint channel:#clan-logs kickpoint_reason:Clangames_unter_4000
```

2. War missed attacks:
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:0 actiontype:Kickpoint channel:#war-logs kickpoint_reason:CW_Attack_vergessen
```

3. Raid incomplete:
```
/listeningevent add clan:LOST_F2P type:Raid duration:0 actiontype:Kickpoint channel:#raid-logs kickpoint_reason:Raid_unter_5_Angriffe
```

4. CWL missed attacks:
```
/listeningevent add clan:LOST_F2P type:CWL_Tag duration:0 actiontype:Kickpoint channel:#cwl-logs kickpoint_reason:CWL_Attack_vergessen
```

**Result:** Everything automated, kickpoints added automatically.

---

### Setup 2: Reminders + Manual Penalties

**Goal:** Get notified but add kickpoints manually

**Events to create:**

1. War reminder (1 hour before):
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:3600000 actiontype:Info-Nachricht channel:#war-logs
```

2. War results (no penalties):
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:0 actiontype:Info-Nachricht channel:#war-logs
```

3. Raid results (no penalties):
```
/listeningevent add clan:LOST_F2P type:Raid duration:0 actiontype:Info-Nachricht channel:#raid-logs
```

**Result:** You get notified, then use `/kpadd` manually if needed.

---

### Setup 3: War Management Pack

**Goal:** Full war automation from start to end

**Events to create:**

1. War start - show fillers:
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:-1 actiontype:Filler channel:#war-prep
```

2. War start - select donors:
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:-1 actiontype:CW_Donator channel:#war-prep
```

3. War reminder (1 hour before):
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:3600000 actiontype:Info-Nachricht channel:#war-logs
```

4. War end - kickpoints:
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:0 actiontype:Kickpoint channel:#war-logs kickpoint_reason:CW_Attack_vergessen
```

**Result:** Complete war management from start to finish.

---

### Setup 4: Raid Strategy Focus

**Goal:** Improve raid coordination

**Events to create:**

1. Raid fails analysis (info):
```
/listeningevent add clan:LOST_F2P type:Raid duration:0 actiontype:Raidfails channel:#raid-logs
```

2. Raid incomplete attacks:
```
/listeningevent add clan:LOST_F2P type:Raid duration:0 actiontype:Kickpoint channel:#raid-logs kickpoint_reason:Raid_unter_5_Angriffe
```

**Result:** Track both coordination and participation.

---

## Managing Your Events

### View All Events

```
/listeningevent list
```

or filter by clan:

```
/listeningevent list clan:LOST_F2P
```

---

### Remove an Event

First, get the event ID:
```
/listeningevent list clan:LOST_F2P
```

Output shows:
```
#123 - Clan Games
#124 - Clan War
#125 - Raid
```

Remove one:
```
/listeningevent remove id:124
```

---

### Temporarily Disable Events

**Option 1:** Remove and recreate later
**Option 2:** Change to Info-Nachricht (remove kickpoint action)

Unfortunately, there's no "pause" feature currently.

---

## Troubleshooting

### Event Didn't Fire

**Check these:**

1. **Is the event created?**
   ```
   /listeningevent list clan:YOUR_CLAN
   ```

2. **Did the game event actually happen?**
   - Wars must be active
   - Raids must be active
   - Clan Games must be in session

3. **Is bot online?**
   - Check if bot shows as online in Discord

4. **Right timing?**
   - Duration set correctly?
   - War might have ended early (3-star victory)

5. **Bot can send messages in channel?**
   - Bot needs permission in target channel

---

### Wrong Players Flagged

**Clan Games:**
- Bot checks points GAINED, not total points
- Make sure members were in clan when Clan Games started

**Wars:**
- Bot checks current war attacks
- If someone left clan, they won't be checked

**Raids:**
- Bot checks actual attacks used from API
- Sometimes API takes time to update

---

### Kickpoints Not Added

**Check these:**

1. **Does reason exist?**
   ```
   /kpinfo clan:YOUR_CLAN
   ```

2. **Correct spelling?**
   - Reason name must match EXACTLY
   - Use autocomplete to avoid typos

3. **Action type correct?**
   - Must be "Kickpoint" not "Info-Nachricht"

---

### Too Many Notifications

**Solutions:**

1. **Remove duplicate events:**
   ```
   /listeningevent list
   ```
   Check for multiple events of same type

2. **Change to Info-Nachricht:**
   - Remove event
   - Recreate without kickpoint action

---

## Advanced Tips

### Multiple Warnings

Set up two events for wars:
1. Info message 1 hour before (warning)
2. Kickpoints at end (penalty)

```
/listeningevent add clan:LOST_F2P type:Clan_War duration:3600000 actiontype:Info-Nachricht channel:#war-logs

/listeningevent add clan:LOST_F2P type:Clan_War duration:0 actiontype:Kickpoint channel:#war-logs kickpoint_reason:CW_Attack_vergessen
```

---

### Different Penalties for Different Events

Use different kickpoint amounts:
- CW miss: 3 points (serious)
- Raid miss: 2 points (moderate)
- Clan Games: 2 points (moderate)

Create separate reasons:
```
/kpaddreason clan:LOST_F2P reason:CW_Attack_vergessen amount:3
/kpaddreason clan:LOST_F2P reason:Raid_unter_5 amount:2
/kpaddreason clan:LOST_F2P reason:CG_unter_4000 amount:2
```

---

### Testing New Events

1. Create with Info-Nachricht first (no penalties)
2. Let it fire once to check output
3. If correct, remove and recreate with Kickpoint action

---

### Multiple Clans

Set up events for each clan separately:
```
/listeningevent add clan:LOST_F2P type:Clan_War duration:0 actiontype:Kickpoint channel:#f2p-war-logs kickpoint_reason:CW_Attack_vergessen

/listeningevent add clan:LOST_MAIN type:Clan_War duration:0 actiontype:Kickpoint channel:#main-war-logs kickpoint_reason:CW_Attack_vergessen
```

Each clan can have different settings!

---

## Frequently Asked Questions

**Q: Can I set events to fire every week automatically?**

A: Not currently. Events fire when game events happen (wars, raids, etc.). They don't fire on a calendar schedule.

---

**Q: What if someone leaves clan before event fires?**

A: They won't be checked. The bot only checks current clan members at fire time.

---

**Q: Can I undo automatic kickpoints?**

A: Yes, use `/kpremove id:KICKPOINT_ID` to remove them.

---

**Q: What's the 1-hour buffer for Clan Games?**

A: Clan Games end at 12 PM, but event fires at 1 PM. This gives the Clash API time to update final scores.

---

**Q: Can I change kickpoint amounts after event is created?**

A: No, but you can:
1. Edit the reason template with `/kpeditreason` (affects future events)
2. Remove and recreate the event

---

**Q: Do events work for side clans in CWL?**

A: Yes! CWL events check the entire CWL roster, including side clan members.

---

**Q: How do I know when the next event fires?**

A: Use `/listeningevent list` - it shows "Next fire:" for each event.

---

**Q: Can different co-leaders see all events?**

A: Yes, `/listeningevent list` shows all events for everyone with permission.

---

## Quick Reference

### Event Type Summary

| Event Type | Check What | Fire When |
|-----------|-----------|----------|
| Clan Games | Points under 4000 | 28th at 1 PM |
| Clan War (start) | War opt-outs | War starts |
| Clan War (end) | Missed attacks | War ends |
| CWL Day | Daily attacks | Each day ends |
| Raid | Attacks under 5 | Monday morning |
| Raid Fails | Over-hit districts | Monday morning |

### Duration Quick Reference

| Duration | Meaning |
|----------|---------|
| `-1` | War start only |
| `0` | At event end |
| `3600000` | 1 hour before |
| `7200000` | 2 hours before |
| `21600000` | 6 hours before |
| `43200000` | 12 hours before |
| `86400000` | 24 hours before |

### Action Type Summary

| Action | Does What | Needs Reason? |
|--------|-----------|--------------|
| Info-Nachricht | Message only | No |
| Kickpoint | Message + kickpoints | Yes |
| CW Donator | Select donors | No |
| Filler | List opt-outs | No |
| Raidfails | Analyze raids | Optional |

---

**Need help?** Ask your clan leader or check the Commands Guide for step-by-step `/listeningevent` usage!

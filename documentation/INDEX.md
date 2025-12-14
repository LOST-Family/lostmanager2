# Lost Manager 2 Documentation - Quick Start Index

Welcome! This index helps you find exactly what you need.

---

## I Want To...

### Learn the Basics

**I'm new and want to get started**
→ [Getting Started Guide](USER_GUIDE_GETTING_STARTED.md)
- Link your account
- Understand kickpoints
- Basic commands
- First steps

**I want to know what a specific command does**
→ [Commands Guide](USER_GUIDE_COMMANDS.md)
- All commands explained simply
- What to type and what happens
- Real examples for every command

**Something isn't working**
→ [FAQ & Troubleshooting](USER_GUIDE_FAQ.md)
- Common problems and solutions
- Quick answers
- Troubleshooting steps

---

### Manage My Clan

**I want to set up automated monitoring**
→ [Automated Events Guide](USER_GUIDE_AUTOMATED_EVENTS.md)
- How automation works
- Setting up events
- Event types explained
- Common configurations

**I need help with a specific task**
→ [Scenarios & Workflows](USER_GUIDE_SCENARIOS.md)
- New member joins
- War preparation
- Raid management
- Member promotions
- Complete workflows

**I want a quick command reference**
→ [Quick Reference](05_QUICK_REFERENCE.md)
- Command cheat sheet
- Database queries
- Configuration info

---

### Technical Information

**I need system architecture details**
→ [Master Overview](00_MASTER_OVERVIEW.md)
- Complete system overview
- Technology stack
- Architecture
- All systems explained

**I need command implementation details**
→ [Commands Reference](02_COMMANDS_REFERENCE.md)
- Technical command documentation
- Parameters and types
- Process flows
- Error handling

**I need to understand data structures**
→ [Data Structures](01_DATA_STRUCTURES.md)
- Player, Clan, User objects
- All methods and fields
- Database representation
- API integration

**I need database information**
→ [Database Schema](03_DATABASE_SCHEMA.md)
- All tables and columns
- Indexes and relationships
- Queries and maintenance
- Schema files

**I need event system internals**
→ [Event System Deep Dive](04_EVENT_SYSTEM_DEEP_DIVE.md)
- Scheduler architecture
- Event polling algorithm
- Timing mechanisms
- State management

---

## By Role

### Regular Members

**What you can do:**
- Link your account: [Getting Started](USER_GUIDE_GETTING_STARTED.md#step-1-link-your-account)
- Check your info: [playerinfo command](USER_GUIDE_COMMANDS.md#playerinfo)
- View your kickpoints: [kpmember command](USER_GUIDE_COMMANDS.md#kpmember)
- Set your nickname: [setnick command](USER_GUIDE_COMMANDS.md#setnick)
- Check your wins: [wins command](USER_GUIDE_COMMANDS.md#wins)

**Common questions:**
- [How do I link my account?](USER_GUIDE_FAQ.md#how-do-i-link-my-account)
- [What are kickpoints?](USER_GUIDE_FAQ.md#what-are-kickpoints)
- [Why don't I have the right role?](USER_GUIDE_FAQ.md#why-dont-i-have-the-right-discord-role)

---

### Co-Leaders

**Member management:**
- [Add new member](USER_GUIDE_SCENARIOS.md#new-member-joins-clan)
- [Remove member](USER_GUIDE_COMMANDS.md#removemember)
- [Transfer member](USER_GUIDE_COMMANDS.md#transfermember)
- [Check sync status](USER_GUIDE_COMMANDS.md#memberstatus)

**Kickpoint management:**
- [Add kickpoints](USER_GUIDE_SCENARIOS.md#adding-kickpoints-manually)
- [Review kickpoints](USER_GUIDE_SCENARIOS.md#reviewing-member-kickpoints)
- [Handle appeals](USER_GUIDE_SCENARIOS.md#handling-appeals)

**Event management:**
- [War preparation](USER_GUIDE_SCENARIOS.md#preparing-for-clan-war)
- [Raid management](USER_GUIDE_SCENARIOS.md#managing-raid-weekend)
- [Set up automation](USER_GUIDE_SCENARIOS.md#setting-up-full-automation)

**Common tasks:**
- [Member gets promoted](USER_GUIDE_SCENARIOS.md#member-gets-promoted)
- [Sync all roles](USER_GUIDE_SCENARIOS.md#syncing-all-roles)
- [After war ends](USER_GUIDE_SCENARIOS.md#after-war-ends)

---

### Leaders

**Everything co-leaders can do, plus:**
- [Configure clan settings](USER_GUIDE_COMMANDS.md#clanconfig)
- [Set up full automation](USER_GUIDE_SCENARIOS.md#setting-up-full-automation)
- [Adjust penalties](USER_GUIDE_SCENARIOS.md#adjusting-penalties)

**Important guides:**
- [Automated Events Guide](USER_GUIDE_AUTOMATED_EVENTS.md)
- [Scenarios & Workflows](USER_GUIDE_SCENARIOS.md)

---

### Developers

**Getting started:**
- [Master Overview](00_MASTER_OVERVIEW.md) - System architecture
- [Data Structures](01_DATA_STRUCTURES.md) - Code structure
- [Database Schema](03_DATABASE_SCHEMA.md) - Database design

**Implementation details:**
- [Commands Reference](02_COMMANDS_REFERENCE.md) - All commands
- [Event System](04_EVENT_SYSTEM_DEEP_DIVE.md) - Event internals
- [Quick Reference](05_QUICK_REFERENCE.md) - Lookup tables

---

## By Topic

### Account Linking

**User guides:**
- [How to link account](USER_GUIDE_GETTING_STARTED.md#step-1-link-your-account)
- [verify command](USER_GUIDE_COMMANDS.md#verify)
- [link command](USER_GUIDE_COMMANDS.md#link)
- [Multiple accounts](USER_GUIDE_SCENARIOS.md#member-has-multiple-accounts)

**Troubleshooting:**
- [Can't find API token](USER_GUIDE_FAQ.md#i-cant-find-my-api-token)
- [Token says invalid](USER_GUIDE_FAQ.md#api-token-says-invalid)
- [How to unlink](USER_GUIDE_FAQ.md#how-do-i-unlink-an-account)

---

### Kickpoints

**Understanding:**
- [What are kickpoints](USER_GUIDE_GETTING_STARTED.md#step-3-understand-kickpoints)
- [How they work](USER_GUIDE_FAQ.md#what-are-kickpoints)
- [When they expire](USER_GUIDE_FAQ.md#when-do-kickpoints-expire)

**Managing:**
- [Add kickpoints](USER_GUIDE_SCENARIOS.md#adding-kickpoints-manually)
- [Review kickpoints](USER_GUIDE_SCENARIOS.md#reviewing-member-kickpoints)
- [Handle appeals](USER_GUIDE_SCENARIOS.md#handling-appeals)
- [Monthly review](USER_GUIDE_SCENARIOS.md#end-of-month-review)

**Commands:**
- [kpmember](USER_GUIDE_COMMANDS.md#kpmember) - View kickpoints
- [kpadd](USER_GUIDE_COMMANDS.md#kpadd) - Add kickpoints
- [kpremove](USER_GUIDE_COMMANDS.md#kpremove) - Remove kickpoints
- [kpclan](USER_GUIDE_COMMANDS.md#kpclan) - Clan overview

---

### Automated Events

**Understanding:**
- [What are events](USER_GUIDE_AUTOMATED_EVENTS.md#what-are-automated-events)
- [How they work](USER_GUIDE_AUTOMATED_EVENTS.md#how-automated-events-work)
- [Event types](USER_GUIDE_AUTOMATED_EVENTS.md#event-types-explained)

**Setting up:**
- [Full automation setup](USER_GUIDE_SCENARIOS.md#setting-up-full-automation)
- [Testing events](USER_GUIDE_SCENARIOS.md#testing-new-event-setup)
- [Common configurations](USER_GUIDE_AUTOMATED_EVENTS.md#common-event-setups)

**Event types:**
- [Clan Games](USER_GUIDE_AUTOMATED_EVENTS.md#clan-games-events)
- [Clan War](USER_GUIDE_AUTOMATED_EVENTS.md#clan-war-events)
- [CWL Day](USER_GUIDE_AUTOMATED_EVENTS.md#cwl-day-events)
- [Raid Weekend](USER_GUIDE_AUTOMATED_EVENTS.md#raid-weekend-events)

**Technical:**
- [Event system internals](04_EVENT_SYSTEM_DEEP_DIVE.md)
- [Timing mechanisms](04_EVENT_SYSTEM_DEEP_DIVE.md#event-types-and-timing)
- [State management](04_EVENT_SYSTEM_DEEP_DIVE.md#state-management)

---

### Clan Wars

**Managing wars:**
- [War preparation](USER_GUIDE_SCENARIOS.md#preparing-for-clan-war)
- [After war ends](USER_GUIDE_SCENARIOS.md#after-war-ends)
- [Select donors](USER_GUIDE_COMMANDS.md#cwdonator)

**Automation:**
- [War events](USER_GUIDE_AUTOMATED_EVENTS.md#clan-war-events)
- [Filler check](USER_GUIDE_AUTOMATED_EVENTS.md#mode-1-filler-check-war-start)
- [Missed attacks](USER_GUIDE_AUTOMATED_EVENTS.md#mode-2-missed-attacks-war-end)

---

### Raid Weekend

**Managing raids:**
- [Raid management](USER_GUIDE_SCENARIOS.md#managing-raid-weekend)
- [raidping command](USER_GUIDE_COMMANDS.md#raidping)

**Automation:**
- [Raid events](USER_GUIDE_AUTOMATED_EVENTS.md#raid-weekend-events)
- [Attack checking](USER_GUIDE_AUTOMATED_EVENTS.md#mode-1-simple-attack-check)
- [Raid fails analysis](USER_GUIDE_AUTOMATED_EVENTS.md#mode-2-raid-fails-analysis)

---

### Clan Games

**Managing:**
- [Tracking Clan Games](USER_GUIDE_SCENARIOS.md#tracking-clan-games)

**Automation:**
- [Clan Games events](USER_GUIDE_AUTOMATED_EVENTS.md#clan-games-events)
- [How it works](USER_GUIDE_AUTOMATED_EVENTS.md#what-it-monitors-player-points-scored-during-clan-games)

---

### CWL

**Managing:**
- [Setting up CWL](USER_GUIDE_SCENARIOS.md#setting-up-cwl)
- [Check roster](USER_GUIDE_COMMANDS.md#cwlmemberstatus)

**Automation:**
- [CWL events](USER_GUIDE_AUTOMATED_EVENTS.md#cwl-day-events)

---

### Discord Roles

**Understanding:**
- [How roles work](USER_GUIDE_FAQ.md#how-do-discord-roles-work)
- [What can each role do](USER_GUIDE_FAQ.md#what-can-i-do-as-a-member)

**Managing:**
- [Check roles](USER_GUIDE_COMMANDS.md#checkroles)
- [Member promoted](USER_GUIDE_SCENARIOS.md#member-gets-promoted)
- [Sync all roles](USER_GUIDE_SCENARIOS.md#syncing-all-roles)

**Troubleshooting:**
- [Wrong role](USER_GUIDE_FAQ.md#why-dont-i-have-the-right-discord-role)
- [Bot won't change nickname](USER_GUIDE_FAQ.md#bot-wont-change-my-nickname)

---

## Complete File List

### User Guides (Start Here!)
1. **[USER_GUIDE_GETTING_STARTED.md](USER_GUIDE_GETTING_STARTED.md)** - First steps
2. **[USER_GUIDE_COMMANDS.md](USER_GUIDE_COMMANDS.md)** - All commands
3. **[USER_GUIDE_AUTOMATED_EVENTS.md](USER_GUIDE_AUTOMATED_EVENTS.md)** - Event automation
4. **[USER_GUIDE_FAQ.md](USER_GUIDE_FAQ.md)** - FAQ & troubleshooting
5. **[USER_GUIDE_SCENARIOS.md](USER_GUIDE_SCENARIOS.md)** - Workflows

### Technical Documentation
6. **[00_MASTER_OVERVIEW.md](00_MASTER_OVERVIEW.md)** - System overview
7. **[01_DATA_STRUCTURES.md](01_DATA_STRUCTURES.md)** - Data models
8. **[02_COMMANDS_REFERENCE.md](02_COMMANDS_REFERENCE.md)** - Technical commands
9. **[03_DATABASE_SCHEMA.md](03_DATABASE_SCHEMA.md)** - Database
10. **[04_EVENT_SYSTEM_DEEP_DIVE.md](04_EVENT_SYSTEM_DEEP_DIVE.md)** - Event internals
11. **[05_QUICK_REFERENCE.md](05_QUICK_REFERENCE.md)** - Quick lookup

### This File
12. **[README.md](README.md)** - Documentation overview

---

## Search Tips

### Can't Find Something?

**Use your browser's find function** (Ctrl+F or Cmd+F):

**For commands:**
- Search command name (e.g., "verify", "kpadd")
- Look in [Commands Guide](USER_GUIDE_COMMANDS.md)

**For problems:**
- Search error message or problem description
- Check [FAQ](USER_GUIDE_FAQ.md)

**For tasks:**
- Search what you want to do (e.g., "promote", "war", "raid")
- Check [Scenarios](USER_GUIDE_SCENARIOS.md)

**For events:**
- Search event type (e.g., "Clan Games", "Raid")
- Check [Automated Events](USER_GUIDE_AUTOMATED_EVENTS.md)

---

## Quick Links by Question

**"How do I..."**
- Link my account? → [Getting Started](USER_GUIDE_GETTING_STARTED.md#step-1-link-your-account)
- Check my kickpoints? → [kpmember](USER_GUIDE_COMMANDS.md#kpmember)
- Add a new member? → [New member scenario](USER_GUIDE_SCENARIOS.md#new-member-joins-clan)
- Set up automation? → [Full automation](USER_GUIDE_SCENARIOS.md#setting-up-full-automation)
- Change my nickname? → [setnick](USER_GUIDE_COMMANDS.md#setnick)

**"What is..."**
- A kickpoint? → [Kickpoints explained](USER_GUIDE_FAQ.md#what-are-kickpoints)
- An automated event? → [Events explained](USER_GUIDE_AUTOMATED_EVENTS.md#what-are-automated-events)
- The difference between roles? → [Permissions](USER_GUIDE_FAQ.md#what-can-i-do-as-a-member)

**"Why..."**
- Didn't I get notified? → [Event troubleshooting](USER_GUIDE_FAQ.md#why-didnt-i-get-notified)
- Is my role wrong? → [Role troubleshooting](USER_GUIDE_FAQ.md#why-dont-i-have-the-right-discord-role)
- Did I get kickpoints? → [Auto kickpoints](USER_GUIDE_FAQ.md#why-did-i-get-kickpoints-automatically)

**"Can I..."**
- Link multiple accounts? → [Multiple accounts](USER_GUIDE_FAQ.md#can-i-link-multiple-accounts)
- Remove kickpoints? → [Remove kickpoints](USER_GUIDE_FAQ.md#can-kickpoints-be-removed)
- Pause events? → [Pause events](USER_GUIDE_FAQ.md#can-events-be-paused)

---

## Documentation Philosophy

**User Guides (Priority):**
- Simple language, no jargon
- User perspective (not developer)
- Practical focus (what to type, what happens)
- Real examples with actual values
- Step-by-step workflows

**Technical Docs (When Needed):**
- Implementation details
- System architecture
- Developer perspective
- Database and code structure

**Both Sections:**
- Comprehensive coverage
- Accurate information
- Easy to navigate
- Searchable

---

## Getting Help

**Still can't find what you need?**

1. **Check the FAQ first:** [FAQ & Troubleshooting](USER_GUIDE_FAQ.md)
2. **Search all docs:** Use browser find function (Ctrl+F)
3. **Ask your leaders:** They can help with clan-specific questions
4. **Check Discord:** Look for bot channels or announcements

---

## Feedback

**Found an error or missing information?**
- Report to your clan leaders
- They can contact the bot developer

**Want to contribute?**
- Documentation is maintained in the GitHub repository
- Contributions welcome via pull requests

---

**Last Updated:** 2024-12-14  
**Bot Version:** 2.1.0  
**Documentation Version:** 2.2.0

---

*Use the links above to jump directly to what you need. Happy managing!* 🎮

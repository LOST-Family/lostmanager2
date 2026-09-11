# Quick Reference Guide

This is a condensed reference guide for LostManager2. For detailed information, see the other documentation files.

---

## Essential Commands at a Glance

### Player Linking
```
/verify tag:#ABC123 apitoken:xxx        → Link your account
/link tag:#ABC123 user:@User            → Admin link
/unlink tag:#ABC123                     → Remove link
/playerinfo user:@User                  → Show user's accounts
```

### Member Management
```
/addmember clan:X player:#TAG role:Y    → Add to clan DB
/removemember player:#TAG               → Remove from clan DB
/editmember player:#TAG role:Y          → Change role
/listmembers clan:X                     → List clan members
/memberstatus clan:X                    → Check DB/API sync
/transfermember player:#TAG clan:Y      → Move between clans
```

### Kickpoints
```
/kpaddreason clan:X reason:Y amount:Z   → Create template
/kpadd player:#TAG reason:Y             → Add kickpoints
/kpmember player:#TAG                   → View kickpoints
/kpclan clan:X                          → View clan KPs
/kpremove id:123                        → Delete kickpoint
/clanconfig clan:X                      → Configure clan
```

### Utilities
```
/checkroles clan:X                      → Verify Discord roles
/raidping clan:X                        → Ping incomplete raids
/wins season:2024-01 clan:X             → Show wins stats
/listeningevent add ...                 → Create auto-event
/cwdonator clan:X                       → Select donors
```

---

## Database Quick Reference

### Core Tables
```sql
players (coc_tag, discord_id, clan_tag, clan_role, name)
clans (clan_tag, name, max_kickpoints, ...)
kickpoints (id, player_tag, reason, amount, date, expires)
kickpoint_reasons (name, clan_tag, amount)
listening_events (id, clan_tag, listeningtype, ...)
achievement_data (id, player_tag, type, time, data)
```

### Common Queries
```sql
-- Get player
SELECT * FROM players WHERE coc_tag = '#ABC123';

-- Get clan members
SELECT * FROM players WHERE clan_tag = '#CLAN123';

-- Get user's accounts
SELECT * FROM players WHERE discord_id = '123456789012345678';

-- Get active kickpoints
SELECT * FROM kickpoints WHERE player_tag = ? AND expires > NOW();

-- Get clan config
SELECT * FROM clans WHERE clan_tag = '#CLAN123';
```

---

## Event System Cheat Sheet

### Event Types
- **CS** (Clan Games): 22nd-28th monthly, tracks points
- **CW** (Clan War): Tracks attacks, can trigger on start/end
- **CWLDAY**: Tracks CWL day attacks
- **RAID**: Tracks raid attacks and district analysis

### Duration Values
- `-1`: Fire on event START (war start detection)
- `0`: Fire at event END
- `3600000`: Fire 1 hour before end (milliseconds)

### Action Types
- **infomessage**: Info only, no automation
- **kickpoint**: Auto-add kickpoints (requires reason)
- **cwdonator**: Random donor selection
- **filler**: List war fillers (opted out)
- **raidfails**: Raid district analysis

### Example Events
```
War End (Missed Attacks):
/listeningevent add clan:X type:Clan_War duration:0 
  actiontype:Kickpoint channel:#logs 
  kickpoint_reason:CW_Attack

War Start (Filler Check):
/listeningevent add clan:X type:Clan_War duration:start 
  actiontype:Filler channel:#prep

Clan Games:
/listeningevent add clan:X type:Clan_Games duration:0 
  actiontype:Kickpoint channel:#logs 
  kickpoint_reason:CG_Low

Raid End:
/listeningevent add clan:X type:Raid duration:0 
  actiontype:Kickpoint channel:#logs 
  kickpoint_reason:Raid_Incomplete
```

---

## Data Wrapper Reference

### Player
```java
Player p = new Player("#ABC123");
p.getNameAPI();              // Current name from API
p.getClanDB();               // Clan from database (authoritative)
p.IsLinked();                // Is linked to Discord?
p.getKickpointsTotal();      // Total active kickpoints
p.getUser();                 // Linked Discord user
p.refreshData();             // Clear cache
```

### Clan
```java
Clan c = new Clan("#CLAN123");
c.getPlayersDB();            // Members from database
c.isCWActive();              // Is war active?
c.RaidActive();              // Is raid active?
c.getMaxKickpoints();        // Kickpoint limit
c.getKickpointReasons();     // Reason templates
```

### User
```java
User u = new User("123456789012345678");
u.getAllLinkedAccounts();    // All linked players
u.getClanRoles();            // Map of clan_tag → role
```

---

## Configuration Files

### Environment Variables
```bash
# Discord
LOST_MANAGER_TOKEN=xxx
DISCORD_GUILD_ID=xxx
DISCORD_VERIFIED_ROLE_ID=xxx
DISCORD_EX_MEMBER_ROLE_ID=xxx

# Database
LOST_MANAGER_DB_URL=jdbc:postgresql://host:port/db
LOST_MANAGER_DB_USER=user
LOST_MANAGER_DB_PASSWORD=pass

# APIs
LOST_MANAGER_API_KEY=xxx              # CoC API
```

### Files
```
achievement_data_schema.sql            # Achievement tracking
cw_fillers_table.sql                   # War fillers
cwdonator_lists_table.sql              # Donor rotation
sideclans_table.sql                    # CWL side clans
```

---

## Permissions Reference

### Command Permissions
- **Co-Leader or Higher**: Most management commands
  - Member management, kickpoints, events, etc.
- **Leader Only**: `/clanconfig`
- **Admin Only**: `/restart`
- **Anyone**: Info commands (`/playerinfo`, `/listmembers`, etc.)

### Discord Bot Permissions
Required permissions:
- Send Messages
- Embed Links
- Manage Nicknames
- Manage Roles
- Read Message History
- Add Reactions
- View Channel

---

## Clan Role Mapping

### Database Values
```
Database clan_role    → In-Game Role    → RoleType Enum
"leader"              → Leader           → LEADER
"coLeader"            → Co-Leader        → COLEADER
"admin"               → Elder            → ELDER
"member"              → Member           → MEMBER
"hiddencoleader"      → Hidden Co-Leader → COLEADER
```

**Important**: "admin" in database = Elder, NOT bot admin!

---

## Kickpoints System

### Workflow
1. **Setup**: `/clanconfig` - Set max_kickpoints, expire_after_days
2. **Create Reasons**: `/kpaddreason` - Create templates
3. **Add Kickpoints**: `/kpadd` - Assign to players
4. **Monitor**: `/kpmember` or `/kpclan` - Check status
5. **Manage**: `/kpedit` or `/kpremove` - Modify/delete

### Expiration
```
expires = date + clan.kickpoints_expire_after_days
```

Active kickpoints: `WHERE expires > NOW()`

---

## API Integration

### CoC API Endpoints
```
GET /players/{tag}                     → Player info
GET /clans/{tag}                       → Clan info
GET /clans/{tag}/currentwar            → Current war
GET /clans/{tag}/currentwar/leaguegroup → CWL group
GET /clans/{tag}/capitalraidseasons    → Raid data
POST /players/{tag}/verifytoken        → Verify API token
```

### Authentication
```
Authorization: Bearer {LOST_MANAGER_API_KEY}
```

### Rate Limits
- CoC API has rate limits (undocumented)
- Bot polls every 2 minutes to minimize calls
- Retry with exponential backoff on errors

---

## Scheduling System

### Schedulers
1. **schedulernames**: Name updates (every 2 hours)
2. **schedulertasks**: All events and automated tasks
3. **Event Polling**: Check events (every 2 minutes)

### Event Lifecycle
```
Create → Poll → Schedule → Execute → Log
```

### Timing
- Polling interval: 2 minutes
- Scheduling threshold: 5 minutes
- Retry attempts: 3 with backoff (5s, 10s, 20s)

---

## Troubleshooting

### Bot Won't Start
1. Check environment variables
2. Verify database connection
3. Check Discord token
4. Review console logs

### Commands Not Working
1. Bot online?
2. Correct permissions?
3. Check command syntax
4. View console for errors

### Events Not Firing
1. Check `/listeningevent list`
2. Verify game state (war/raid active)
3. Check fire time calculation
4. Review console logs

### Database Issues
1. Test connection: `psql -U user -d db`
2. Check credentials
3. Verify tables exist
4. Run schema files if needed

---

## Maintenance Tasks

### Daily
- Monitor console for errors
- Check event execution

### Weekly
- Review kickpoints: `/kpclan`
- Check member sync: `/memberstatus`
- Verify roles: `/checkroles`
- Clean cw_fillers: `DELETE FROM cw_fillers WHERE war_end_time < NOW() - INTERVAL '14 days'`

### Monthly
- Clean achievement_data: `SELECT cleanup_old_achievement_data()`
- Review event configurations
- Update clan settings if needed
- Database backup

### Quarterly
- Full database backup
- Review and optimize indexes
- Update documentation if needed

---

## Common Workflows

### New Member Onboarding
1. Member joins Discord
2. `/verify tag:#TAG apitoken:xxx` (self-service)
3. Bot assigns verified role
4. Leader: `/addmember clan:X player:#TAG role:Member`
5. Verify: `/checkroles clan:X`

### Clan War Setup
1. Create events:
   - Start filler check: `duration:start actiontype:Filler`
   - End attack check: `duration:0 actiontype:Kickpoint`
2. Events fire automatically
3. Review results in channel
4. Handle violations

### Kickpoint Management
1. Configure: `/clanconfig clan:X`
2. Create reasons: `/kpaddreason clan:X reason:Y amount:Z`
3. Add as needed: `/kpadd player:#TAG reason:Y`
4. Weekly review: `/kpclan clan:X`
5. Address players near limit

### Season Tracking
1. Bot auto-saves wins at season start/end
2. View stats: `/wins season:2024-01 clan:X`
3. Identify inactive members
4. Take action as needed

---

## Best Practices

### Security
✅ Use environment variables for secrets
✅ Regular database backups
✅ Restrict bot admin access
✅ Monitor bot logs
❌ Don't share API keys
❌ Don't commit secrets to git

### Performance
✅ Use prepared statements
✅ Index frequently queried columns
✅ Regular VACUUM ANALYZE
✅ Monitor API rate limits
❌ Don't query in loops
❌ Don't cache forever

### Operations
✅ Test in dev environment first
✅ Document configuration changes
✅ Keep documentation updated
✅ Regular audits
❌ Don't restart during events
❌ Don't modify database directly (use commands)

---

## File Structure
```
lostmanager2/
├── pom.xml                              # Maven config
├── src/main/java/
│   ├── lostmanager/Bot.java             # Main entry point
│   ├── commands/                        # All commands
│   │   ├── discord/admin/               # Discord admin
│   │   ├── discord/util/                # Discord utility
│   │   └── coc/                         # CoC commands
│   │       ├── links/                   # Linking
│   │       ├── memberlist/              # Members
│   │       ├── kickpoints/              # Kickpoints
│   │       └── util/                    # CoC utility
│   ├── datawrapper/                     # Data models
│   │   ├── Player.java
│   │   ├── Clan.java
│   │   ├── User.java
│   │   ├── Kickpoint.java
│   │   ├── ListeningEvent.java
│   │   └── ...
│   ├── dbutil/                          # Database
│   │   ├── Connection.java
│   │   ├── DBUtil.java
│   │   └── DBManager.java
│   └── util/                            # Utilities
│       ├── MessageUtil.java
│       ├── SeasonUtil.java
│       └── ...
├── achievement_data_schema.sql          # Schema files
├── cw_fillers_table.sql
├── cwdonator_lists_table.sql
├── sideclans_table.sql
└── documentation/                       # This folder
    ├── 00_MASTER_OVERVIEW.md
    ├── 01_DATA_STRUCTURES.md
    ├── 02_COMMANDS_REFERENCE.md
    ├── 03_DATABASE_SCHEMA.md
    ├── 04_EVENT_SYSTEM_DEEP_DIVE.md
    └── 05_QUICK_REFERENCE.md
```

---

## Resources

### Documentation Files
- **00_MASTER_OVERVIEW.md**: Complete system overview
- **01_DATA_STRUCTURES.md**: Data wrappers and models
- **02_COMMANDS_REFERENCE.md**: All commands detailed
- **03_DATABASE_SCHEMA.md**: Database schema and SQL
- **04_EVENT_SYSTEM_DEEP_DIVE.md**: Event system internals
- **05_QUICK_REFERENCE.md**: This file

### External Documentation
- **LISTENING_EVENTS.md**: Event system user guide
- **LISTENING_EVENTS_COMPLETE_GUIDE.md**: Comprehensive event guide
- **CHECKROLES_COMMAND.md**: Checkroles command details
- **IMPLEMENTATION_SUMMARY.md**: Feature implementation summary

### External Resources
- JDA Documentation: https://jda.wiki
- CoC API: https://developer.clashofclans.com/
- PostgreSQL Docs: https://www.postgresql.org/docs/
- Discord Developer Portal: https://discord.com/developers/docs

---

## Version Information

**Current Version**: 2.1.0

**Key Dependencies**:
- JDA: 5.0.0-alpha.14 (Discord API)
- PostgreSQL: 42.7.7 (Database)
- org.json: 20230227 (JSON parsing)
- Jackson: 2.15.2 (JSON serialization)

**Build Tool**: Maven

**Java Version**: Requires Java 11+

---

## Support and Contribution

### Getting Help
1. Check this documentation
2. Review console logs for errors
3. Test in development environment
4. Check existing issues/PRs

### Reporting Issues
Include:
- Bot version
- Error messages from console
- Steps to reproduce
- Expected vs actual behavior

### Contributing
1. Fork the repository
2. Create feature branch
3. Test thoroughly
4. Submit pull request with description

---

**End of Quick Reference**

This guide provides quick lookups for common tasks. For detailed information, see the comprehensive documentation files listed above.

# Lost Manager 2 - Discord Bot for Clash of Clans

A comprehensive Discord bot for managing Clash of Clans clans, featuring player verification, kickpoint tracking, automated event monitoring, and member management.

---

## 🎮 What Is Lost Manager 2?

Lost Manager 2 helps you manage your Clash of Clans clan on Discord by:
- **Linking** Discord accounts to Clash of Clans player tags
- **Tracking** penalties (kickpoints) for rule violations
- **Automating** monitoring of Wars, Raids, Clan Games, and CWL
- **Syncing** Discord roles with in-game clan ranks
- **Managing** member rosters and transfers

---

## 📚 Documentation

### 👤 For Discord Users

New to the bot? Start here:

**[📖 Getting Started Guide](documentation/USER_GUIDE_GETTING_STARTED.md)**
- Link your account
- Understand kickpoints
- Learn basic commands
- First steps with the bot

**[⚡ Commands Guide](documentation/USER_GUIDE_COMMANDS.md)**
- All commands explained simply
- What to type and what happens
- Real examples for every command

**[🔧 FAQ & Troubleshooting](documentation/USER_GUIDE_FAQ.md)**
- Common problems and solutions
- Quick answers
- Troubleshooting steps

---

### 👑 For Clan Leaders

Managing your clan:

**[🤖 Automated Events Guide](documentation/USER_GUIDE_AUTOMATED_EVENTS.md)**
- Set up automatic monitoring
- Event types explained
- Common configurations

**[📋 Scenarios & Workflows](documentation/USER_GUIDE_SCENARIOS.md)**
- New member setup
- War preparation
- Raid management
- Complete task workflows

**[🔍 Quick Index](documentation/INDEX.md)**
- Find anything fast
- Search by topic or role
- Complete file list

---

### 💻 For Developers

Technical documentation:

**[📘 Master Overview](documentation/00_MASTER_OVERVIEW.md)**
- System architecture
- Technology stack
- Complete overview

**[🗄️ Database Schema](documentation/03_DATABASE_SCHEMA.md)**
- All tables and columns
- Relationships
- Queries

**[⚙️ Event System Deep Dive](documentation/04_EVENT_SYSTEM_DEEP_DIVE.md)**
- Scheduler architecture
- Event polling
- State management

**[📁 All Documentation](documentation/README.md)**
- Complete documentation index
- 12 comprehensive guides
- 340+ KB of documentation

---

## 🚀 Quick Start

### For New Members

1. **Link your account:**
   ```
   /verify tag:#YOURTAG apitoken:YOURTOKEN
   ```

2. **Check your info:**
   ```
   /playerinfo
   ```

3. **View your kickpoints:**
   ```
   /kpmember player:YourName
   ```

Need help? Check the [Getting Started Guide](documentation/USER_GUIDE_GETTING_STARTED.md)!

---

### For Co-Leaders

**Common tasks:**

Add new member:
```
/addmember clan:YourClan player:#TAG role:Member
```

Check who needs to raid:
```
/raidping clan:YourClan
```

Check role sync:
```
/checkroles clan:YourClan
```

More workflows: [Scenarios Guide](documentation/USER_GUIDE_SCENARIOS.md)

---

### For Leaders

**Set up automation:**

Create automated Clan War checking:
```
/listeningevent add clan:YourClan type:Clan_War duration:0 actiontype:Kickpoint channel:#war-logs kickpoint_reason:CW_Attack_vergessen
```

Full setup guide: [Automated Events](documentation/USER_GUIDE_AUTOMATED_EVENTS.md)

---

## ✨ Key Features

### Player Management
- ✅ Self-service account verification
- ✅ Multi-account support per Discord user
- ✅ Automatic role assignment
- ✅ Nickname synchronization

### Kickpoints System
- ✅ Automated penalty tracking
- ✅ Configurable expiration (default 30 days)
- ✅ Custom reason templates per clan
- ✅ Violation history tracking

### Automated Events
- ✅ **Clan Games** - Track points (4000 threshold)
- ✅ **Clan Wars** - Check missed attacks
- ✅ **CWL** - Daily attack monitoring
- ✅ **Raids** - Attack completion + coordination analysis

### Clan Management
- ✅ Member roster tracking
- ✅ Role synchronization checks
- ✅ Inter-clan transfers
- ✅ Season wins tracking
- ✅ CWL roster verification

---

## 🎯 Commands Overview

### Everyone Can Use
- `/verify` - Link your account
- `/playerinfo` - Check your info
- `/kpmember` - View kickpoints
- `/setnick` - Set nickname
- `/wins` - Check season wins

### Co-Leader+
- `/addmember`, `/removemember`, `/transfermember` - Member management
- `/kpadd`, `/kpremove`, `/kpedit` - Kickpoint management
- `/listeningevent` - Event automation
- `/raidping` - Check raid progress
- `/checkroles` - Verify Discord roles

### Leader Only
- `/clanconfig` - Configure clan settings

**[Full Command List](documentation/USER_GUIDE_COMMANDS.md)** with examples and explanations.

---

## 🛠️ Technology Stack

- **Language:** Java
- **Framework:** JDA (Java Discord API) 5.0.0-alpha.14
- **Database:** PostgreSQL 42.7.7
- **CoC API:** Clash of Clans Official API
- **REST API:** Built-in HTTP Server (Port 8070)
- **Build Tool:** Maven

---

## 🌐 REST API

Lost Manager 2 includes a REST API for programmatic access to Clans, Players, and Users data.

**Key Features:**
- JSON endpoints for clans, players, and users
- CORS support for web applications
- Runs on port 8070 (configurable)
- Zero security vulnerabilities (CodeQL verified)

**Documentation:**
- **[REST API Documentation](REST_API_DOCUMENTATION.md)** - Complete API reference
- **[REST API Test Plan](REST_API_TEST_PLAN.md)** - Manual testing guide
- **[Implementation Summary](REST_API_IMPLEMENTATION_SUMMARY.md)** - Technical details

**Quick Example:**
```bash
# Get all clans
curl http://localhost:8070/api/clans

# Get clan members
curl http://localhost:8070/api/clans/%232P0LRJP9V/members

# Get user info
curl http://localhost:8070/api/users/123456789012345678
```

---

## 📖 Additional Documentation

### Root Directory Guides
- [LISTENING_EVENTS.md](LISTENING_EVENTS.md) - Event system user guide
- [LISTENING_EVENTS_COMPLETE_GUIDE.md](LISTENING_EVENTS_COMPLETE_GUIDE.md) - Comprehensive event guide
- **[REST_API_DOCUMENTATION.md](REST_API_DOCUMENTATION.md)** - REST API reference ✨
- **[REST_API_TEST_PLAN.md](REST_API_TEST_PLAN.md)** - API testing guide ✨
- **[REST_API_IMPLEMENTATION_SUMMARY.md](REST_API_IMPLEMENTATION_SUMMARY.md)** - Technical details ✨

### Documentation Folder
- **5 User-Friendly Guides** - Simple language, practical focus
- **6 Technical Docs** - System architecture, implementation details
- **340+ KB** of comprehensive documentation
- **12 documents** covering every feature

**[Browse All Documentation](documentation/)**

---

## 📞 Getting Help

### For Users
1. Check [FAQ & Troubleshooting](documentation/USER_GUIDE_FAQ.md)
2. Search documentation (Ctrl+F)
3. Ask your clan leaders
4. Check Discord announcements

### For Developers
1. Read [Master Overview](documentation/00_MASTER_OVERVIEW.md)
2. Check [Technical Docs](documentation/)
3. Review source code in `src/`

---

## 🤝 Contributing

Documentation contributions welcome! See [documentation/README.md](documentation/README.md) for guidelines.

---

## 📄 License

This project is part of the Lost Family clan management system.

---

## 🔗 Quick Links

- **[Documentation Home](documentation/)** - All documentation
- **[Quick Index](documentation/INDEX.md)** - Find anything fast
- **[Getting Started](documentation/USER_GUIDE_GETTING_STARTED.md)** - New user guide
- **[Commands Guide](documentation/USER_GUIDE_COMMANDS.md)** - All commands
- **[FAQ](documentation/USER_GUIDE_FAQ.md)** - Troubleshooting

---

## 📊 Stats

- **39+ commands** fully documented
- **4 event types** (Clan Games, Wars, CWL, Raids)
- **200+ examples** in documentation
- **100+ workflow scenarios**
- **12 comprehensive guides**
- **340+ KB** of documentation

---

**Version:** 2.1.0  
**Documentation Version:** 2.2.0  
**Last Updated:** 2024-12-14

---

*Built with ❤️ for the Lost Family clan organization*

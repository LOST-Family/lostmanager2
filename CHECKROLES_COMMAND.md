# CheckRoles Command Documentation

## Overview
The `/checkroles` command allows clan Co-Leaders and above to verify that all clan members have the correct Discord roles assigned based on their clan rank.

## Usage

```
/checkroles clan:<clan_name>
```

### Parameters
- **clan** (required): The clan to check. Uses autocomplete to select from configured clans.

### Permissions
- Requires at least Co-Leader rank in any clan
- Command fails with error message if user lacks permissions

## How It Works

1. **Retrieves Clan Members**: Gets all members from the clan database
2. **Checks Each Member**: For each linked member:
   - Determines their clan role (Leader, Co-Leader, Elder, Member)
   - Gets the expected Discord role for that clan rank
   - Checks if the member has that role on Discord
3. **Reports Issues**: Lists any members who:
   - Are not on the Discord server
   - Don't have their expected role
   - Have roles that aren't configured for the clan

## Output

The command displays:

### Statistics Section
- Total members in the clan
- Number of linked members (Discord account connected)
- Number of unlinked members (no Discord account connected)
- Number of members without the correct role

### Issues Section
Lists each member with a role issue, showing:
- Player name and tag
- Expected role (Anführer/Vize-Anführer/Ältester/Mitglied)
- Discord user mention (@username)
- Issue type:
  - `(nicht auf dem Server)` - User is linked but not in Discord server
  - `(Rolle nicht konfiguriert)` - Discord role not set up for this clan
  - `(fehlt: @RoleName)` - User is missing the expected role

### Success Message
If all linked members have correct roles:
```
✅ Alle verlinkten Mitglieder haben die korrekte Discord-Rolle!
```

## Features

### Refresh Button
- Includes a 🔁 refresh button
- Click to re-run the check without typing the command again
- Useful for verifying fixes after role assignments

### Timestamp
- Shows when the check was last run
- Format: `dd.MM.yyyy um HH:mm Uhr` (German timezone)

### Smart Handling
- **Hidden Co-Leaders**: Checked as co-leaders (they should have the co-leader role)
- **Unlinked Members**: Counted but not checked for roles (can't have Discord roles)
- **Unconfigured Roles**: Detected and reported if clan roles aren't set up

## Example Use Cases

### Scenario 1: After Clan War League
Check if all promoted members received their new roles:
```
/checkroles clan:LOST_F2P
```

### Scenario 2: Regular Maintenance
Weekly check to ensure role synchronization:
```
/checkroles clan:MyClans
```

### Scenario 3: New Member Onboarding
Verify new members received their roles after linking:
```
/checkroles clan:Training_Clan
```

## Role Mapping

The command maps clan roles to Discord roles as configured in the database:

| Clan Role | Database Field | German Display |
|-----------|----------------|----------------|
| Leader | leader_role_id | Anführer |
| Co-Leader | co_leader_role_id | Vize-Anführer |
| Elder | elder_role_id | Ältester |
| Member | member_role_id | Mitglied |

## Troubleshooting

### "Der Parameter 'clan' ist erforderlich!"
- You must select a clan from the autocomplete dropdown

### "Du musst mindestens Vize-Anführer eines Clans sein..."
- You don't have permission to use this command
- Contact a clan leader or bot administrator

### "Keine Mitglieder in diesem Clan gefunden."
- The selected clan has no members in the database
- Clan may need to be synced with the API

### Roles showing as "Rolle nicht konfiguriert"
- The clan's Discord roles haven't been set up in the database
- Use `/clanconfig` to configure roles for the clan

## Tips

1. **Run after promotions/demotions**: Always check after rank changes in-game
2. **Use the refresh button**: Quick way to verify your role assignments
3. **Check unlinked members separately**: Use `/listmembers` to see who needs to link
4. **Set up roles first**: Configure clan roles via `/clanconfig` before using this command

## Related Commands

- `/listmembers` - View all clan members and their ranks
- `/clanconfig` - Configure Discord roles for a clan
- `/link` - Link Discord account to CoC account
- `/verify` - Verify a linked account

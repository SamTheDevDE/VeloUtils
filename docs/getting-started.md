# Getting started

[Documentation](README.md) · [Modules](modules.md) · [Configuration](configuration.md)

This guide is for server owners. No programming knowledge is required.

## Words used in these guides

| Word | Meaning |
|---|---|
| Proxy | Velocity, the service players connect to before joining a backend server |
| Backend | A Paper or Folia server registered behind Velocity, such as `lobby` or `survival` |
| Module | A VeloUtils feature that can be switched on or off |
| Placeholder | A value such as `{player}` that VeloUtils replaces when showing text |
| MiniMessage | The formatting used for colors and styles, such as `<red>text</red>` |
| UUID | The permanent account ID used to identify a player even if their name changes |

## 1. Choose your setup

| Your setup | Install this |
|---|---|
| Velocity proxy only | `VeloUtils-Velocity-<version>.jar` on Velocity |
| One Paper or Folia server | `VeloUtils-Paper-<version>.jar` on that server |
| Velocity network with backend servers | The Velocity JAR on the proxy and the Paper JAR on every Paper/Folia backend |

The Paper JAR also supports Folia. You do not need a separate Folia download.

## 2. Check the requirements

- Java 25
- Velocity 4.1 or newer for the proxy plugin
- Paper or Folia 26.2 for the backend plugin
- PlaceholderAPI only if you want placeholders from other plugins

Back up your server before installing or updating any plugin.

## 3. Install the JARs

### Velocity

1. Stop the proxy.
2. Put `VeloUtils-Velocity-<version>.jar` in the proxy's `plugins` folder.
3. Start the proxy once.
4. Open `plugins/VeloUtils/config.yml` and choose the modules you want.
5. Restart the proxy after changing module settings.

### Paper or Folia

1. Stop the server.
2. Put `VeloUtils-Paper-<version>.jar` in the server's `plugins` folder.
3. Start the server once.
4. Open `plugins/VeloUtils/config.yml` and choose the modules you want.
5. Restart the server.

Feature-specific files such as `modules/chat.yml` are created when their module is enabled for the first time.

## 4. Connect Velocity and your backends

Skip this section for a proxy-only or standalone Paper/Folia setup.

On the proxy and every backend:

1. Set `protocol.authentication.required` to `true`.
2. Put the same private secret in `protocol.authentication.shared-secret`.
3. Use a random secret containing at least 32 characters.
4. Set each backend's `server-id` to its exact Velocity server name, such as `lobby` or `survival`.
5. Restart the proxy and all backends.

The relevant part of both config files should look like this. Replace the example secret with your own value:

```yaml
protocol:
  authentication:
    required: true
    shared-secret: "replace-this-with-your-own-private-32-character-or-longer-secret"
```

Never share the secret or upload a populated config file publicly. Also firewall backend ports and enable Velocity secure player forwarding.

## 5. Enable features

Features are called modules. Most optional modules start disabled so the plugin does not use resources for features you do not want.

For example, to enable chat, messaging, presentation, and AFK on Paper/Folia:

```yaml
modules:
  afk: true
  announcements: false
  chat: true
  messaging: true
  presentation: true
  moderation: false
  placeholders: true
  staff-chat: true
  network-alerts: true
```

For cross-server chat or private messages, also enable `chat` or `messaging` in the Velocity `modules` section.

```yaml
modules:
  chat: true
  messaging: true
```

See [Modules](modules.md) before enabling a feature and [Configuration](configuration.md) for the related files.

## 6. Set permissions

Velocity permissions are normally managed with LuckPerms or another permission plugin. VeloUtils does not automatically make players administrators.

Useful starting points:

```text
lp group admin permission set veloutils.admin.* true
lp group admin permission set veloutils.network.* true
lp group admin permission set veloutils.maintenance.* true
```

Review the complete [commands and permissions](commands-and-permissions.md) page before granting `veloutils.*`, because it includes powerful moderation and remote-operation permissions.

## 7. Check the installation

On Velocity, run:

```text
/veloutils status
/veloutils config validate
```

For a combined network, join each backend and use `/serverinfo <server>` to check whether its bridge is healthy.

If a feature does not work:

- Confirm its module is enabled on the correct server.
- Restart after changing module settings.
- Check the console for a clear configuration error.
- For network features, confirm the proxy and backend use the same secret.
- Check that you have the required permission.
- Read [What currently works?](implementation-status.md) for known limitations.

When asking for help, include the VeloUtils version, platform version, relevant error message, and a copy of the affected config with every password, secret, hash key, and webhook removed.

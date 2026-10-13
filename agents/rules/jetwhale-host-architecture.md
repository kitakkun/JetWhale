# jetwhale-host Architecture Rules

## Repository vs Service

```
Repository: Data persistence and retrieval (DataStore, DB, files, memory cache)
Service: Everything else (business logic, object management, external integrations)
```

### Repository
- Handles access to a single data source
- Does NOT depend on other Repositories
- Does NOT contain business logic
- Examples: `EnabledPluginsRepository`, `PluginFactoryRepository`, `DebugSessionRepository`

### Service
- Combines multiple Repositories/Services for business logic
- Manages object lifecycles (creation/disposal)
- Examples: `PluginInstanceService`, `PluginComposeSceneService`, `ADBAutoWiringService`
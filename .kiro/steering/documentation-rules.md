# Documentation Structure and Maintenance Rules

This steering file defines the rules and guidelines for maintaining the project's documentation structure. All agents working with documentation must follow these rules.

---

## Documentation Structure Rules

### Directory Organization

**Active Documentation (`/docs/`):**
- `/docs/project/` - Requirements, architecture, and high-level decisions
- `/docs/domain/` - Domain models, state machines, and business logic
- `/docs/implementation/` - Component details, interactions, and technical specifics
- `/docs/operations/` - Error handling, security, monitoring, and troubleshooting
- `/docs/testing/` - Test strategies, scenarios, and results
- `/docs/guides/` - User guides, setup instructions, and tutorials

**Archived Documentation (`/archive/`):**
- `/archive/tasks/` - Completed task files (TASK_*.md)
- `/archive/analyses/` - Historical analyses and investigations
- `/archive/fixes/` - Historical bug fixes and patches

### Document Placement Rules

**When creating new documentation:**
1. **Requirements and Architecture** → `/docs/project/`
2. **Domain Models and State Machines** → `/docs/domain/`
3. **Component Implementation Details** → `/docs/implementation/`
4. **Error Handling and Security** → `/docs/operations/`
5. **Test Documentation** → `/docs/testing/`
6. **User-Facing Guides** → `/docs/guides/`
7. **Temporary Task Files** → Archive immediately after completion
8. **Investigation/Analysis Files** → Archive when superseded by permanent docs

---

## File Naming Conventions

### Active Documentation
- Use lowercase with hyphens: `component-name.md`
- Be descriptive: `websocket-management.md` not `ws.md`
- Avoid dates in filenames (use git history for versioning)
- Use singular nouns: `model.md` not `models.md` (unless truly plural)

### Archived Documentation
- Preserve original filenames when archiving
- Add ARCHIVED header but keep original name
- Example: `TASK_1.1_COMPLETION_SUMMARY.md` stays as-is in `/archive/tasks/`

### Special Files
- `README.md` - High-level project overview (root only)
- `DOCS_INDEX.md` - Navigation hub for all documentation
- `MIGRATION_LOG.md` - Log of documentation operations
- `DOCS_MAINTENANCE_RULES.md` - This file

---

## Archiving Procedures

### When to Archive

Archive a document when:
1. It describes a completed task (TASK_*.md files)
2. It's an investigation that led to a permanent solution
3. It's superseded by consolidated documentation
4. It's a temporary analysis or fix document
5. It contains outdated information but has historical value

**Never delete documentation** - always archive it.

### How to Archive

**Step 1: Add ARCHIVED Header**
```markdown
# STATUS: ARCHIVED

**Archived Date:** YYYY-MM-DD
**Reason:** [Brief reason for archiving]
**Current Documentation:** [Link to current doc that supersedes this]

---

[Original content follows...]
```

**Step 2: Move to Appropriate Archive Directory**
- Task files → `/archive/tasks/`
- Analyses → `/archive/analyses/`
- Fixes → `/archive/fixes/`

**Step 3: Log the Operation**
Add entry to `MIGRATION_LOG.md`:
```markdown
### Archive Operation
- **Operation:** ARCHIVE
- **Timestamp:** YYYY-MM-DD HH:MM
- **Source:** [original path]
- **Destination:** [archive path]
- **Reason:** [reason for archiving]
- **Current Doc:** [link to replacement]
```

---

## Conflict Resolution Guidelines

### Priority Order (Highest to Lowest)

1. **Source Code** - The actual implementation is the ultimate truth
2. **Recent Active Documentation** - Documentation in `/docs/` updated within 30 days
3. **Design Documents** - Specifications and design docs in `.kiro/specs/`
4. **Archived Documentation** - Historical documents in `/archive/`

### Conflict Resolution Process

**When documentation conflicts with code:**
1. Verify the code behavior (read source, check tests)
2. Update documentation to match code
3. Mark old documentation as outdated or archive it
4. Log the conflict resolution in MIGRATION_LOG.md

**When multiple documents conflict:**
1. Check file dates (prefer newer)
2. Check location (prefer `/docs/` over `/archive/`)
3. Verify against source code
4. If uncertain, mark as "UNKNOWN / TO CLARIFY: [reason]"
5. Flag for user review

**When uncertain:**
- Add note: `**TO CLARIFY:** [description of uncertainty]`
- Reference conflicting sources
- Flag for manual review
- Do not guess or assume

### Conflict Logging

Always log conflict resolutions:
```markdown
### Conflict Resolution
- **Timestamp:** YYYY-MM-DD HH:MM
- **Conflicting Files:** [file1, file2, ...]
- **Topic:** [what was conflicting]
- **Resolution:** [CODE_TRUTH | NEWEST_DOC | MANUAL]
- **Decision:** [what was decided]
- **Rationale:** [why this decision was made]
```

---

## RAG Indexing Priorities

The Supervisor RAG system should prioritize documents in this order:

### Priority 1: Core Technical Documentation (Index First)
- `/docs/project/architecture.md` - System architecture
- `/docs/domain/model.md` - Domain models and objects
- `/docs/implementation/components.md` - Component details
- `/docs/implementation/lifecycle.md` - Lifecycle management

**Rationale:** These documents provide the foundational understanding of the system.

### Priority 2: Operational Documentation (Index Second)
- `/docs/implementation/interactions.md` - Component interactions
- `/docs/operations/errors-and-recovery.md` - Error handling
- `/docs/operations/security.md` - Security considerations
- `/docs/domain/state-machine.md` - State transitions

**Rationale:** These documents help with debugging and operational issues.

### Priority 3: Supporting Documentation (Index Third)
- `/docs/testing/` - All test documentation
- `/docs/operations/troubleshooting.md` - Troubleshooting guides
- `/docs/guides/` - User guides and tutorials
- `README.md` - High-level overview

**Rationale:** These documents provide context and guidance but are less critical for core development.

### Excluded from Indexing
- `/archive/**` - Historical documents (exclude entirely)
- `MIGRATION_LOG.md` - Operational log (not needed for RAG)
- Build artifacts and temporary files

---

## Documentation Quality Standards

### Required Elements for Component Documentation

Every component document must include:
1. **Role** - Single sentence describing responsibility
2. **Main Fields** - Types, descriptions, invariants
3. **Main Methods** - Full documentation (see template below)
4. **Relationships** - Dependencies and dependents
5. **Lifecycle** - Creation, usage, destruction
6. **Testability** - How to test, edge cases
7. **Code References** - File paths and line numbers

### Method Documentation Template

```markdown
#### `methodName(param1: Type, param2: Type): ReturnType`
**Role:** [What this method does]
**Preconditions:** [What must be true before calling]
**Parameters:**
- `param1`: [description, validation rules]
- `param2`: [description, validation rules]
**Returns:** [description of return value]
**Postconditions:** [What is guaranteed after execution]
**Side-effects:** [State changes, I/O, etc.]
**Errors:** [Possible exceptions/errors]
**Code Reference:** `path/to/file.kt:123`
```

### Code Reference Format

Always use this format for code references:
- `VoiceClientManager.kt:45` - Specific line
- `VoiceClientManager.kt:45-67` - Line range
- `VoiceClientManager.kt` - Entire file

Verify all code references are valid before committing documentation.

---

## Logging Requirements

### When to Log

Log every documentation operation:
- Creating new documents
- Moving/archiving documents
- Consolidating multiple documents
- Resolving conflicts
- Updating existing documents
- Completing phases of work

### Log Entry Format

```markdown
### [Operation Type]
- **Operation:** [CREATE | MOVE | ARCHIVE | CONSOLIDATE | UPDATE]
- **Timestamp:** YYYY-MM-DD HH:MM
- **Source(s):** [source file(s)]
- **Destination:** [destination file]
- **Reason:** [why this operation was performed]
- **Conflicts Resolved:** [any conflicts, or "None"]
- **Status:** [SUCCESS | FAILED | PARTIAL]
```

### Phase Completion Logging

At the end of each phase:
```markdown
## Phase [N] Complete
**Date:** YYYY-MM-DD
**Summary:**
- Files Created: [count]
- Files Moved: [count]
- Files Consolidated: [count]
- Conflicts Resolved: [count]
- Operations Logged: [count]
**Status:** COMPLETE
**User Approval:** [PENDING | APPROVED | REJECTED]
```

---

## Hook Suggestions for Automation

Consider creating agent hooks for:

1. **On File Save Hook** - When saving markdown files in `/docs/`:
   - Verify code references are valid
   - Check for broken internal links
   - Ensure proper formatting

2. **On Commit Hook** - Before committing:
   - Verify all documentation changes are logged
   - Check that archived files have ARCHIVED headers
   - Validate documentation structure

3. **Weekly Maintenance Hook** - Scheduled:
   - Check for outdated documentation (>90 days)
   - Verify code references still valid
   - Generate documentation coverage report

4. **On Code Change Hook** - When source code changes:
   - Flag related documentation for review
   - Check if component documentation needs updates
   - Verify method signatures still match docs

---

## Maintenance Workflow

### Adding New Documentation

1. Determine correct directory based on content type
2. Create file with appropriate naming convention
3. Follow quality standards template
4. Add code references where applicable
5. Log creation in MIGRATION_LOG.md
6. Update DOCS_INDEX.md if major document

### Updating Existing Documentation

1. Verify changes against source code
2. Update content
3. Update "Last Updated" date if present
4. Verify code references still valid
5. Log update in MIGRATION_LOG.md

### Archiving Documentation

1. Follow archiving procedures (see above)
2. Add ARCHIVED header with link to current doc
3. Move to appropriate archive directory
4. Log operation
5. Update any references in active docs

---

## Emergency Procedures

### If Documentation Structure Breaks

1. Stop all operations immediately
2. Check MIGRATION_LOG.md for last successful operation
3. Use git to rollback if needed: `git reset --hard [commit-hash]`
4. Report issue to user
5. Await instructions before proceeding

### If Conflicts Cannot Be Resolved

1. Mark conflict as "MANUAL REVIEW REQUIRED"
2. Document both versions in conflict
3. Add note explaining the conflict
4. Flag for user attention
5. Do not proceed with automated resolution

---

## Review and Updates

This steering file should be reviewed:
- After completing major documentation restructuring
- When documentation structure changes
- When new documentation patterns emerge
- At least quarterly

**Last Updated:** 2025-12-01
**Version:** 1.0

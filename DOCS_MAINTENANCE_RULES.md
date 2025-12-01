# Documentation Maintenance Rules

**Version:** 1.0  
**Last Updated:** 2025-12-01  
**Status:** ACTIVE

This document defines the rules and procedures for maintaining the project's documentation structure. All developers and agents working with documentation must follow these guidelines.

---

## Table of Contents

1. [Where New Documents Should Go](#where-new-documents-should-go)
2. [How to Update Existing Documents](#how-to-update-existing-documents)
3. [Conflict Resolution Procedures](#conflict-resolution-procedures)
4. [Logging Requirements](#logging-requirements)
5. [Hook Suggestions for Automation](#hook-suggestions-for-automation)
6. [RAG Indexing Guidelines](#rag-indexing-guidelines)

---

## Where New Documents Should Go

### Decision Tree

When creating new documentation, follow this decision tree:

```
Is it about requirements or high-level architecture?
├─ YES → /docs/project/
│
Is it about domain models or state machines?
├─ YES → /docs/domain/
│
Is it about component implementation details?
├─ YES → /docs/implementation/
│
Is it about operations (security, errors, monitoring)?
├─ YES → /docs/operations/
│
Is it about testing strategy or results?
├─ YES → /docs/testing/
│
Is it a user guide or tutorial?
├─ YES → /docs/guides/
│
Is it a temporary task or analysis file?
└─ YES → Archive immediately after completion to /archive/
```

### Directory Guidelines

#### `/docs/project/` - Project-Level Documentation
**Place here:**
- Requirements documents (functional, technical, security)
- Architecture overviews and diagrams
- Architecture Decision Records (ADRs)
- High-level design documents
- Project roadmaps and planning

**Examples:**
- `requirements.md` - System requirements
- `architecture.md` - System architecture
- `decisions.md` - ADRs and design rationale

#### `/docs/domain/` - Domain Models and Business Logic
**Place here:**
- Domain object definitions
- State machine diagrams
- Business logic documentation
- Data model relationships
- Event definitions

**Examples:**
- `model.md` - Core domain objects
- `state-machine.md` - State transitions
- `events.md` - Event definitions

#### `/docs/implementation/` - Technical Implementation Details
**Place here:**
- Component documentation with method signatures
- Interaction sequences and data flows
- Lifecycle management patterns
- Integration guides
- API documentation

**Examples:**
- `components.md` - Detailed component docs
- `interactions.md` - Component interactions
- `lifecycle.md` - Lifecycle management
- `audio-pipeline.md` - Audio implementation
- `websocket-management.md` - WebSocket details

#### `/docs/operations/` - Operational Documentation
**Place here:**
- Security guidelines and audit reports
- Error handling procedures
- Monitoring and logging strategies
- Troubleshooting guides
- Performance optimization

**Examples:**
- `security.md` - Security practices
- `errors-and-recovery.md` - Error handling
- `troubleshooting.md` - Common issues
- `monitoring.md` - Monitoring setup

#### `/docs/testing/` - Test Documentation
**Place here:**
- Test strategies and plans
- Test scenarios and cases
- Test results and reports
- Testing guidelines
- Coverage reports

**Examples:**
- `test-strategy.md` - Testing approach
- `test-results.md` - Test outcomes
- `test-scenarios.md` - Test cases

#### `/docs/guides/` - User Guides and Tutorials
**Place here:**
- Quick start guides
- Setup instructions
- Configuration guides
- User tutorials
- How-to documents

**Examples:**
- `quick-start.md` - Getting started
- `picovoice-setup.md` - Wake word setup
- `deployment.md` - Deployment guide

#### `/archive/` - Historical Documentation
**Place here:**
- Completed task files (TASK_*.md)
- Historical analyses and investigations
- Superseded bug fixes
- Outdated documentation with historical value

**Subdirectories:**
- `/archive/tasks/` - Completed tasks
- `/archive/analyses/` - Historical analyses
- `/archive/fixes/` - Historical bug fixes

---

## How to Update Existing Documents

### Update Workflow

1. **Identify the Document**
   - Locate the document in `/docs/` hierarchy
   - Verify it's the correct document to update

2. **Verify Against Source Code**
   - Check that updates match current code
   - Use code as source of truth
   - Mark discrepancies for investigation

3. **Make Updates**
   - Update content with accurate information
   - Add code references where applicable
   - Update "Last Updated" date if present
   - Maintain consistent formatting

4. **Verify Code References**
   - Check all file:line references are valid
   - Update references if code has moved
   - Mark invalid references as "TO CLARIFY"

5. **Log the Update**
   - Add entry to MIGRATION_LOG.md
   - Include what was changed and why
   - Reference source code if applicable

6. **Update Cross-References**
   - Check if other documents reference this one
   - Update links if document structure changed
   - Verify all internal links work

### Update Template

When updating a document, add this entry to MIGRATION_LOG.md:

```markdown
### Document Update
- **Operation:** UPDATE
- **Timestamp:** YYYY-MM-DD HH:MM
- **File:** [path to file]
- **Changes:** [brief description of changes]
- **Reason:** [why the update was needed]
- **Code References Updated:** [count or "None"]
- **Verified Against Code:** [YES/NO]
- **Status:** SUCCESS
```

### Best Practices

**DO:**
- ✅ Verify changes against source code
- ✅ Add code references for technical claims
- ✅ Update cross-references in other documents
- ✅ Log all significant updates
- ✅ Use consistent formatting
- ✅ Mark uncertainties as "TO CLARIFY"

**DON'T:**
- ❌ Make assumptions without verifying code
- ❌ Delete information without archiving
- ❌ Update without logging changes
- ❌ Break existing links without updating references
- ❌ Mix multiple unrelated updates in one change

---

## Conflict Resolution Procedures

### Priority Order

When documentation conflicts with other sources, resolve using this priority order:

1. **Source Code** (Highest Priority)
   - The actual implementation is the ultimate truth
   - Always verify documentation against code
   - Update documentation to match code behavior

2. **Recent Active Documentation** (High Priority)
   - Documentation in `/docs/` updated within 30 days
   - Assume recent docs reflect current understanding
   - Verify against code before trusting

3. **Design Documents** (Medium Priority)
   - Specifications in `.kiro/specs/`
   - May represent intended behavior vs actual
   - Verify against code to determine if implemented

4. **Archived Documentation** (Low Priority)
   - Historical documents in `/archive/`
   - May be outdated or superseded
   - Use for historical context only

### Conflict Resolution Process

#### Step 1: Identify the Conflict
- Document what information conflicts
- List all sources with conflicting information
- Note the specific claims that differ

#### Step 2: Verify Against Source Code
```bash
# Find relevant code
grep -r "methodName" gemini-multimodal-websocket-demo/src/

# Check specific file
cat gemini-multimodal-websocket-demo/src/main/java/.../ClassName.kt
```

#### Step 3: Determine Resolution
- If code matches one source: Use that source
- If code differs from all sources: Update all to match code
- If code is ambiguous: Mark as "TO CLARIFY" and flag for review

#### Step 4: Update Documentation
- Update all conflicting documents
- Add note explaining the conflict resolution
- Reference the source code as evidence

#### Step 5: Log the Resolution
Add entry to MIGRATION_LOG.md:

```markdown
### Conflict Resolution
- **Timestamp:** YYYY-MM-DD HH:MM
- **Conflicting Files:** [file1, file2, ...]
- **Topic:** [what was conflicting]
- **Resolution:** [CODE_TRUTH | NEWEST_DOC | MANUAL]
- **Decision:** [what was decided]
- **Rationale:** [why this decision was made]
- **Code Reference:** [file:line if applicable]
- **Status:** RESOLVED
```

### Handling Uncertainties

When you cannot resolve a conflict:

1. **Mark as "TO CLARIFY"**
   ```markdown
   **TO CLARIFY:** [Description of uncertainty]
   - Source A claims: [claim]
   - Source B claims: [claim]
   - Code shows: [what code shows or "unclear"]
   - Needs: [what's needed to resolve]
   ```

2. **Flag for Manual Review**
   - Add to a "TO CLARIFY" section in the document
   - Log in MIGRATION_LOG.md
   - Notify team for review

3. **Do Not Guess**
   - Never make assumptions without evidence
   - Better to mark as unclear than to document incorrectly
   - Incorrect documentation is worse than no documentation

---

## Logging Requirements

### What to Log

Log every documentation operation in MIGRATION_LOG.md:

- ✅ Creating new documents
- ✅ Moving/archiving documents
- ✅ Consolidating multiple documents
- ✅ Resolving conflicts
- ✅ Updating existing documents (significant changes)
- ✅ Completing phases of work

### Log Entry Format

#### Creating Documents
```markdown
### Document Creation
- **Operation:** CREATE
- **Timestamp:** YYYY-MM-DD HH:MM
- **File:** [path to new file]
- **Purpose:** [why this document was created]
- **Source Files:** [files used as sources, or "None"]
- **Code References:** [count]
- **Status:** SUCCESS
```

#### Moving/Archiving Documents
```markdown
### Archive Operation
- **Operation:** ARCHIVE
- **Timestamp:** YYYY-MM-DD HH:MM
- **Source:** [original path]
- **Destination:** [archive path]
- **Reason:** [why archived]
- **Current Doc:** [link to replacement doc]
- **Status:** SUCCESS
```

#### Consolidating Documents
```markdown
### Consolidation Operation
- **Operation:** CONSOLIDATE
- **Timestamp:** YYYY-MM-DD HH:MM
- **Destination:** [consolidated doc path]
- **Source Files:** [list of source files]
- **Content:** [brief description of content]
- **Conflicts Resolved:** [count or "None"]
- **Status:** SUCCESS
```

#### Updating Documents
```markdown
### Document Update
- **Operation:** UPDATE
- **Timestamp:** YYYY-MM-DD HH:MM
- **File:** [path to file]
- **Changes:** [description of changes]
- **Reason:** [why updated]
- **Code References Updated:** [count or "None"]
- **Status:** SUCCESS
```

#### Resolving Conflicts
```markdown
### Conflict Resolution
- **Operation:** RESOLVE_CONFLICT
- **Timestamp:** YYYY-MM-DD HH:MM
- **Conflicting Files:** [list of files]
- **Topic:** [what conflicted]
- **Resolution:** [CODE_TRUTH | NEWEST_DOC | MANUAL]
- **Decision:** [what was decided]
- **Rationale:** [why]
- **Status:** RESOLVED
```

### Phase Completion Logging

At the end of each major phase:

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

Consider creating agent hooks to automate documentation maintenance:

### 1. On File Save Hook
**Trigger:** When saving markdown files in `/docs/`

**Actions:**
- Verify code references are valid
- Check for broken internal links
- Ensure proper formatting
- Validate document structure

**Implementation:**
```yaml
trigger: on_file_save
pattern: "docs/**/*.md"
action: verify_documentation
```

### 2. On Commit Hook
**Trigger:** Before committing changes

**Actions:**
- Verify all documentation changes are logged
- Check that archived files have ARCHIVED headers
- Validate documentation structure
- Ensure no temporary files in `/docs/`

**Implementation:**
```yaml
trigger: pre_commit
action: validate_documentation_changes
```

### 3. Weekly Maintenance Hook
**Trigger:** Scheduled (weekly)

**Actions:**
- Check for outdated documentation (>90 days)
- Verify code references still valid
- Generate documentation coverage report
- Identify documents needing updates

**Implementation:**
```yaml
trigger: schedule
schedule: "0 0 * * 0"  # Every Sunday at midnight
action: documentation_maintenance
```

### 4. On Code Change Hook
**Trigger:** When source code changes

**Actions:**
- Flag related documentation for review
- Check if component documentation needs updates
- Verify method signatures still match docs
- Generate list of potentially outdated docs

**Implementation:**
```yaml
trigger: on_file_save
pattern: "gemini-multimodal-websocket-demo/src/**/*.kt"
action: flag_related_documentation
```

### 5. Documentation Coverage Hook
**Trigger:** Manual or scheduled

**Actions:**
- Analyze code coverage by documentation
- Identify undocumented components
- Generate coverage report
- Suggest documentation improvements

**Implementation:**
```yaml
trigger: manual
command: "check_documentation_coverage"
action: generate_coverage_report
```

---

## RAG Indexing Guidelines

### Indexing Priorities

Configure the Supervisor RAG system to index documentation in this priority order:

#### Priority 1: Core Technical Documentation
**Index First** - These documents provide foundational understanding:

- `docs/project/architecture.md` - System architecture
- `docs/domain/model.md` - Domain models
- `docs/implementation/components.md` - Component details
- `docs/implementation/lifecycle.md` - Lifecycle management

**Rationale:** Answer "what is this system?" and "how does it work?"

#### Priority 2: Operational Documentation
**Index Second** - These documents help with debugging and operations:

- `docs/implementation/interactions.md` - Component interactions
- `docs/domain/state-machine.md` - State transitions
- `docs/operations/errors-and-recovery.md` - Error handling
- `docs/operations/security.md` - Security practices

**Rationale:** Answer "why does it work this way?" and "what happens when X occurs?"

#### Priority 3: Supporting Documentation
**Index Third** - These documents provide context and guidance:

- `docs/testing/` - All test documentation
- `docs/operations/troubleshooting.md` - Troubleshooting
- `docs/guides/` - User guides
- `README.md` - High-level overview

**Rationale:** Helpful for onboarding and reference but less critical for core development.

### Exclusions

**Exclude from RAG indexing:**
- `/archive/**` - Historical documents (archived for reference only)
- `MIGRATION_LOG.md` - Operational log (not needed for development)
- `DOCS_MAINTENANCE_RULES.md` - Maintenance procedures (not code-related)
- Build artifacts and temporary files

### Indexing Configuration

**Recommended RAG configuration:**

```yaml
indexing:
  priority_1:
    - docs/project/architecture.md
    - docs/domain/model.md
    - docs/implementation/components.md
    - docs/implementation/lifecycle.md
  
  priority_2:
    - docs/implementation/interactions.md
    - docs/domain/state-machine.md
    - docs/operations/errors-and-recovery.md
    - docs/operations/security.md
  
  priority_3:
    - docs/testing/**
    - docs/operations/troubleshooting.md
    - docs/guides/**
    - README.md
  
  exclude:
    - archive/**
    - MIGRATION_LOG.md
    - DOCS_MAINTENANCE_RULES.md
    - "**/*.log"
    - "**/*.tmp"
```

### Indexing Best Practices

**DO:**
- ✅ Index active documentation in `/docs/`
- ✅ Prioritize core technical documents
- ✅ Update index when documents change significantly
- ✅ Exclude archived and temporary files
- ✅ Configure priority levels for retrieval

**DON'T:**
- ❌ Index archived documentation
- ❌ Index operational logs
- ❌ Index temporary or work-in-progress files
- ❌ Index build artifacts
- ❌ Treat all documents with equal priority

---

## Emergency Procedures

### If Documentation Structure Breaks

1. **Stop All Operations Immediately**
   - Do not make further changes
   - Do not attempt to fix automatically

2. **Check MIGRATION_LOG.md**
   - Find last successful operation
   - Identify what operation failed
   - Note any error messages

3. **Use Git to Rollback**
   ```bash
   # View recent commits
   git log --oneline -10
   
   # Rollback to last good state
   git reset --hard [commit-hash]
   ```

4. **Report Issue**
   - Document what went wrong
   - Include error messages
   - Note what operation was attempted

5. **Await Instructions**
   - Do not proceed with automated fixes
   - Wait for manual review
   - Provide all relevant information

### If Conflicts Cannot Be Resolved

1. **Mark Conflict as "MANUAL REVIEW REQUIRED"**
   ```markdown
   **MANUAL REVIEW REQUIRED**
   - Conflict: [description]
   - Source A: [claim]
   - Source B: [claim]
   - Code: [what code shows]
   - Unable to resolve automatically
   ```

2. **Document Both Versions**
   - Include all conflicting information
   - Add context for each version
   - Reference sources

3. **Add Note Explaining Conflict**
   - Why it couldn't be resolved
   - What information is needed
   - Who should review

4. **Flag for User Attention**
   - Add to "TO CLARIFY" section
   - Log in MIGRATION_LOG.md
   - Notify team

5. **Do Not Proceed**
   - Do not guess or assume
   - Do not make arbitrary choices
   - Wait for manual resolution

---

## Review and Updates

### Review Schedule

This document should be reviewed:
- ✅ After completing major documentation restructuring
- ✅ When documentation structure changes
- ✅ When new documentation patterns emerge
- ✅ At least quarterly (every 3 months)

### Update Process

When updating this document:
1. Increment version number
2. Update "Last Updated" date
3. Log changes in MIGRATION_LOG.md
4. Notify team of changes
5. Update any related documentation

### Version History

- **v1.0** (2025-12-01) - Initial version after Phase 5 completion

---

## Questions and Support

For questions about documentation maintenance:
1. Check this document first
2. Review [DOCS_INDEX.md](DOCS_INDEX.md) for structure
3. Check [MIGRATION_LOG.md](MIGRATION_LOG.md) for examples
4. Contact the development team

---

**Remember:** Good documentation is maintained documentation. Follow these rules to keep our documentation accurate, organized, and useful.

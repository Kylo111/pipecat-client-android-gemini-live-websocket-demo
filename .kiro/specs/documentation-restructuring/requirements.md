# Requirements Document - Documentation Restructuring

## Introduction

This specification defines the requirements for restructuring and organizing the project's markdown documentation. The current state has 82 markdown files in the root directory (~20,329 lines) with no clear structure, mixing source documentation, temporary task files, and outdated analyses. This makes it difficult for the Supervisor RAG system to effectively index and retrieve relevant information.

The goal is to create a professional, well-organized documentation structure that serves as a comprehensive technical reference for architects and the Supervisor agent.

## Glossary

- **Supervisor (RAG)**: The AI agent that indexes and retrieves documentation to assist with development tasks
- **Source Documentation**: Active, current documentation that describes the system
- **Archived Documentation**: Historical documents preserved for reference but marked as outdated
- **Guard Rails**: Safety rules that prevent accidental data loss during migration
- **Steering Rules**: Guidelines for future agents on how to maintain documentation
- **MIGRATION_LOG.md**: Log file tracking all file movements and consolidations during restructuring

## Requirements

### Requirement 1: Documentation Analysis and Planning

**User Story:** As a project architect, I want a clear analysis of existing documentation, so that I understand what needs to be reorganized.

#### Acceptance Criteria

1. WHEN analyzing existing documentation THEN the system SHALL categorize all 82 markdown files into source, temporary, and archived categories
2. WHEN creating the restructuring plan THEN the system SHALL define a clear directory structure with /docs/ and /archive/ hierarchies
3. WHEN mapping files THEN the system SHALL specify the destination for each existing file
4. WHEN identifying conflicts THEN the system SHALL prioritize source code as the source of truth over documentation dates

### Requirement 2: Safe Migration with Guard Rails

**User Story:** As a project maintainer, I want strict safety rules during migration, so that no information is lost accidentally.

#### Acceptance Criteria

1. WHEN moving any file THEN the system SHALL never delete without moving to /archive/
2. WHEN archiving a file THEN the system SHALL add an ARCHIVED header with a link to the current document
3. WHEN encountering conflicting information THEN the system SHALL compare with source code and mark outdated content
4. WHEN performing any operation THEN the system SHALL log it in MIGRATION_LOG.md with source, destination, and reason
5. WHEN completing each phase THEN the system SHALL stop and wait for user approval before proceeding

### Requirement 3: Directory Structure Creation

**User Story:** As a documentation consumer, I want a logical directory structure, so that I can easily find relevant information.

#### Acceptance Criteria

1. WHEN creating the structure THEN the system SHALL create /docs/ with subdirectories: project, domain, implementation, operations, testing, guides
2. WHEN creating the structure THEN the system SHALL create /archive/ with subdirectories: tasks, analyses, fixes
3. WHEN organizing files THEN the system SHALL place active documentation in /docs/ and historical files in /archive/
4. WHEN structuring /docs/project/ THEN the system SHALL include requirements.md, architecture.md, decisions.md
5. WHEN structuring /docs/domain/ THEN the system SHALL include model.md, state-machine.md, events.md

### Requirement 4: File Archiving

**User Story:** As a developer, I want outdated task files archived properly, so that they don't clutter the workspace but remain accessible.

#### Acceptance Criteria

1. WHEN archiving TASK_*.md files THEN the system SHALL move them to /archive/tasks/
2. WHEN archiving analysis files (ANALIZA_*.md, NAPRAWA_*.md) THEN the system SHALL move them to /archive/analyses/
3. WHEN archiving fix documents THEN the system SHALL move them to /archive/fixes/
4. WHEN archiving any file THEN the system SHALL prepend "STATUS: ARCHIVED - See [link]" header
5. WHEN archiving THEN the system SHALL preserve original file content below the header

### Requirement 5: Documentation Consolidation

**User Story:** As a technical writer, I want scattered information consolidated into comprehensive documents, so that knowledge is centralized.

#### Acceptance Criteria

1. WHEN consolidating THEN the system SHALL merge related documents into single authoritative sources
2. WHEN creating requirements.md THEN the system SHALL extract requirements from README.md and audit documents
3. WHEN creating architecture.md THEN the system SHALL consolidate architecture information from multiple sources
4. WHEN creating components.md THEN the system SHALL document all major classes with methods, states, and dependencies
5. WHEN consolidating THEN the system SHALL add source references showing which files contributed content

### Requirement 6: Technical Documentation Quality

**User Story:** As a Supervisor agent, I want detailed technical documentation, so that I can accurately understand system behavior.

#### Acceptance Criteria

1. WHEN documenting methods THEN the system SHALL include role, parameters, return value, preconditions, postconditions, side-effects, and errors
2. WHEN documenting objects THEN the system SHALL include role, fields, methods, relationships, lifecycle, and testability notes
3. WHEN documenting relationships THEN the system SHALL specify direction, type (composition/aggregation/observation), cardinality, and impact on testability
4. WHEN uncertain about information THEN the system SHALL mark it as "UNKNOWN / TO CLARIFY: [reason]"
5. WHEN adding information THEN the system SHALL include references to source code files and line numbers

### Requirement 7: Steering Rules for Future Maintenance

**User Story:** As a future developer, I want clear rules for maintaining documentation, so that the structure remains organized.

#### Acceptance Criteria

1. WHEN creating steering rules THEN the system SHALL create .kiro/steering/documentation-rules.md
2. WHEN defining rules THEN the system SHALL specify where new documents should be placed
3. WHEN defining rules THEN the system SHALL establish conflict resolution procedures (code > documentation)
4. WHEN defining rules THEN the system SHALL require logging all documentation changes
5. WHEN defining rules THEN the system SHALL specify RAG indexing priorities

### Requirement 8: Migration Logging and Verification

**User Story:** As a project auditor, I want complete logs of all migration operations, so that I can verify correctness.

#### Acceptance Criteria

1. WHEN starting migration THEN the system SHALL create MIGRATION_LOG.md
2. WHEN moving a file THEN the system SHALL log source path, destination path, reason, and any conflicts resolved
3. WHEN consolidating files THEN the system SHALL log which sources contributed to which output
4. WHEN completing a phase THEN the system SHALL log phase completion with summary statistics
5. WHEN finishing migration THEN the system SHALL provide a final summary of all operations

### Requirement 9: RAG Indexing Configuration

**User Story:** As a Supervisor agent, I want clear indexing priorities, so that I retrieve the most relevant documentation first.

#### Acceptance Criteria

1. WHEN configuring RAG THEN the system SHALL designate Priority 1 documents (architecture.md, model.md, components.md, lifecycle.md)
2. WHEN configuring RAG THEN the system SHALL designate Priority 2 documents (interactions.md, errors-and-recovery.md)
3. WHEN configuring RAG THEN the system SHALL designate Priority 3 documents (troubleshooting.md, guides, README.md as optional)
4. WHEN configuring RAG THEN the system SHALL exclude /archive/* from indexing
5. WHEN documenting priorities THEN the system SHALL explain the rationale for each priority level

### Requirement 10: Backup and Safety

**User Story:** As a project maintainer, I want a backup before migration starts, so that I can rollback if needed.

#### Acceptance Criteria

1. WHEN starting migration THEN the system SHALL create a git commit with message "Pre-documentation-restructuring"
2. WHEN creating backup THEN the system SHALL verify the commit was successful before proceeding
3. WHEN encountering errors THEN the system SHALL stop and report the issue without continuing
4. WHEN user requests rollback THEN the system SHALL provide instructions for reverting to the backup commit
5. WHEN completing migration THEN the system SHALL create a final commit with message "Post-documentation-restructuring"

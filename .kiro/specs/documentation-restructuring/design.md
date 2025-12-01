# Design Document - Documentation Restructuring

## Overview

This design describes the systematic approach to restructuring 82 markdown files (~20,329 lines) from the project root into an organized documentation hierarchy. The system will safely migrate files while preserving all information, consolidate scattered knowledge into comprehensive technical documents, and establish maintenance rules for future documentation work.

## Architecture

### High-Level Structure

```
Project Root
├── README.md (simplified, high-level)
├── DOCS_INDEX.md (navigation hub)
├── DOC_RESTRUCTURING_PLAN.md (this plan)
├── MIGRATION_LOG.md (operation log)
│
├── /docs/ (active documentation)
│   ├── /project/ (requirements, architecture, decisions)
│   ├── /domain/ (model, state machines, events)
│   ├── /implementation/ (components, interactions, details)
│   ├── /operations/ (errors, monitoring, security, performance)
│   ├── /testing/ (strategy, scenarios, results)
│   └── /guides/ (user guides, setup instructions)
│
├── /archive/ (historical documents)
│   ├── /tasks/ (TASK_*.md files)
│   ├── /analyses/ (ANALIZA_*.md, NAPRAWA_*.md)
│   └── /fixes/ (FIX_*.md, AUDIO_*_FIX.md)
│
└── /.kiro/steering/
    └── documentation-rules.md (maintenance guidelines)
```

### Migration Pipeline

```
[Existing Files] → [Analysis] → [Categorization] → [Safe Migration] → [Consolidation] → [Verification]
       ↓              ↓              ↓                    ↓                  ↓              ↓
    82 files    Source/Temp/    Archive/Docs/      Move with         Merge related    Check links
                 Archived      Consolidate        logging           content          & references
```

## Components and Interfaces

### 1. File Analyzer

**Responsibility:** Categorize existing markdown files

**Methods:**
- `analyzeFile(path: string): FileCategory`
  - Input: File path
  - Output: Category (SOURCE, TEMPORARY, ARCHIVED)
  - Logic: Check filename patterns (TASK_*, ANALIZA_*, etc.), content, and dates

- `detectConflicts(files: File[]): Conflict[]`
  - Input: List of files
  - Output: List of conflicting information
  - Logic: Compare content across files, identify contradictions

### 2. Migration Manager

**Responsibility:** Safely move and archive files

**Methods:**
- `moveToArchive(sourcePath: string, category: string): Result`
  - Preconditions: File exists, MIGRATION_LOG.md created
  - Actions: Move file, add ARCHIVED header, log operation
  - Postconditions: File in /archive/, header added, logged
  - Errors: FileNotFound, PermissionDenied

- `addArchivedHeader(filePath: string, currentDocLink: string): void`
  - Preconditions: File exists in /archive/
  - Actions: Prepend "STATUS: ARCHIVED - See [link]" to file
  - Side-effects: Modifies file content

### 3. Documentation Consolidator

**Responsibility:** Merge related documents into comprehensive sources

**Methods:**
- `consolidateDocuments(sources: File[], outputPath: string): Result`
  - Preconditions: All source files exist and are readable
  - Actions: Extract relevant content, merge, add source references
  - Postconditions: New consolidated document created
  - Errors: ConflictingInformation, MissingSource

- `resolveConflict(conflict: Conflict, sourceCode: Code): Resolution`
  - Preconditions: Source code accessible
  - Logic: Compare documentation with code, prioritize code truth
  - Postconditions: Conflict marked as resolved in log

### 4. Code Verifier

**Responsibility:** Verify documentation against source code

**Methods:**
- `verifyAgainstCode(docContent: string, codeFiles: File[]): VerificationResult`
  - Input: Documentation content, relevant code files
  - Output: List of discrepancies or confirmation
  - Logic: Parse documentation claims, check against actual code

- `extractCodeReferences(docContent: string): Reference[]`
  - Input: Documentation content
  - Output: List of code references (file:line)
  - Logic: Find method names, class names, extract locations

### 5. Migration Logger

**Responsibility:** Track all operations in MIGRATION_LOG.md

**Methods:**
- `logOperation(operation: Operation): void`
  - Input: Operation details (type, source, dest, reason, conflicts)
  - Actions: Append to MIGRATION_LOG.md with timestamp
  - Format: `## [Date] [Operation]\n- Source: ...\n- Dest: ...\n- Reason: ...`

- `logPhaseCompletion(phase: string, stats: Statistics): void`
  - Input: Phase name, statistics (files moved, consolidated, etc.)
  - Actions: Write phase summary to log

## Data Models

### FileCategory
```typescript
enum FileCategory {
  SOURCE,      // Active documentation (README, architecture docs)
  TEMPORARY,   // Task files, work logs (TASK_*, ANALIZA_*)
  ARCHIVED     // Outdated analyses, old fixes
}
```

### Operation
```typescript
interface Operation {
  timestamp: Date
  type: 'MOVE' | 'CONSOLIDATE' | 'CREATE' | 'UPDATE'
  sourcePaths: string[]
  destinationPath: string
  reason: string
  conflictsResolved: Conflict[]
}
```

### Conflict
```typescript
interface Conflict {
  files: string[]
  description: string
  resolution: 'CODE_TRUTH' | 'NEWEST_DOC' | 'MANUAL'
  resolvedValue: string
}
```

### VerificationResult
```typescript
interface VerificationResult {
  isValid: boolean
  discrepancies: Discrepancy[]
  codeReferences: Reference[]
}

interface Discrepancy {
  docClaim: string
  actualCode: string
  location: string  // file:line
  severity: 'CRITICAL' | 'WARNING' | 'INFO'
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: No Information Loss
*For any* file in the original set, after migration, the file content must exist either in /docs/ (consolidated) or /archive/ (preserved with header)
**Validates: Requirements 2.1, 4.5**

### Property 2: Archive Header Consistency
*For any* file in /archive/, the file must start with "STATUS: ARCHIVED - See [link]" header
**Validates: Requirements 4.4**

### Property 3: Migration Logging Completeness
*For any* file operation (move, consolidate, create), there must exist a corresponding entry in MIGRATION_LOG.md
**Validates: Requirements 8.2, 8.3**

### Property 4: Code Truth Priority
*For any* conflict between documentation and source code, the resolution must match the source code
**Validates: Requirements 1.4, 2.3**

### Property 5: Reference Validity
*For any* code reference in documentation (file:line), the referenced code must exist at that location
**Validates: Requirements 6.5**

### Property 6: Directory Structure Completeness
*For all* required directories (/docs/project/, /docs/domain/, etc.), they must exist after Phase 2
**Validates: Requirements 3.1, 3.2**

### Property 7: Steering Rules Existence
*After* Phase 1 completion, .kiro/steering/documentation-rules.md must exist and contain all required sections
**Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5**

### Property 8: Backup Verification
*Before* any migration operation, a git commit with message "Pre-documentation-restructuring" must exist
**Validates: Requirements 10.1, 10.2**

## Error Handling

### Error Categories

**CRITICAL (stop immediately):**
- Backup commit failed
- MIGRATION_LOG.md cannot be created
- File deletion without archiving attempted
- Source code verification failed critically

**RECOVERABLE (log and continue):**
- Single file move failed (retry or skip)
- Minor conflict in consolidation (mark as UNKNOWN)
- Code reference not found (mark as TO CLARIFY)

**WARNINGS (log only):**
- Outdated information detected
- Missing code reference
- Ambiguous conflict resolution

### Error Recovery Strategies

1. **File Operation Failure:**
   - Log error with full context
   - Skip file and continue
   - Report at phase end for manual review

2. **Conflict Resolution Failure:**
   - Mark conflict as "MANUAL REVIEW REQUIRED"
   - Include both versions in output
   - Flag for user attention

3. **Code Verification Failure:**
   - Mark documentation section as "UNKNOWN / TO CLARIFY"
   - Add note about verification failure
   - Continue with rest of document

## Testing Strategy

### Unit Testing

**File Analyzer Tests:**
- Test categorization of TASK_*.md → TEMPORARY
- Test categorization of README.md → SOURCE
- Test categorization of ANALIZA_*.md → ARCHIVED
- Test conflict detection between contradictory files

**Migration Manager Tests:**
- Test moveToArchive() creates correct directory structure
- Test addArchivedHeader() prepends correct header
- Test logging of operations

**Documentation Consolidator Tests:**
- Test merging of related documents
- Test source reference addition
- Test conflict resolution with code priority

### Integration Testing

**End-to-End Migration Test:**
1. Create test set of markdown files
2. Run full migration pipeline
3. Verify all files accounted for (docs or archive)
4. Verify MIGRATION_LOG.md completeness
5. Verify no information loss

**Conflict Resolution Test:**
1. Create files with conflicting information
2. Create source code with ground truth
3. Run consolidation
4. Verify code truth prevails
5. Verify conflict logged

### Manual Verification

**After Each Phase:**
- User reviews phase summary
- User checks sample of moved/consolidated files
- User verifies MIGRATION_LOG.md entries
- User approves before next phase

## Implementation Notes

### Phase Execution Order

Each phase is a discrete task that must complete before the next begins:

1. **Phase 1: Preparation** - Setup safety infrastructure
2. **Phase 2: Archiving** - Move temporary/outdated files
3. **Phase 3: Consolidation** - Create comprehensive docs
4. **Phase 4: Detailed Documentation** - Add technical depth
5. **Phase 5: Verification** - Final checks and cleanup

### Checkpoint Pattern

After each phase:
```
1. Log phase completion with statistics
2. Generate phase summary for user
3. Wait for user approval
4. If approved: proceed to next phase
5. If rejected: rollback phase and report issues
```

### Conflict Resolution Algorithm

```
function resolveConflict(conflict: Conflict): Resolution {
  // Priority 1: Check source code
  codeValue = extractFromCode(conflict.topic)
  if (codeValue exists) {
    return Resolution(value: codeValue, source: 'CODE_TRUTH')
  }
  
  // Priority 2: Check file dates
  newestFile = conflict.files.sortByDate().first()
  if (newestFile.date < 30 days ago) {
    return Resolution(value: newestFile.content, source: 'NEWEST_DOC')
  }
  
  // Priority 3: Manual review
  return Resolution(value: null, source: 'MANUAL', 
                   note: 'Requires user review')
}
```

### Documentation Quality Template

Every component documentation must follow:

```markdown
## [ComponentName]

### Role
[Single sentence describing responsibility]

### Main Fields
- `fieldName: Type` - [description, invariants]

### Main Methods

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
**Example:**
```kotlin
// Usage example
```

### Relationships
- **Depends on:** [Other components] (composition/aggregation/observation)
- **Used by:** [Other components]

### Lifecycle
1. Creation: [How instances are created]
2. Usage: [Typical usage patterns]
3. Destruction: [Cleanup requirements]

### Testability
- **Mocking:** [How to mock for tests]
- **Edge cases:** [Important edge cases to test]
```

## Performance Considerations

- File operations are I/O bound - process in batches
- Code verification can be cached per file
- MIGRATION_LOG.md should be buffered (write in batches)
- Large file consolidation should stream content

## Security Considerations

- Never delete files without archiving
- Preserve file permissions during moves
- Sanitize file paths to prevent directory traversal
- Validate all user inputs (file paths, confirmations)

## Future Enhancements

- Automated conflict detection using AST parsing
- Integration with git history for better date tracking
- Automated code reference validation in CI/CD
- Documentation coverage metrics (% of code documented)

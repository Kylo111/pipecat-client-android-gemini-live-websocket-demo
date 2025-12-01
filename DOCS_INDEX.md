# Documentation Index

**Last Updated:** 2025-12-01  
**Version:** 1.0

Welcome to the comprehensive documentation for the Gemini Multimodal WebSocket Demo Android application. This index provides quick navigation to all technical documentation organized by topic and priority.

---

## Quick Navigation

### 🚀 Getting Started
- [Quick Start Guide](docs/guides/quick-start.md) - Installation, building, and first run
- [Picovoice Setup Guide](docs/guides/picovoice-setup.md) - Wake word detection configuration
- [README](README.md) - High-level project overview

### 🏗️ Architecture & Design
- [Architecture Overview](docs/project/architecture.md) - System architecture and components
- [Requirements](docs/project/requirements.md) - Functional and technical requirements
- [Architecture Decisions](docs/project/decisions.md) - ADRs and design rationale

### 🧩 Domain & Models
- [Domain Model](docs/domain/model.md) - Core domain objects and relationships
- [State Machines](docs/domain/state-machine.md) - State transitions and lifecycle

### 💻 Implementation Details
- [Components](docs/implementation/components.md) - Detailed component documentation
- [Interactions](docs/implementation/interactions.md) - Component interaction sequences
- [Lifecycle Management](docs/implementation/lifecycle.md) - Activity and service lifecycle

### 🔧 Operations
- Coming soon: Security, Error Handling, Troubleshooting

### 🧪 Testing
- Coming soon: Test Strategy, Test Results

---

## Documentation Structure

```
docs/
├── project/          # High-level project documentation
│   ├── requirements.md      # Functional and technical requirements
│   ├── architecture.md      # System architecture overview
│   └── decisions.md         # Architecture Decision Records (ADRs)
│
├── domain/           # Domain models and business logic
│   ├── model.md             # Core domain objects and relationships
│   └── state-machine.md     # State machines and lifecycle states
│
├── implementation/   # Technical implementation details
│   ├── components.md        # Detailed component documentation
│   ├── interactions.md      # Component interaction sequences
│   └── lifecycle.md         # Activity and service lifecycle management
│
├── operations/       # Operational documentation
│   └── (coming soon)        # Security, errors, troubleshooting
│
├── testing/          # Test documentation
│   └── (coming soon)        # Test strategy and results
│
└── guides/           # User guides and tutorials
    ├── quick-start.md       # Getting started guide
    └── picovoice-setup.md   # Wake word setup guide
```

---

## RAG Indexing Priorities

The Supervisor RAG system indexes documentation in the following priority order to optimize retrieval:

### Priority 1: Core Technical Documentation (Index First)
These documents provide foundational understanding of the system and should be indexed first for maximum relevance.

- **[Architecture Overview](docs/project/architecture.md)** - System architecture, components, and data flows
- **[Domain Model](docs/domain/model.md)** - Core domain objects, relationships, and data structures
- **[Components](docs/implementation/components.md)** - Detailed component documentation with methods and interfaces
- **[Lifecycle Management](docs/implementation/lifecycle.md)** - Activity and service lifecycle patterns

**Rationale:** These documents answer fundamental questions about "what is this system?" and "how does it work?" They provide the context needed for understanding all other documentation.

### Priority 2: Operational Documentation (Index Second)
These documents help with debugging, troubleshooting, and understanding system behavior.

- **[Interactions](docs/implementation/interactions.md)** - Component interaction sequences and data flows
- **[State Machines](docs/domain/state-machine.md)** - State transitions and lifecycle states
- **[Requirements](docs/project/requirements.md)** - Functional and technical requirements
- **[Architecture Decisions](docs/project/decisions.md)** - ADRs and design rationale

**Rationale:** These documents help answer "why does it work this way?" and "what happens when X occurs?" They're essential for debugging and understanding system behavior.

### Priority 3: Supporting Documentation (Index Third)
These documents provide context, guidance, and reference information.

- **[Quick Start Guide](docs/guides/quick-start.md)** - Installation and setup
- **[Picovoice Setup Guide](docs/guides/picovoice-setup.md)** - Wake word configuration
- **[README](README.md)** - High-level overview

**Rationale:** These documents are helpful for onboarding and reference but are less critical for core development tasks.

### Excluded from Indexing
- `/archive/**` - Historical documents (archived for reference only)
- `MIGRATION_LOG.md` - Operational log (not needed for development)
- Build artifacts and temporary files

---

## Document Purpose Descriptions

### Project Documentation (`docs/project/`)

**requirements.md**
- **Purpose:** Define what the system must do
- **Audience:** Architects, developers, product managers
- **Content:** Functional requirements (FR-1 to FR-7), technical requirements (TR-1 to TR-8), security requirements, compliance requirements, non-functional requirements
- **Use Cases:** Understanding system capabilities, validating implementations, planning new features

**architecture.md**
- **Purpose:** Describe how the system is structured
- **Audience:** Architects, senior developers
- **Content:** High-level architecture, core components, data flows, resource management, security architecture, network architecture
- **Use Cases:** Understanding system design, planning refactoring, onboarding new developers

**decisions.md**
- **Purpose:** Document architectural decisions and rationale
- **Audience:** Architects, technical leads
- **Content:** 13 Architecture Decision Records (ADRs), decision log, superseded decisions
- **Use Cases:** Understanding why certain approaches were chosen, avoiding repeated discussions, learning from past decisions

### Domain Documentation (`docs/domain/`)

**model.md**
- **Purpose:** Define core domain objects and their relationships
- **Audience:** Developers, architects
- **Content:** VoiceClientManager, SessionManager, ConnectionState, ReconnectionManager, AudioPipeline, relationship diagrams
- **Use Cases:** Understanding data structures, implementing new features, debugging state issues

**state-machine.md**
- **Purpose:** Document state transitions and lifecycle management
- **Audience:** Developers
- **Content:** ConnectionState state machine, MainActivity lifecycle, VoiceService lifecycle, PorcupineService lifecycle, state diagrams
- **Use Cases:** Understanding state transitions, debugging lifecycle issues, implementing new states

### Implementation Documentation (`docs/implementation/`)

**components.md**
- **Purpose:** Provide detailed component documentation
- **Audience:** Developers
- **Content:** 8 major components with full method documentation (role, parameters, returns, preconditions, postconditions, side-effects, errors, examples)
- **Use Cases:** Understanding component APIs, implementing new features, debugging component behavior

**interactions.md**
- **Purpose:** Document how components interact
- **Audience:** Developers
- **Content:** 5 key interaction sequences (Start Conversation, Reconnection Flow, Background Operation, Wake Word Detection, Error Recovery)
- **Use Cases:** Understanding data flows, debugging interaction issues, implementing new flows

**lifecycle.md**
- **Purpose:** Document activity and service lifecycle management
- **Audience:** Developers
- **Content:** Activity lifecycle (onCreate, onPause, onResume, onStop, onDestroy), memory pressure callbacks, service lifecycle, resource lifecycle
- **Use Cases:** Understanding lifecycle patterns, debugging lifecycle issues, implementing lifecycle-aware features

### Guides (`docs/guides/`)

**quick-start.md**
- **Purpose:** Help developers get started quickly
- **Audience:** New developers
- **Content:** Installation, building, first run, basic usage, development commands
- **Use Cases:** Onboarding, initial setup, quick reference

**picovoice-setup.md**
- **Purpose:** Guide wake word detection setup
- **Audience:** Developers, operators
- **Content:** Quick start with built-in wake words, custom wake word setup, troubleshooting
- **Use Cases:** Configuring wake word detection, debugging wake word issues

---

## Navigation by Topic

### Audio & Voice
- [Architecture - Audio Pipeline](docs/project/architecture.md#audio-pipeline)
- [Domain Model - AudioPipeline](docs/domain/model.md#audiopipeline)
- [Components - VoiceClientManager](docs/implementation/components.md#voiceclientmanager)
- [Interactions - Start Conversation](docs/implementation/interactions.md#start-conversation-flow)

### WebSocket & Connectivity
- [Architecture - Network Architecture](docs/project/architecture.md#network-architecture)
- [Domain Model - ConnectionState](docs/domain/model.md#connectionstate)
- [State Machine - ConnectionState](docs/domain/state-machine.md#connectionstate-state-machine)
- [Interactions - Reconnection Flow](docs/implementation/interactions.md#reconnection-flow)

### Background Operation
- [Requirements - Background Operation](docs/project/requirements.md#tr-2-background-operation)
- [Architecture - VoiceService](docs/project/architecture.md#voiceservice)
- [Lifecycle - Service Lifecycle](docs/implementation/lifecycle.md#service-lifecycle)
- [Interactions - Background Operation](docs/implementation/interactions.md#background-operation-flow)

### Wake Word Detection
- [Requirements - Wake Word Detection](docs/project/requirements.md#fr-6-wake-word-detection)
- [Architecture - PorcupineService](docs/project/architecture.md#porcupineservice)
- [Components - PicovoiceManager](docs/implementation/components.md#picovoicemanager)
- [Interactions - Wake Word Detection](docs/implementation/interactions.md#wake-word-detection-flow)
- [Guide - Picovoice Setup](docs/guides/picovoice-setup.md)

### Session Management
- [Domain Model - SessionManager](docs/domain/model.md#sessionmanager)
- [Components - SessionManager](docs/implementation/components.md#sessionmanager)
- [Lifecycle - Session Lifecycle](docs/implementation/lifecycle.md#session-lifecycle)

### Error Handling
- [Architecture - Error Handling](docs/project/architecture.md#error-handling)
- [Interactions - Error Recovery](docs/implementation/interactions.md#error-recovery-flow)

### Lifecycle Management
- [Lifecycle - Activity Lifecycle](docs/implementation/lifecycle.md#activity-lifecycle)
- [Lifecycle - Memory Pressure](docs/implementation/lifecycle.md#memory-pressure-callbacks)
- [State Machine - MainActivity Lifecycle](docs/domain/state-machine.md#mainactivity-lifecycle-states)

---

## How to Use This Documentation

### For New Developers
1. Start with [README](README.md) for a high-level overview
2. Read [Quick Start Guide](docs/guides/quick-start.md) to set up your environment
3. Review [Architecture Overview](docs/project/architecture.md) to understand the system
4. Dive into [Components](docs/implementation/components.md) for implementation details

### For Debugging
1. Check [State Machines](docs/domain/state-machine.md) to understand current state
2. Review [Interactions](docs/implementation/interactions.md) for relevant flows
3. Consult [Components](docs/implementation/components.md) for method documentation
4. Check [Lifecycle Management](docs/implementation/lifecycle.md) for lifecycle issues

### For Adding Features
1. Review [Requirements](docs/project/requirements.md) to understand existing requirements
2. Check [Architecture Decisions](docs/project/decisions.md) for design patterns
3. Study [Domain Model](docs/domain/model.md) to understand data structures
4. Review [Components](docs/implementation/components.md) for APIs to extend

### For Architecture Review
1. Read [Architecture Overview](docs/project/architecture.md)
2. Review [Architecture Decisions](docs/project/decisions.md)
3. Check [Domain Model](docs/domain/model.md) for data architecture
4. Review [State Machines](docs/domain/state-machine.md) for state management

---

## Maintenance

This documentation is actively maintained. For guidelines on updating documentation, see [DOCS_MAINTENANCE_RULES.md](DOCS_MAINTENANCE_RULES.md).

### Documentation Updates
- All documentation changes should be logged in [MIGRATION_LOG.md](MIGRATION_LOG.md)
- Code references should be verified against source code
- Conflicts should be resolved using code as source of truth
- Archived documents are in `/archive/` for historical reference

### Contributing
When updating documentation:
1. Follow the structure defined in [DOCS_MAINTENANCE_RULES.md](DOCS_MAINTENANCE_RULES.md)
2. Add code references where applicable
3. Update this index if adding new major documents
4. Log changes in [MIGRATION_LOG.md](MIGRATION_LOG.md)

---

## Archive

Historical documentation is preserved in `/archive/` for reference:
- `/archive/tasks/` - Completed task files (33 files)
- `/archive/analyses/` - Historical analyses (38 files)
- `/archive/fixes/` - Historical bug fixes (6 files)

**Note:** Archived documents are marked with "STATUS: ARCHIVED" headers and are excluded from RAG indexing.

---

## Additional Resources

- **Source Code:** `gemini-multimodal-websocket-demo/src/main/java/`
- **Build Configuration:** `build.gradle.kts`, `gradle/libs.versions.toml`
- **Migration Log:** [MIGRATION_LOG.md](MIGRATION_LOG.md)
- **Maintenance Rules:** [DOCS_MAINTENANCE_RULES.md](DOCS_MAINTENANCE_RULES.md)

---

**For questions or issues with documentation, please refer to [DOCS_MAINTENANCE_RULES.md](DOCS_MAINTENANCE_RULES.md) or contact the development team.**

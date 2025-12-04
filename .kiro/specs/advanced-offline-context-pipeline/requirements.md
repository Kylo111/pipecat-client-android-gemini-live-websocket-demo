# Requirements Document

## Introduction

This document specifies the requirements for the Advanced Offline Context Pipeline - a sophisticated memory system for the Gemini Live voice assistant application. The system replaces the current simple summary-based approach with a structured memory architecture using User Cards (Global and Local), Meta-Summaries, and a Clipboard Tool for Gemini Live.

The pipeline consists of two main processes:
1. **Context Assembly (Session Start)**: Aggregates data from the database (Cards, Prompts, Transcript) into an optimized input prompt for Gemini Live
2. **Memory Evolution (Session End)**: Uses LLM (Gemini Pro/Flash) to process the completed session transcript and update User Cards and Meta-Summary

Key design decisions:
- No migration needed - old session history can be deleted (app not yet released to users)
- Global Memory Update Prompt - single hardcoded prompt for memory updates (no per-conversation customization)
- Clipboard functionality via Gemini Live Tool (Function Calling) instead of dedicated UI logic

## Glossary

- **Global User Card**: JSON structure containing persistent facts about the user across all conversations (name, preferences, tech stack, professional background)
- **Local Conversation Card**: JSON structure containing state and facts specific to a single conversation (current topic, project state, goals, agreed facts)
- **Meta-Summary**: Narrative text summarizing the history of a conversation in a coherent story form
- **Memory Update Service**: Component that uses Gemini Pro/Flash to analyze session transcripts and update memory structures
- **Context Builder**: Component that assembles all memory data into a single prompt for Gemini Live at session start
- **Clipboard Tool**: Gemini Live Function Calling tool that allows the AI to copy text to the system clipboard on user request
- **Session Transcript**: Full text record of a voice conversation session
- **Memory Evolution**: The process of updating memory structures after a session ends

## Requirements

### Requirement 1

**User Story:** As a user, I want the AI assistant to remember facts about me across all conversations, so that I don't have to repeat basic information about myself.

#### Acceptance Criteria

1. WHEN a session starts THEN the Context Builder SHALL include the Global User Card JSON in the system prompt
2. WHEN a session ends THEN the Memory Update Service SHALL extract new persistent user facts from the transcript and update the Global User Card
3. WHEN the Global User Card is updated THEN the system SHALL preserve existing facts that were not contradicted in the new session
4. WHEN serializing the Global User Card THEN the system SHALL produce valid JSON that can be deserialized back to an equivalent object

### Requirement 2

**User Story:** As a user, I want the AI to remember the context and state of each specific conversation, so that I can continue projects and discussions seamlessly.

#### Acceptance Criteria

1. WHEN a session starts THEN the Context Builder SHALL include the Local Conversation Card JSON in the system prompt
2. WHEN a session ends THEN the Memory Update Service SHALL update the Local Conversation Card with new project state, goals, and agreed facts
3. WHEN a new conversation is created THEN the system SHALL initialize the Local Conversation Card as an empty JSON object
4. WHEN loading a conversation with null localCardJson field THEN the system SHALL treat it as an empty JSON object
5. WHEN serializing the Local Conversation Card THEN the system SHALL produce valid JSON that can be deserialized back to an equivalent object

### Requirement 3

**User Story:** As a user, I want a narrative summary of my conversation history, so that the AI understands the flow and context of our past interactions.

#### Acceptance Criteria

1. WHEN a session starts THEN the Context Builder SHALL include the Meta-Summary text in the system prompt
2. WHEN a session ends THEN the Memory Update Service SHALL extend the Meta-Summary with key events from the new session
3. WHEN a new conversation is created THEN the system SHALL set the Meta-Summary to indicate a new conversation start
4. WHEN loading a conversation with null metaSummary field THEN the system SHALL treat it as "New conversation started"
5. WHEN the Meta-Summary exceeds 1000 words THEN the Memory Update Service SHALL condense the earliest parts of the narrative while preserving the most recent events in full detail

### Requirement 4

**User Story:** As a user, I want to ask the AI to copy text to my clipboard, so that I can easily save code snippets, summaries, or important information.

#### Acceptance Criteria

1. WHEN the user asks to copy or save text THEN Gemini Live SHALL invoke the copyToClipboard tool with the requested text
2. WHEN the copyToClipboard tool is invoked THEN the system SHALL copy the provided text to the Android system clipboard
3. WHEN the clipboard operation succeeds THEN the system SHALL return a success response to Gemini Live
4. WHEN the clipboard operation completes THEN the system SHALL emit an event that can be observed for UI feedback

### Requirement 5

**User Story:** As a user, I want the context assembly to be efficient and within token limits, so that the AI can process my conversation history without errors.

#### Acceptance Criteria

1. WHEN building context THEN the Context Builder SHALL combine Global User Card, Local Conversation Card, Meta-Summary, Last Session Transcript (if available), and system prompts into a single formatted string
2. WHEN the combined context exceeds 30,000 characters (~7,500 tokens) THEN the Context Builder SHALL truncate the Last Session Transcript to fit within limits, prioritizing the most recent messages while preserving Cards and Meta-Summary in full
3. WHEN building context THEN the Context Builder SHALL include the conversation-specific system prompt (persona) if defined
4. WHEN building context THEN the Context Builder SHALL structure sections with clear delimiters for AI comprehension
5. WHEN initializing the Gemini Live session THEN the system SHALL explicitly enable contextWindowCompression in the session configuration
6. WHEN configuring compression THEN the system SHALL set triggerTokens to 100,000 tokens to ensure stability in long sessions (>1 hour)

### Requirement 6

**User Story:** As a developer, I want the memory update process to be robust and handle errors gracefully, so that the app remains stable even when network issues occur.

#### Acceptance Criteria

1. WHEN the Memory Update Service receives an invalid JSON response THEN the system SHALL log the error and preserve existing memory state
2. WHEN the network is unavailable during memory update THEN the system SHALL queue the update task for later execution
3. WHEN parsing the LLM response THEN the system SHALL handle malformed JSON gracefully using lenient parsing
4. WHEN the memory update fails THEN the system SHALL continue app operation without crashing
5. WHEN a session ends THEN the system SHALL set memoryUpdatePending flag to true and display "Zapisuję wspomnienia..." in the UI
6. WHEN memory update completes (success or failure) THEN the system SHALL set memoryUpdatePending flag to false
7. WHEN memoryUpdatePending is true THEN the system SHALL prevent starting a new session for that conversation

### Requirement 7

**User Story:** As a user, I want the database schema to support the new memory structures, so that my conversation data is properly stored and retrieved.

#### Acceptance Criteria

1. WHEN the app starts THEN the system SHALL initialize GlobalMemoryDataStore for storing Global User Card
2. WHEN the app starts THEN the system SHALL ensure the conversations table has localCardJson, metaSummary, lastUpdatedAt, and memoryUpdatePending columns
3. WHEN upgrading from an older database version THEN the system SHALL use destructive migration to reset data
4. WHEN storing memory data THEN the system SHALL use nullable columns for optional fields

### Requirement 8

**User Story:** As a user, I want the old per-conversation summary prompt feature removed from offline conversations, so that the system uses the simplified global memory update approach.

#### Acceptance Criteria

1. WHEN the database schema is updated THEN the system SHALL remove the customSummaryPrompt column from conversations table
2. WHEN the database schema is updated THEN the system SHALL remove the copySummaryToClipboard column from conversations table
3. WHEN updating memory for offline conversations THEN the Memory Update Service SHALL use a single global instruction prompt defined in the application code

### Requirement 9

**User Story:** As a user, I want the summary generation to work differently for LibreChat vs Offline conversations, so that each mode uses the appropriate memory approach.

#### Acceptance Criteria

1. WHEN a LibreChat session ends THEN the system SHALL use the existing summary prompt from Settings to generate a summary
2. WHEN an Offline (Gemini Live) session ends THEN the system SHALL use the Memory Update Service with Global/Local Cards and Meta-Summary
3. WHEN determining which summary approach to use THEN the system SHALL check the conversation source field (librechat vs gemini_live)
4. WHEN the conversation source is gemini_live THEN the system SHALL skip the old summary generation and use Memory Evolution instead

### Requirement 10

**User Story:** As an administrator, I want system prompts organized in a centralized configuration, so that I can easily review and modify default prompts.

#### Acceptance Criteria

1. WHEN the application initializes THEN the system SHALL load system prompts from a centralized SystemPrompts configuration object
2. WHEN accessing the tools instruction prompt THEN the system SHALL retrieve it from the SystemPrompts configuration
3. WHEN accessing the LibreChat summary prompt THEN the system SHALL retrieve it from the SystemPrompts configuration
4. WHEN accessing the Memory Update instruction prompt THEN the system SHALL retrieve it from the SystemPrompts configuration
5. WHEN a prompt is needed THEN the SystemPrompts configuration SHALL provide a default value that can be overridden in Settings where appropriate


# Storage and Persistence

<details>
<summary>Relevant source files</summary>

The following files were used as context for generating this wiki page:

- [zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/GitNotebookRepo.java](zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/GitNotebookRepo.java)
- [zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/NotebookRepo.java](zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/NotebookRepo.java)
- [zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/NotebookRepoSync.java](zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/NotebookRepoSync.java)
- [zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/VFSNotebookRepo.java](zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/VFSNotebookRepo.java)
- [zeppelin-zengine/src/test/java/org/apache/zeppelin/notebook/repo/GitNotebookRepoTest.java](zeppelin-zengine/src/test/java/org/apache/zeppelin/notebook/repo/GitNotebookRepoTest.java)
- [zeppelin-zengine/src/test/java/org/apache/zeppelin/notebook/repo/NotebookRepoSyncTest.java](zeppelin-zengine/src/test/java/org/apache/zeppelin/notebook/repo/NotebookRepoSyncTest.java)
- [zeppelin-zengine/src/test/java/org/apache/zeppelin/notebook/repo/VFSNotebookRepoTest.java](zeppelin-zengine/src/test/java/org/apache/zeppelin/notebook/repo/VFSNotebookRepoTest.java)

</details>



This document covers Zeppelin's storage and persistence layer, which manages how notebooks are stored, synchronized, and versioned across different storage backends. The system supports multiple storage implementations including local file systems, Git repositories, and cloud storage providers, with built-in synchronization capabilities between multiple backends.

For information about notebook structure and lifecycle management, see [Core Server Components](#2.2). For interpreter-specific storage and recovery mechanisms, see [Interpreter Framework](#2.3).

## Storage Architecture Overview

Zeppelin's storage system is built around a pluggable architecture that supports multiple storage backends through the `NotebookRepo` interface. The system can operate with up to two storage backends simultaneously, with automatic synchronization between them.

```mermaid
graph TB
    subgraph "Storage Layer Architecture"
        NotebookService["NotebookService"]
        NotebookRepoSync["NotebookRepoSync<br/>Synchronization Layer"]
        
        subgraph "Storage Implementations"
            VFSRepo["VFSNotebookRepo<br/>File System Storage"]
            GitRepo["GitNotebookRepo<br/>Version Control Storage"]
            S3Repo["S3NotebookRepo<br/>Cloud Storage"]
            AzureRepo["AzureNotebookRepo<br/>Azure Storage"]
            MongoRepo["MongoNotebookRepo<br/>Database Storage"]
        end
        
        subgraph "Configuration"
            ZeppelinConf["ZeppelinConfiguration<br/>ZEPPELIN_NOTEBOOK_STORAGE"]
            PluginManager["PluginManager<br/>Storage Loading"]
        end
    end
    
    NotebookService --> NotebookRepoSync
    NotebookRepoSync --> VFSRepo
    NotebookRepoSync --> GitRepo
    NotebookRepoSync --> S3Repo
    NotebookRepoSync --> AzureRepo
    NotebookRepoSync --> MongoRepo
    
    ZeppelinConf --> NotebookRepoSync
    PluginManager --> NotebookRepoSync
```

Sources: [zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/NotebookRepoSync.java:44-103](), [zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/NotebookRepo.java:30-42]()

## NotebookRepo Interface

The `NotebookRepo` interface defines the core contract for all storage implementations. It provides standardized methods for notebook lifecycle operations and includes utility methods for file naming conventions.

| Method | Purpose | Parameters |
|--------|---------|------------|
| `list()` | List all notebooks | `AuthenticationInfo subject` |
| `get()` | Retrieve specific notebook | `noteId, notePath, subject` |
| `save()` | Persist notebook changes | `Note note, subject` |
| `move()` | Relocate notebook or folder | `noteId, notePath, newNotePath, subject` |
| `remove()` | Delete notebook or folder | `noteId, notePath, subject` |

```mermaid
graph LR
    subgraph "File Naming Convention"
        NotePath["/my_project/my_note"]
        NoteId["2A94M5J1Z"]
        FileName["my_project/my_note_2A94M5J1Z.zpln"]
    end
    
    subgraph "Utility Methods"
        buildNoteFileName["buildNoteFileName()"]
        getNoteId["getNoteId()"]
        getNotePath["getNotePath()"]
    end
    
    NotePath --> buildNoteFileName
    NoteId --> buildNoteFileName
    buildNoteFileName --> FileName
    FileName --> getNoteId
    FileName --> getNotePath
```

Sources: [zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/NotebookRepo.java:140-181]()

## Synchronization Layer

The `NotebookRepoSync` class manages synchronization between multiple storage backends, supporting both one-way and two-way synchronization modes. It operates as the primary interface between the notebook service and underlying storage implementations.

```mermaid
graph TB
    subgraph "NotebookRepoSync Operations"
        Primary["Primary Storage<br/>(repos[0])"]
        Secondary["Secondary Storage<br/>(repos[1])"]
        
        subgraph "Sync Logic"
            Compare["notesCheckDiff()"]
            Push["pushNotes()"]
            Pull["pullNotes()"]
            Delete["deleteNotes()"]
        end
        
        subgraph "Conflict Resolution"
            OneWaySync["oneWaySync mode"]
            TwoWaySync["Timestamp comparison"]
            VersionCheck["lastModificationDate()"]
        end
    end
    
    Primary --> Compare
    Secondary --> Compare
    Compare --> Push
    Compare --> Pull
    Compare --> Delete
    
    OneWaySync --> Push
    TwoWaySync --> VersionCheck
    VersionCheck --> Push
    VersionCheck --> Pull
```

### Synchronization Process

The synchronization algorithm compares notebooks between storage backends and determines required actions:

1. **Note Comparison**: Uses `notesCheckDiff()` to identify differences between storages
2. **Timestamp Analysis**: Compares modification dates using paragraph timestamps
3. **Action Classification**: Categorizes notes into push, pull, or delete operations
4. **Conflict Resolution**: Applies one-way or timestamp-based resolution strategies

Sources: [zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/NotebookRepoSync.java:315-376](), [zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/NotebookRepoSync.java:231-275]()

## File System Storage

The `VFSNotebookRepo` implementation provides file-based storage using Apache Commons VFS, supporting local file systems and various remote file system protocols.

```mermaid
graph TB
    subgraph "VFSNotebookRepo Components"
        FileSystemManager["fsManager<br/>FileSystemManager"]
        RootFileObject["rootNotebookFileObject<br/>FileObject"]
        
        subgraph "File Operations"
            ListFolder["listFolder()"]
            SaveNote["save()"]
            MoveNote["move()"]
            RemoveNote["remove()"]
        end
        
        subgraph "VFS Backends"
            LocalFS["Local File System"]
            FTPFS["FTP"]
            SFTPFS["SFTP"]
            HTTPFS["HTTP/WebDAV"]
        end
    end
    
    FileSystemManager --> RootFileObject
    RootFileObject --> ListFolder
    RootFileObject --> SaveNote
    RootFileObject --> MoveNote
    RootFileObject --> RemoveNote
    
    FileSystemManager --> LocalFS
    FileSystemManager --> FTPFS
    FileSystemManager --> SFTPFS
    FileSystemManager --> HTTPFS
```

### File Organization

Notebooks are stored as JSON files with the `.zpln` extension, organized in a hierarchical folder structure that mirrors the notebook path hierarchy:

- **Root Directory**: Configured via `ZEPPELIN_NOTEBOOK_DIR`
- **File Naming**: `{notePath}_{noteId}.zpln` (path separator becomes folder structure)
- **Temporary Files**: Uses `.tmp` extension during save operations for atomicity

Sources: [zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/VFSNotebookRepo.java:96-128](), [zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/VFSNotebookRepo.java:142-159]()

## Version Control Storage

The `GitNotebookRepo` extends `VFSNotebookRepo` to provide Git-based version control capabilities, including checkpointing, revision history, and rollback functionality.

```mermaid
graph TB
    subgraph "GitNotebookRepo Features"
        GitRepo["Git Repository"]
        VFSBase["VFSNotebookRepo<br/>Base Functionality"]
        
        subgraph "Version Control Operations"
            Checkpoint["checkpoint()"]
            RevisionHistory["revisionHistory()"]
            GetRevision["get(noteId, notePath, revId)"]
            SetRevision["setNoteRevision()"]
        end
        
        subgraph "Git Operations"
            GitAdd["git.add()"]
            GitCommit["git.commit()"]
            GitCheckout["git.checkout()"]
            GitStash["git.stash()"]
            GitLog["git.log()"]
        end
    end
    
    VFSBase --> GitRepo
    Checkpoint --> GitAdd
    Checkpoint --> GitCommit
    RevisionHistory --> GitLog
    GetRevision --> GitStash
    GetRevision --> GitCheckout
    SetRevision --> GitCheckout
```

### Revision Management

The Git implementation provides sophisticated revision management:

1. **Checkpointing**: Creates Git commits only when changes are detected via `git.diff()`
2. **Revision Retrieval**: Uses stash/checkout/unstash sequence to access historical versions
3. **History Tracking**: Leverages Git log to provide complete revision history
4. **Safe Rollback**: Implements atomic rollback operations using Git reset

Sources: [zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/GitNotebookRepo.java:123-146](), [zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/GitNotebookRepo.java:155-191]()

## Configuration and Initialization

Storage backends are configured through `ZeppelinConfiguration` and loaded dynamically via the `PluginManager`. The system supports flexible configuration of multiple storage classes.

```mermaid
graph LR
    subgraph "Configuration Flow"
        ConfVars["ConfVars.ZEPPELIN_NOTEBOOK_STORAGE"]
        ClassNames["Storage Class Names<br/>Comma-separated"]
        PluginManager["PluginManager.loadNotebookRepo()"]
        
        subgraph "Default Storage"
            DefaultStorage["org.apache.zeppelin.notebook.repo.GitNotebookRepo"]
        end
        
        subgraph "Initialization"
            InitRepo["repo.init(conf)"]
            AddToList["repos.add(repo)"]
        end
    end
    
    ConfVars --> ClassNames
    ClassNames --> PluginManager
    PluginManager --> InitRepo
    InitRepo --> AddToList
    DefaultStorage --> PluginManager
```

### Configuration Properties

| Property | Purpose | Default Value |
|----------|---------|---------------|
| `ZEPPELIN_NOTEBOOK_STORAGE` | Storage implementation classes | `GitNotebookRepo` |
| `ZEPPELIN_NOTEBOOK_ONE_WAY_SYNC` | Enable one-way synchronization | `false` |
| `ZEPPELIN_NOTEBOOK_DIR` | Root storage directory | `notebook/` |

Sources: [zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/NotebookRepoSync.java:66-103](), [zeppelin-zengine/src/test/java/org/apache/zeppelin/notebook/repo/NotebookRepoSyncTest.java:82-87]()

## Persistence Data Flow

The following diagram illustrates how notebook data flows through the persistence layer during typical operations:

```mermaid
sequenceDiagram
    participant NS as "NotebookService"
    participant NRS as "NotebookRepoSync"
    participant PR as "Primary Repo"
    participant SR as "Secondary Repo"
    
    Note over NS,SR: Save Operation
    NS->>NRS: save(note, subject)
    NRS->>PR: save(note, subject)
    NRS->>SR: save(note, subject)
    
    Note over NS,SR: Sync Operation
    NS->>NRS: sync(subject)
    NRS->>PR: list(subject)
    NRS->>SR: list(subject)
    NRS->>NRS: notesCheckDiff()
    
    alt Notes need push
        NRS->>PR: get(noteId, notePath, subject)
        NRS->>SR: save(note, subject)
    else Notes need pull
        NRS->>SR: get(noteId, notePath, subject)
        NRS->>PR: save(note, subject)
    end
```

Sources: [zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/NotebookRepoSync.java:167-178](), [zeppelin-zengine/src/main/java/org/apache/zeppelin/notebook/repo/NotebookRepoSync.java:277-279]()

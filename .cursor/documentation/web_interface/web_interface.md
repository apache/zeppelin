# Web Interface

<details>
<summary>Relevant source files</summary>

The following files were used as context for generating this wiki page:

- [zeppelin-distribution/src/bin_license/LICENSE](zeppelin-distribution/src/bin_license/LICENSE)
- [zeppelin-distribution/src/bin_license/licenses/LICENSE-patuaOne-font](zeppelin-distribution/src/bin_license/licenses/LICENSE-patuaOne-font)
- [zeppelin-distribution/src/bin_license/licenses/LICENSE-source_code_pro-font](zeppelin-distribution/src/bin_license/licenses/LICENSE-source_code_pro-font)
- [zeppelin-web/.eslintrc](zeppelin-web/.eslintrc)
- [zeppelin-web/Gruntfile.js](zeppelin-web/Gruntfile.js)
- [zeppelin-web/bower.json](zeppelin-web/bower.json)
- [zeppelin-web/karma.conf.js](zeppelin-web/karma.conf.js)
- [zeppelin-web/package-lock.json](zeppelin-web/package-lock.json)
- [zeppelin-web/package.json](zeppelin-web/package.json)
- [zeppelin-web/src/app/app.controller.test.js](zeppelin-web/src/app/app.controller.test.js)
- [zeppelin-web/src/app/app.js](zeppelin-web/src/app/app.js)
- [zeppelin-web/src/app/home/home.controller.js](zeppelin-web/src/app/home/home.controller.js)
- [zeppelin-web/src/app/home/home.css](zeppelin-web/src/app/home/home.css)
- [zeppelin-web/src/app/home/home.html](zeppelin-web/src/app/home/home.html)
- [zeppelin-web/src/app/notebook/notebook-actionBar.html](zeppelin-web/src/app/notebook/notebook-actionBar.html)
- [zeppelin-web/src/app/notebook/notebook.controller.js](zeppelin-web/src/app/notebook/notebook.controller.js)
- [zeppelin-web/src/app/notebook/notebook.controller.test.js](zeppelin-web/src/app/notebook/notebook.controller.test.js)
- [zeppelin-web/src/app/notebook/notebook.css](zeppelin-web/src/app/notebook/notebook.css)
- [zeppelin-web/src/app/notebook/notebook.html](zeppelin-web/src/app/notebook/notebook.html)
- [zeppelin-web/src/app/notebook/paragraph/paragraph-control.html](zeppelin-web/src/app/notebook/paragraph/paragraph-control.html)
- [zeppelin-web/src/app/notebook/paragraph/paragraph.controller.js](zeppelin-web/src/app/notebook/paragraph/paragraph.controller.js)
- [zeppelin-web/src/app/notebook/paragraph/paragraph.controller.test.js](zeppelin-web/src/app/notebook/paragraph/paragraph.controller.test.js)
- [zeppelin-web/src/app/notebook/paragraph/paragraph.css](zeppelin-web/src/app/notebook/paragraph/paragraph.css)
- [zeppelin-web/src/app/notebook/paragraph/paragraph.html](zeppelin-web/src/app/notebook/paragraph/paragraph.html)
- [zeppelin-web/src/app/tabledata/tabledata.test.js](zeppelin-web/src/app/tabledata/tabledata.test.js)
- [zeppelin-web/src/components/navbar/navbar.controller.js](zeppelin-web/src/components/navbar/navbar.controller.js)
- [zeppelin-web/src/components/navbar/navbar.controller.test.js](zeppelin-web/src/components/navbar/navbar.controller.test.js)
- [zeppelin-web/src/components/navbar/navbar.css](zeppelin-web/src/components/navbar/navbar.css)
- [zeppelin-web/src/components/navbar/navbar.html](zeppelin-web/src/components/navbar/navbar.html)
- [zeppelin-web/src/index.html](zeppelin-web/src/index.html)

</details>



The Apache Zeppelin web interface is a browser-based frontend application that provides an interactive notebook environment for data analytics. This document covers the architecture, components, and implementation details of the client-side web application.

For information about server-side notebook management and APIs, see [Notebook Server and APIs](#4.1). For details about interpreter configuration interfaces, see [Interpreter Management UI](#3.2).

## Frontend Architecture Overview

The Zeppelin web interface is built as a single-page application (SPA) using AngularJS 1.5.7. The application follows a modular architecture with distinct controllers, services, and templates for different functional areas.

### Application Structure

```mermaid
graph TB
    subgraph "zeppelin-web Module"
        AppJS["app.js<br/>Main Module"]
        IndexHTML["index.html<br/>Entry Point"]
        MainCtrl["MainCtrl<br/>Root Controller"]
    end
    
    subgraph "Core Components"
        NavCtrl["NavCtrl<br/>navbar.controller.js"]
        HomeCtrl["HomeCtrl<br/>home.controller.js"]
        NotebookCtrl["NotebookCtrl<br/>notebook.controller.js"]
        ParagraphCtrl["ParagraphCtrl<br/>paragraph.controller.js"]
    end
    
    subgraph "Services & Communication"
        WebSocketSrv["websocketMsgSrv<br/>WebSocket Service"]
        BaseUrlSrv["baseUrlSrv<br/>REST API Base"]
        HeliumSrv["heliumService<br/>Plugin System"]
    end
    
    subgraph "External Libraries"
        Angular["AngularJS 1.5.7"]
        Bootstrap["Bootstrap 3.x"]
        ACE["ACE Editor"]
        D3["D3.js + NVD3"]
    end
    
    AppJS --> NavCtrl
    AppJS --> HomeCtrl
    AppJS --> NotebookCtrl
    AppJS --> ParagraphCtrl
    
    NotebookCtrl --> ParagraphCtrl
    NavCtrl --> WebSocketSrv
    NotebookCtrl --> WebSocketSrv
    ParagraphCtrl --> WebSocketSrv
    
    AppJS --> Angular
    AppJS --> Bootstrap
    ParagraphCtrl --> ACE
    AppJS --> D3
```

Sources: [zeppelin-web/src/app/app.js:67-106](), [zeppelin-web/src/index.html:15-223](), [zeppelin-web/bower.json:4-43]()

### Module Dependencies

The main application module `zeppelinWebApp` integrates numerous Angular modules and third-party libraries:

| Category | Modules | Purpose |
|----------|---------|---------|
| Core Angular | `ngCookies`, `ngAnimate`, `ngRoute`, `ngSanitize` | Basic Angular functionality |
| UI Components | `ui.bootstrap`, `ui.ace`, `as.sortable` | UI widgets and editor |
| Communication | `angular-websocket`, `ngResource` | Server communication |
| Data Processing | `angular.filter`, `ui.grid` | Data manipulation and display |
| Utilities | `ngToast`, `ngclipboard`, `xeditable` | User experience enhancements |

Sources: [zeppelin-web/src/app/app.js:27-59](), [zeppelin-web/bower.json:5-39]()

## Routing and Navigation

The application uses Angular's `$routeProvider` to define client-side routes that map URLs to templates and controllers.

### Route Configuration

```mermaid
graph LR
    subgraph "Main Routes"
        Root["/"]
        Notebook["/notebook/:noteId"]
        Paragraph["/notebook/:noteId/paragraph/:paragraphId"]
        Revision["/notebook/:noteId/revision/:revisionId"]
        Interpreter["/interpreter"]
        JobManager["/jobmanager"]
    end
    
    subgraph "Templates & Controllers"
        HomeHTML["home.html"]
        NotebookHTML["notebook.html<br/>NotebookCtrl"]
        InterpreterHTML["interpreter.html<br/>InterpreterCtrl"]
        JobHTML["jobmanager.html<br/>JobmanagerCtrl"]
    end
    
    Root --> HomeHTML
    Notebook --> NotebookHTML
    Paragraph --> NotebookHTML
    Revision --> NotebookHTML
    Interpreter --> InterpreterHTML
    JobManager --> JobHTML
```

Sources: [zeppelin-web/src/app/app.js:86-130]()

The routing system supports:
- **Notebook Display**: `/notebook/:noteId` routes to the main notebook interface
- **Paragraph Focus**: `/notebook/:noteId/paragraph/:paragraphId` for direct paragraph access
- **Revision Viewing**: `/notebook/:noteId/revision/:revisionId` for version control
- **Management Interfaces**: Dedicated routes for interpreter and job management

## Core User Interface Components

### Navigation Bar Component

The `NavCtrl` controller manages the top-level navigation, search functionality, and user authentication state.

**Key Functions:**
- `search(searchTerm)` - Navigate to search results ([zeppelin-web/src/components/navbar/navbar.controller.js:205-207]())
- `logout()` - Handle user logout process ([zeppelin-web/src/components/navbar/navbar.controller.js:101-177]())
- `loadNotes()` - Fetch and display notebook list ([zeppelin-web/src/components/navbar/navbar.controller.js:93-95]())

```mermaid
graph TB
    subgraph "NavCtrl Responsibilities"
        UserAuth["User Authentication<br/>Login/Logout"]
        NotebookList["Notebook List<br/>Dropdown Menu"]
        SearchBox["Search Functionality<br/>Global Search"]
        ConnStatus["Connection Status<br/>WebSocket State"]
    end
    
    subgraph "Services Used"
        WebSocketSrv["websocketMsgSrv"]
        BaseUrlSrv["baseUrlSrv"]
        NoteFactory["noteListFactory"]
    end
    
    UserAuth --> BaseUrlSrv
    NotebookList --> NoteFactory
    SearchBox --> BaseUrlSrv
    ConnStatus --> WebSocketSrv
```

Sources: [zeppelin-web/src/components/navbar/navbar.controller.js:17-276](), [zeppelin-web/src/components/navbar/navbar.html:13-154]()

### Home Page Interface

The `HomeCtrl` manages the landing page, displaying available notebooks and providing creation/import functionality.

**Key Features:**
- Note list with filtering capabilities
- Import/export note functionality  
- Folder-based organization
- Real-time updates via WebSocket

Sources: [zeppelin-web/src/app/home/home.controller.js:17-139](), [zeppelin-web/src/app/home/home.html:14-128]()

### Notebook Interface Architecture

The notebook interface consists of the main `NotebookCtrl` controller and multiple `ParagraphCtrl` instances for individual code/text blocks.

```mermaid
graph TB
    subgraph "NotebookCtrl Scope"
        NoteConfig["Note Configuration<br/>Settings & Permissions"]
        ActionBar["Action Bar<br/>Run, Export, Clone"]
        ParagraphMgmt["Paragraph Management<br/>Add, Remove, Reorder"]
        Search["Search & Replace<br/>Content Search"]
    end
    
    subgraph "ParagraphCtrl Instances"
        Para1["ParagraphCtrl #1<br/>Code Editor + Results"]
        Para2["ParagraphCtrl #2<br/>Code Editor + Results"]
        ParaN["ParagraphCtrl #N<br/>Code Editor + Results"]
    end
    
    subgraph "Shared Services"
        WebSocket["websocketMsgSrv<br/>Real-time Communication"]
        Helium["heliumService<br/>Visualization Plugins"]
    end
    
    NoteConfig --> WebSocket
    ActionBar --> WebSocket
    ParagraphMgmt --> Para1
    ParagraphMgmt --> Para2
    ParagraphMgmt --> ParaN
    
    Para1 --> WebSocket
    Para2 --> WebSocket
    ParaN --> WebSocket
    
    Para1 --> Helium
    Para2 --> Helium
    ParaN --> Helium
```

Sources: [zeppelin-web/src/app/notebook/notebook.controller.js:21-1319](), [zeppelin-web/src/app/notebook/paragraph/paragraph.controller.js:31-1519]()

## Communication Layer

The frontend communicates with the Zeppelin server through two primary channels:

### WebSocket Communication

Real-time bidirectional communication is handled by `websocketMsgSrv` for:
- **Paragraph Execution**: `runParagraph()`, `cancelParagraphRun()`
- **Note Management**: `getNote()`, `updateNote()`, `cloneNote()`
- **Interpreter Control**: `getInterpreterBindings()`, `saveInterpreterBindings()`
- **Real-time Updates**: Paragraph results, status changes, collaborative editing

### REST API Integration

HTTP requests through `baseUrlSrv` for:
- **Authentication**: Login/logout operations
- **File Operations**: Note import/export
- **Configuration**: System settings and version info
- **Permissions**: Note access control

```mermaid
graph LR
    subgraph "Frontend Services"
        WebSocketSrv["websocketMsgSrv"]
        BaseUrlSrv["baseUrlSrv"]
        HttpSrv["$http"]
    end
    
    subgraph "Server Endpoints"
        WSEndpoint["WebSocket<br/>/ws"]
        RestAPI["REST API<br/>/api/..."]
        AuthEndpoint["Auth<br/>/login"]
    end
    
    subgraph "Communication Types"
        RealTime["Real-time Updates<br/>Paragraph execution<br/>Collaborative editing"]
        ReqResp["Request/Response<br/>Configuration<br/>File operations"]
    end
    
    WebSocketSrv --> WSEndpoint
    BaseUrlSrv --> RestAPI
    HttpSrv --> AuthEndpoint
    
    WSEndpoint --> RealTime
    RestAPI --> ReqResp
    AuthEndpoint --> ReqResp
```

Sources: [zeppelin-web/src/components/websocket/websocket-event.factory.js](), [zeppelin-web/src/components/baseUrl/baseurl.service.js]()

## Code Editor Integration

The paragraph interface integrates the ACE editor for code editing with extensive customization and language support.

### Editor Configuration

The `ParagraphCtrl` configures ACE editor instances with:
- **Syntax Highlighting**: Multiple language modes (Scala, Python, SQL, etc.)
- **Autocompletion**: Custom completion provider with backend integration
- **Key Bindings**: Emacs/default key bindings based on platform
- **Real-time Collaboration**: Patch-based synchronization

**Key Editor Functions:**
- `aceLoaded(_editor)` - Initialize editor instance ([zeppelin-web/src/app/notebook/paragraph/paragraph.controller.js:751-1018]())
- `aceChanged(_, editor)` - Handle content changes ([zeppelin-web/src/app/notebook/paragraph/paragraph.controller.js:726-742]())
- `sendPatch()` - Collaborative editing synchronization ([zeppelin-web/src/app/notebook/paragraph/paragraph.controller.js:744-749]())

Sources: [zeppelin-web/src/app/notebook/paragraph/paragraph.controller.js:751-1018](), [zeppelin-web/bower.json:17-18]()

## Build System and Development Workflow

The frontend uses a modern JavaScript build pipeline combining Grunt, Webpack, and npm.

### Build Tools Configuration

| Tool | Purpose | Key Files |
|------|---------|-----------|
| **Grunt** | Task automation | [zeppelin-web/Gruntfile.js]() |
| **Webpack** | Module bundling | [zeppelin-web/webpack.config.js]() |
| **npm** | Dependency management | [zeppelin-web/package.json]() |
| **Bower** | Frontend dependencies | [zeppelin-web/bower.json]() |

### Development Scripts

The [zeppelin-web/package.json:8-24]() defines key npm scripts:
- `npm run dev` - Development server with hot reload
- `npm run build:dist` - Production build
- `npm run lint:watch` - Continuous linting
- `npm run karma-test` - Unit test execution

### Asset Processing Pipeline

```mermaid
graph LR
    subgraph "Source Files"
        JSNG["Angular.js Files<br/>Controllers, Services"]
        HTML["HTML Templates"]
        CSS["CSS/SCSS Styles"]
        Deps["Bower Dependencies<br/>Third-party libs"]
    end
    
    subgraph "Build Process"
        Webpack["Webpack<br/>Module Bundling"]
        Grunt["Grunt Tasks<br/>Copy, Minify, Concat"]
        PostCSS["PostCSS<br/>Autoprefixer"]
    end
    
    subgraph "Output"
        DistJS["Bundled JS<br/>dist/scripts/"]
        DistCSS["Optimized CSS<br/>dist/styles/"]
        DistHTML["Processed HTML<br/>dist/"]
    end
    
    JSNG --> Webpack
    HTML --> Grunt
    CSS --> PostCSS
    Deps --> Grunt
    
    Webpack --> DistJS
    PostCSS --> DistCSS
    Grunt --> DistHTML
```

Sources: [zeppelin-web/Gruntfile.js:18-342](), [zeppelin-web/package.json:8-24](), [zeppelin-web/webpack.config.js]()

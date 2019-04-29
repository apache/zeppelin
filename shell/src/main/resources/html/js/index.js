// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

var noteId = getParams("noteId");
var paragraphId = getParams("paragraphId");

var ws = new WebSocket("ws://" + location.host + "/terminal/");

ws.onopen = () => {
    // alert("ws.onopen");
    // t.showOverlay("Connection established", 1000);
    app.onTerminalReady();
}

ws.onerror = () => {
    // t.showOverlay("Connection error", 3000);
}

ws.onclose = () => {
    // t.showOverlay("Connection closed", 3000);
}

ws.onmessage = (e) => {
    let data = JSON.parse(e.data);
    switch (data.type) {
        case "TERMINAL_PRINT":
            t.io.print(data.text);
    }
}

function action(type, data) {
    let action = Object.assign({
        type
    }, data);

    return JSON.stringify(action);
}

let app = {
    onTerminalInit() {
        // alert("TERMINAL_INIT");
        ws.send(action("TERMINAL_INIT", {
            noteId, paragraphId
        }));
    },
    onCommand(command) {
        ws.send(action("TERMINAL_COMMAND", {
            command
        }));
    },
    resizeTerminal(columns, rows) {
        ws.send(action("TERMINAL_RESIZE", {
            columns, rows
        }));
    },
    onTerminalReady() {
        // alert("TERMINAL_READY");
        ws.send(action("TERMINAL_READY", {
            noteId, paragraphId
        }));
    }
};

var t = null;
function setupHterm() {
    // alert("setupHterm");

    hterm.defaultStorage = new lib.Storage.Memory();
    t = new hterm.Terminal();

    t.onTerminalReady = function() {
        // app.onTerminalInit();

        // Create a new terminal IO object and give it the foreground.
        // (The default IO object just prints warning messages about unhandled
        // things to the the JS console.)
        const io = t.io.push();

        io.onVTKeystroke = (str) => {
            // Do something useful with str here.
            // For example, Secure Shell forwards the string onto the NaCl plugin.
            app.onCommand(str);
        };

        io.sendString = io.onVTKeystroke;

        io.onTerminalResize = (columns, rows) => {
            // React to size changes here.
            // Secure Shell pokes at NaCl, which eventually results in
            // some ioctls on the host.
            app.resizeTerminal(columns, rows);
        };

        // You can call io.push() to foreground a fresh io context, which can
        // be uses to give control of the terminal to something else.  When that
        // thing is complete, should call io.pop() to restore control to the
        // previous io object.
    };

    t.decorate(document.querySelector('#terminal'));
    t.installKeyboard();

    // TODO:(Xun) Support multiple style themes
    // t.getPrefs().set("cursor-color", "rgba(0, 0, 0, 0.75)");
    // t.getPrefs().set("cursor-blink", true);
    // t.getPrefs().set("background-color", "white");
    // t.getPrefs().set("foreground-color", "black");
    t.getPrefs().set("font-size", 12);

    var now = new Date()
    t.io.println('Last login: ' + now.toUTCString() + '\033[?25h');
}

function getParams(key) {
    var reg = new RegExp("(^|&)" + key + "=([^&]*)(&|$)");
    var r = location.search.substr(1).match(reg);
    if (r != null) {
        return unescape(r[2]);
    }
    return null;
};

// This will be whatever normal entry/initialization point your project uses.
window.onload = function () {
    lib.init(setupHterm);
};

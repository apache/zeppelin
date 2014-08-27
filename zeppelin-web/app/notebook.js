function Notebook(config){
    var notebook = this;
    this.config = config;

    this.ws = new WebSocket(config.socket);

    // current note
    this.currentNote;

    this.setNote = function(data){
        console.log("setNote %o", data);
        var target = $(config.target.selector);

        if(this.currentNote) {
            if(this.currentNote.data.id == data.id){
                // update current note
                this.currentNote.refresh(data)
                console.log("update curret note");
            } else {
                // change to another note
                this.currentNote.destroy();
                this.currentNote = new Note(this, data)
                this.currentNote.render(target)
            }
        } else {
            // load note
            this.currentNote = new Note(this, data)
            this.currentNote.render(target)
        }
    };


    this.ws.onmessage = function(msg) {
        var payload = undefined

        if(msg.data){
            payload = $.parseJSON(msg.data);
        }
        console.log("RECEIVE << %o, %o", payload.op, payload);
        var op = payload.op;
        var data = payload.data;

        if(op == "NOTE"){        // note data
            notebook.setNote(data.note)
        } else if(op == "NOTES_INFO") { // list of notes
            // nothing to do. raise event
        } else if(op == "PARAGRAPH") { // single paragraph
            notebook.currentNote.refreshParagraph(data.paragraph.id, data.paragraph);
        }
        
        if(notebook.listener){
            notebook.listener.onMessage(op, data);
        }
    };
    
    this.ws.onopen = function(response) {
        console.log("Websocket created %o", response);
    };

    this.ws.onerror = function(response){
        console.log("On error %o", response);
    };

    this.ws.onclose = function(response){
        console.log("On close %o", response);
        notebook.closeNote();
    };

    this.send = function(o){
        console.log("SEND >> %o, %o", o.op, o);
        this.ws.send(JSON.stringify(o))
    };

    this.setListener = function(listener){
        this.listener = listener;
    };

    // Create new notebook
    this.newNote = function(){
        this.send({ op : "NEW_NOTE" });
    };


    // Open note
    this.openNote = function(noteId){
        this.send({ op : "GET_NOTE", data : { id : noteId}});
    };
    
    this.closeNote = function(){
        if(!this.currentNote) return;
        this.currentNote.destroy();
        this.currentNote = undefined;
    };

    this.deleteNote = function(noteId){
        this.send({ op : "DEL_NOTE", data : { id : noteId}});
    };

    // list all notebooks
    this.listNotes = function(){
        this.send({op : "LIST_NOTES" });
    };    
};


function Note(notebook, data){
    this.notebook = notebook;
    this.data = data;               // backdata

    this.paragraphs = [];
    this.target;

    this.getParagraph = function(id){
        for(var i=0; i<this.paragraphs.length; i++){
            var o = this.paragraphs[i];
            if(o.data.id==id){
                return o;
            }
        }
        return undefined;
    };

    this.refreshParagraph = function(id, data){
        var newData = jQuery.extend(true, {}, this.data);
        for(var i=0; i<newData.paragraphs.length; i++){
            if(newData.paragraphs[i].id == id){
                newData.paragraphs[i] = data
            }
        }
        this.refresh(newData);
    };

    // refresh data 
    this.refresh = function(data){
        console.log("Note.refresh %o", data);

        var paragraphsEl = this.target.children();
        var newParagraphs = [];

        var prevEl = undefined;
        for(var i=0; i<data.paragraphs.length; i++){  // iterate new info
            var newParagraphInfo = data.paragraphs[i];

            // search for object
            var existingParagraph = this.getParagraph(newParagraphInfo.id);
            
            if(existingParagraph){
                newParagraphs.push(existingParagraph);
                
                var el = existingParagraph.target;
                if(el.index()!=i){  // el need to be moved
                    el.detach();
                    if(i==0){
                        this.target.prepend(el);
                    } else {
                        el.insertAfter(newParagraphs[i-1].target);
                    }
                }
                existingParagraph.refresh(newParagraphInfo);
            } else {
                var p = new Paragraph(this.notebook, newParagraphInfo);
                newParagraphs.push(p);
                if(i==0){
                    this.target.prepend("<div id='"+p.data.id+"' class=\"paragraph\"></div>");
                } else {
                    $("<div id='"+p.data.id+"' class=\"paragraph\"></div>").insertAfter(newParagraphs[i-1].target);
                }
                p.render($('#'+p.data.id));
            }
        }

        // remove deleted
        for(var i=0; i < this.paragraphs.length; i++){
            var p = this.paragraphs[i];
            var found = false;
            for(var j=0; j<newParagraphs.length; j++){
                if(p.data.id == newParagraphs[j].data.id){
                    found = true;
                    break;
                }
            }
            if(found==false){
                p.destroy();
            }
        }
            
        this.paragraphs = newParagraphs;

// totally clear and render
/*
        this.destroy();
        this.data = data;
        this.render(this.target);
*/
    };

    this.render = function(target){
        this.target = target;

        console.log("Note.render %o", this.data);
        var paragraphs = this.data.paragraphs;
        if(!paragraphs || paragraphs.length==0){
            console.log("No paragraph found!");
        } else {
            for(var i=0; i< paragraphs.length; i++){
                var p = new Paragraph(this.notebook, paragraphs[i]);
                this.paragraphs.push(p);

                target.append("<div id='"+p.data.id+"' class=\"paragraph\"></div>");
                p.render($('#'+p.data.id));
            }
        }
    }

    this.destroy = function(){
        this.target.empty();
        this.data = undefined;
        this.paragraphs = [];
    }
};

function Paragraph(notebook, data){
    this.notebook = notebook;
    this.data = data;
    this.target;
    this.table;
    
    // refresh paragraph with new data
    this.refresh = function(data, force){
        if(Object.equals(this.data, data) && !force){
            // Up to date
            return;
        }

        // update editor
        this.target.children("textarea").val(data.paragraph);

        // update form
        var paragraph = this;
        var formEl = this.target.children(".form");
        formEl.empty();
        if(data.form.forms){
            for(var name in data.form.forms){
                var form = data.form.forms[name];
                var value = form.value;
                
                if(data.form.params[name]){
                    value = data.form.params[name];
                }
                
                var html = "";
                if(form.type=="INPUT"){
                    formEl.append(name + ' : <input name="'+name+'" value="'+value+'"></input>');
                    formEl.children('[name="'+name+'"]').on('change', function(formName){
                        var name = formName;
                        return function(evt){
                            var value = formEl.children('[name="'+name+'"]').val();
                            paragraph.data.form.params[name] = value;
                            paragraph.run();
                        }
                    }(name));
                } else {
                    console.log("Unsupported form type %o", form);
                }

            }
            
        }

        // update progress
        this.target.children(".status").html(data.status);

        // update result
        var result = data.result;
        var target = this.target.children(".result");
        
        // same type
        var typeChanged = true;
        if(data.result && this.data.result &&
           data.result.type == this.data.result.type){
            typeChanged = false;
        }
        if(force){
            typeChanged = true
        }

        if(typeChanged){
            target.empty();
        }

        console.log("Refresh paragraph this=%o, data=%o, force=%o, typeChanged=%o", this, data, force, typeChanged);
        
        if (result) {
            if(result.type=="NULL"){
                target.empty();
            } else if(result.type=="HTML"){
                target.html(result.msg);
            } else if(result.type=="TABLE"){
                // parse table and create object
                var columnNames = [];
                var rows = [];
                var textRows = result.msg.split('\n');
                for(var i=0; i<textRows.length; i++){
                    var textRow = textRows[i];
                    if(textRow=="") continue;
                    var textCols = textRow.split('\t');
                    var cols = [];
                    for(var j=0; j<textCols.length; j++){
                        var col = textCols[j];
                        if(i==0){
                            columnNames.push(col);
                        } else {
                            cols.push(col);
                        }
                    }
                    if(i!=0){
                        rows.push(cols);
                    }
                }
                console.log("rows=%o", rows);
                var config = data.form.params["_table"];

                if(typeChanged){
                    var table = new Table(config, columnNames, rows, new function(paragraph){
                        this.modeChanged = function(table){
                            console.log("Changed %o", table);
                            paragraph.data.form.params["_table"] = table.config;
                            paragraph.commit();
                        }
                    }(this));
                    table.render(target);
                    this.table = table;
                } else {
                    this.table.refresh(config, columnNames, rows);
                }
            } else {
                target.html("<pre>"+result.msg+"</pre>");
            }
        }
        this.data = data;
    };

    this.render = function(target){
        var p = this;
        this.target = target;
        target.html('<textarea></textarea>'+
                    '<div class="form"></div>'+
                    '<div class="status"></div>'+
                    '<div class="result"></div>');
        target.on('keypress', 'textarea', function(e){
            if(e.shiftKey && e.keyCode==13){  // shift + enter
                p.run();
                return false;                 // discard the event
            }
        });

        this.refresh(this.data, true);
    }


    this.run = function(){
        console.log("Run paragraph");
        var paragraph = this.target.children('textarea').val();

        notebook.send({
            op : "RUN_PARAGRAPH",
            data : {
                id : this.data.id,
                paragraph : paragraph,
                params : this.data.form.params
            }
        });
    };

    // submit change
    this.commit = function(){
        var paragraph = this.target.children('textarea').val();

        notebook.send({
            op : "COMMIT_PARAGRAPH",
            data : {
                id : this.data.id,
                paragraph : paragraph,
                params : this.data.form.params
            }
        });
    };

    this.destroy = function(){
        this.target.remove();
    };
};


function Table(config, columnNames, rows, listener){
    this.columnNames = columnNames;
    this.rows = rows;
    this.config = config || {
        mode : "table",
        height : 300,
    };
    this.listener = listener;
    this.target;

    this.refresh = function(config, columnNames, rows){
        console.log("Table refresh %o", config);
        this.config = config;

        this.columnNames = columnNames;
        this.rows = rows;

        this.target.children(".tableDisplay").empty();

        if(this.config.mode==="line"){
            this.target.children(".tableDisplay").append("<svg></svg>");
            
            var xColIndex = 0;
            var yColIndexes = [];
            // select yColumns. 
            for(var i=0; i<columnNames.length; i++){
                if(i!=xColIndex){
                    yColIndexes.push(i);
                }
            }

            var d3g = [];
            for(var i=0; i<yColIndexes.length; i++){
                d3g.push({
                    values : [],
                    key : columnNames[i]
                });
            }

            for(var i=0; i<rows.length; i++){
                var row = rows[i];
                for(var j=0; j<yColIndexes.length; j++){
                    var xVar = row[xColIndex];
                    var yVar = row[yColIndexes[j]];
                    d3g[j].values.push({
                        x : isNaN(xVar) ? xVar :parseFloat(xVar),
                        y : parseFloat(yVar)
                    });
                }
            }
            var chart = nv.models.multiBarChart()
                .transitionDuration(300)
                .reduceXTicks(true)
                .rotateLabels(0)
                .showControls(true)
                .groupSpacing(0.1)
            ;
            
            //chart.xAxis.tickFormat(d3.format(',f'));
            chart.yAxis.tickFormat(d3.format(',.1f'));

            var svg = this.target.find(".tableDisplay > svg").height(this.config.height);


            var d3El = d3.selectAll(this.target.find(".tableDisplay > svg").toArray());

            d3El.datum(d3g)
                .call(chart);

            nv.utils.windowResize(chart.update);
        } else {
            // table
            var html = "<table><tr>";
            for(var i=0; i<columnNames.length; i++){
                html += "<th>"+columnNames[i]+"</th>";
            }
            html += "</tr>";
            for(var i=0; i<rows.length; i++){
                html += "<tr>";
                var row = rows[i];
                for(var j=0; j<row.length; j++){
                    var col = row[j];
                    html += "<td>"+col+"</td>";
                }
                html += "</tr>";
            }
            html += "</table>";
            this.target.children(".tableDisplay").html(html);
        }

    };

    
    this.render = function(target){
        var self = this;
        this.target = target;

        this.target.empty();
        this.target.html('<div class="tableDisplay"></div><div class="tableControl"></div>')
        
        var ctr = "";
        ctr += '<a mode="table">Table</a> | <a mode="line">Line chart</a>'
        this.target.children(".tableControl").html(ctr);

         this.target.find('.tableControl > [mode="table"]').on('click', function(){
            self.config.mode = "table";
            self.refresh(self.config, self.columnNames, self.rows);
            if(self.listener) self.listener.modeChanged(self);
        });
        this.target.find('.tableControl > [mode="line"]').on('click', function(){
            self.config.mode = "line";
            self.refresh(self.config, self.columnNames, self.rows);
            if(self.listener) self.listener.modeChanged(self);
        });

        this.refresh(this.config, this.columnNames, this.rows);
    };
};


var nb = new Notebook({
    socket : "ws://localhost:8081",
    target : $('#notebook')
});

nb.setListener({
    onMessage : function(op, data){
        if(op=="NOTES_INFO"){
            if(data.notes && data.notes.length>0){
                // close removed note
                if(nb.currentNote){
                    var found = false;
                    for(var i=0; i<data.notes.length; i++){
                        var noteInfo = data.notes[i];
                        if(nb.currentNote.data.id == noteInfo.id){
                            found = true;
                            break;
                        }
                    }
                    if(found==false){
                        nb.closeNote();
                    }
                }

                // display list of notebooks
                $('#notebookList').empty();
                var html = "";
                for(var i=0; i<data.notes.length; i++){
                    var noteInfo = data.notes[i];
                    html += "<button id="+noteInfo.id+">"+noteInfo.id+"</button>"
                    html += "<button id="+noteInfo.id+"_del>X</button> "
                }
                $('#notebookList').html(html);

                for(var i=0; i<data.notes.length; i++){
                    var noteInfo = data.notes[i];
                    $('#'+noteInfo.id).on('click', function(noteId){ 
                        var id = noteId;
                        return function(event){ 
                            console.log("event %o, noteId=%o", event, id);
                            nb.openNote(id);
                        }
                    }(noteInfo.id));

                    $('#'+noteInfo.id+"_del").on('click', function(noteId){ 
                        var id = noteId;
                        return function(event){ 
                            console.log("event %o, noteId=%o", event, id);
                            nb.deleteNote(id);
                        }
                    }(noteInfo.id));
                }
            }
            
        }
    }
});


setTimeout(function(){
    nb.listNotes();
    $('#newNotebookButton').on('click', function(){
        nb.newNote();
    });
}, 300);
console.log(">>>>>>>>> READY <<<<<<<<<<<");




    /**
     * Deep compare of two objects.
     *
     * Note that this does not detect cyclical objects as it should.
     * Need to implement that when this is used in a more general case. It's currently only used
     * in a place that guarantees no cyclical structures.
     *
     * @param {*} x
     * @param {*} y
     * @return {Boolean} Whether the two objects are equivalent, that is,
     *         every property in x is equal to every property in y recursively. Primitives
     *         must be strictly equal, that is "1" and 1, null an undefined and similar objects
     *         are considered different
     */
    Object.equals = function( x, y ) {
        
        if (isCyclic(x)) {
            throw new Error("Cyclical object passed, cannot compare for equality")
        }
        if (isCyclic(y)) {
            throw new Error("Cyclical object passed, cannot compare for equality")
        }
        // Keep track of objects we've seen to detect cyclical objects
        var seen = [];
        function equals() {
        
        
        }
        // If both x and y are null or undefined and exactly the same
        if ( x === y ) {
            return true;
        }

        // If they are not strictly equal, they both need to be Objects
        if ( ! ( x instanceof Object ) || ! ( y instanceof Object ) ) {
            return false;
        }

        // They must have the exact same prototype chain, the closest we can do is
        // test the constructor.
        if ( x.constructor !== y.constructor ) {
            return false;
        }

        for ( var p in x ) {
            // Inherited properties were tested using x.constructor === y.constructor
            if ( x.hasOwnProperty( p ) ) {
                // Allows comparing x[ p ] and y[ p ] when set to undefined
                if ( ! y.hasOwnProperty( p ) ) {
                    return false;
                }

                // If they have the same strict value or identity then they are equal
                if ( x[ p ] === y[ p ] ) {
                    continue;
                }

                // Numbers, Strings, Functions, Booleans must be strictly equal
                if ( typeof( x[ p ] ) !== "object" ) {
                    return false;
                }

                // Objects and Arrays must be tested recursively
                if ( ! Object.equals( x[ p ],  y[ p ] ) ) {
                    return false;
                }
            }
        }

        for ( p in y ) {
            // allows x[ p ] to be set to undefined
            if ( y.hasOwnProperty( p ) && ! x.hasOwnProperty( p ) ) {
                return false;
            }
        }
        return true;
    };


function isCyclic (obj) {
  var seenObjects = [];
 
  function detect (obj) {
    if (typeof obj === 'object') {
      if (seenObjects.indexOf(obj) !== -1) {
        return true;
      }
      seenObjects.push(obj);
      for (var key in obj) {
        if (obj.hasOwnProperty(key) && detect(obj[key])) {
          return true;
        }
      }
    }
    return false;
  }
 
  return detect(obj);
}




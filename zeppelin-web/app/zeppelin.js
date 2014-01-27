
function Zeppelin(arg){
    this.arg = arg;

    var zql = require('zql').zql;
    this.zql = new zql(this);
    var zan = require('zan').zan;
    this.zan = new zan(this);

    this.getWebResourceURL = function(sessionId, historyId, planId){
	if (historyId==undefined || historyId=="") {
            return this.getRestURL()+"/zql/web/"+sessionId+"/"+planId+"/";
	} else {
	    return this.getRestURL()+"/zql/history/web/"+sessionId+"/"+historyId+"/"+planId+"/";
	}
    }

    this.getBaseURL = function(){
        return "http://"+window.location.host;
    }
    
    this.getRestURL = function(){
        return "http://"+window.location.host+"/cxf/zeppelin";
    }
    
    this.isDevMode = function(){
        if (typeof zeppelinMode != 'undefined') return (zeppelinMode=="development"); 
        else return false;
    }

    this.alert = function(msg, element){
        $(element).append('<div class="alert"><a class="close" data-dismiss="alert">×</a><span>'+msg+'</span></div>');
    }

    this.log = function(msg, level){
        console.log(msg);
    }

    this.getHeaders = function(){
        var headers = {}                
        return headers;
    }

    this.get = function(path, listener, scope, async){
        var devMode = new Object();
        if(this.isDevMode()) {
            devMode["withCredentials"] = true;
        }
        $.support.cors = true;
        $.ajax({
            url : this.getRestURL()+path,
            type : "GET",
            dataType : "json",
            xhrFields: devMode,
            beforeSend: function(xhr) {
                if (!devMode.withCredentials) {
                    xhr.withCredentials = true;
                }
            },
            headers : this.getHeaders(),
            success: function(data, type, status){
                if(listener) listener.call(scope, status.status, data.body);
            },
            error : function(xhr, status){
                console.log("ERROR %o, %o", xhr, status);
                if(listener) listener.call(scope, xhr.status, $.parseJSON(xhr.responseText))
            },
            async : (async==undefined) ? true : async
        });
    }
    
    this.post = function(path, data, listener, scope, async){
        var devMode = new Object();
        if(this.isDevMode()) {
            devMode["withCredentials"] = true;
        }
        $.support.cors = true;
        return $.ajax({
            url : this.getRestURL()+path,
            type : "POST",
            dataType : "json",
            data: JSON.stringify(data),
            contentType : "application/json",
            xhrFields: devMode,
            beforeSend: function(xhr) {
                //WTF: this is soo BAD
                //     1) this could point to another scope (it does so now)
                //     2) anyway, this is just an ELSE clause for IF in line 68...
                //     3) this code is copypasted in 5 different places here
                //if (this.isDevMode() == false) {
                //  xhr.withCredentials = true;
                //}
                if (!devMode.withCredentials) {
                    xhr.withCredentials = true;
                }
            },
            headers : this.getHeaders(),                                
            success: function(data, type, status){
                if(listener) listener.call(scope, status.status, data.body);
            },
            error : function(xhr, status){
                console.log("ERROR %o, %o", xhr, status);
                if(listener) listener.call(scope, xhr.status, $.parseJSON(xhr.responseText))
                
            },
            async : (async==undefined) ? true : async
        });
    }
    this.put = function(path, data, listener, scope, async){
        var devMode = new Object();
        if(this.isDevMode()) {
            devMode["withCredentials"] = true;
        }
        $.support.cors = true;
        $.ajax({
            url : getRestURL()+path,
            type : "PUT",
            dataType : "json",
            data: JSON.stringify(data),
            xhrFields: devMode,
            beforeSend: function(xhr) {
                if (this.isDevMode() == false) {
                    xhr.withCredentials = true;
                }
            },
            headers : getHeaders(),                             
            success: function(data, type, status){
                if(listener) listener.call(scope, status.status, data.body);
            },
            error : function(xhr, status){                      
                log("ERROR %o, %o", xhr, status);
                if(listener) listener.call(scope, xhr.status, $.parseJSON(xhr.responseText))
                
            },
            async : (async==undefined) ? true : async
        });
    }
    
    this.del = function(path, listener, scope, async){
        var devMode = new Object();
        if(this.isDevMode()) {
            devMode["withCredentials"] = true;
        }
        $.support.cors = true;
        $.ajax({
            url : getRestURL()+path,
            type : "DELETE",
            dataType : "json",
            headers : getHeaders(),
            xhrFields: devMode,
            beforeSend: function(xhr) {
                if (this.isDevMode() == false) {
                    xhr.withCredentials = true;
                }
            },                  
            success: function(data, type, status){
                if(listener) listener.call(scope, status.status, data.body);
            },
            error : function(xhr, status){                      
                console.log("ERROR %o, %o", xhr, status);
                if(listener) listener.call(scope, xhr.status, $.parseJSON(xhr.responseText))
                
            },
            async : (async==undefined) ? true : async
        });
    }
}

module.exports.Zeppelin = Zeppelin;

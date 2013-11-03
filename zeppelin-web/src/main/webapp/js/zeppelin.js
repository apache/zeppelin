
function isDevMode(){
	if(zeppelinMode == undefined) return false;
	else return (zeppelinMode=="development");
}

function Zeppelin(arg){
	this.arg = arg;

        this.zql = new function(){
	    zeppelin = this
	    this.create = function(listener, scope){
		zeppelin.get("/zql/new", listener, scope);
            }
	    this.set = function(sessionId, name, zql, params, listener, scope){
		zeppelin.post("/zql/set/"+sessionId, {
		    zql : zql,
		    name : name,
		    params : params
		}, listener, scope);
	    }

	    this.run = function(id, listener, scope){
		zeppelin.get("/zql/run/"+id, listener, scope);
            }

	    this.dryRun = function(id, listener, scope){
		zeppelin.get("/zql/run/"+id+"/dry", listener, scope);
            }

	    this.get = function(sessionId, listener, scope){
		zeppelin.get('/zql/get/'+sessionId, listener, scope);
            }

	    this.find = function(from, to, max, listener, scope){
		zeppelin.post("/zql/find", {
		    from : from,
		    to : to,
		    max : max
		}, listener, scope);
	    }
	}()
    

        this.getWebResourceURL = function(sessionId, planId){
	    return this.getRestURL()+"/zql/web/"+sessionId+"/"+planId+"/";
	}
	
	this.getBaseURL = function(){
		return "http://"+window.location.host.split(":")[0]+":8080";
	}
	
	this.getRestURL = function(){
		return "http://"+window.location.host.split(":")[0]+":8080/cxf/zeppelin";
	}
	
	this.isDevMode = function(){
		if(zeppelinMode == undefined) return false;
		else return (zeppelinMode=="development");
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
		if(isDevMode())
			devMode["withCredentials"] = true;
		$.support.cors = true;
		$.ajax({
			url : this.getRestURL()+path,
	 		type : "GET",
	 		dataType : "json",
	 		xhrFields: devMode,
			beforeSend: function(xhr) {
				if (isDevMode() == false)
			     xhr.withCredentials = true;
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
		if(isDevMode())
			devMode["withCredentials"] = true;
		$.support.cors = true;
		$.ajax({
			url : this.getRestURL()+path,
	 		type : "POST",
	 		dataType : "json",
	 		data: JSON.stringify(data),
	 		contentType : "application/json",
	 		xhrFields: devMode,
			beforeSend: function(xhr) {
				if (isDevMode() == false)
			     xhr.withCredentials = true;
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
		if(this.isDevMode())
			devMode["withCredentials"] = true;
		$.support.cors = true;
		$.ajax({
			url : getRestURL()+path,
	 		type : "PUT",
	 		dataType : "json",
	 		data: JSON.stringify(data),
			xhrFields: devMode,
			beforeSend: function(xhr) {
				if (isDevMode() == false)
			     xhr.withCredentials = true;
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
		if(isDevMode())
			devMode["withCredentials"] = true;
    	$.support.cors = true;
		$.ajax({
			url : getRestURL()+path,
	 		type : "DELETE",
	 		dataType : "json",
			headers : getHeaders(),	 		
			xhrFields: devMode,
			beforeSend: function(xhr) {
				if (isDevMode() == false)
			     xhr.withCredentials = true;
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

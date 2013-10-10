
function isDevMode(){
	if(zeppelinMode == undefined) return false;
	else return (zeppelinMode=="development");
}

function Zeppelin(arg){
	this.arg = arg;
	
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

	this.log = function(msg, level){
		console.log(msg);
	}
	this.getHeaders = function(){
		var headers = {}		
		return headers;
	}
	this.get = function(path, listener, scope, async){
		var devMode = new Object();
		if(this.isDevMode())
			devMode["withCredentials"] = true;
		$.support.cors = true;
		$.ajax({
			url : this.getRestURL()+path,
	 		type : "GET",
	 		dataType : "json",
	 		xhrFields: devMode,
			beforeSend: function(xhr) {
				if (this.isDevMode() == false)
			     xhr.withCredentials = true;
			},
   			headers : this.getHeaders(),
	 		success: function(data, type, status){
	 			if(listener) listener.call(scope, status.status, data.body);
	 		},
	 		error : function(xhr, status){
	 			this.log("ERROR %o, %o", xhr, status);
	 			if(listener) listener.call(scope, xhr.status, $.parseJSON(xhr.responseText))
	 		},
	 		async : (async==undefined) ? true : async
	 	});
	}
	
	this.post = function(path, data, listener, scope, async){
		var devMode = new Object();
		if(this.isDevMode())
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
				if (this.isDevMode() == false)
			     xhr.withCredentials = true;
			},
   			headers : this.getHeaders(),		 		
	 		success: function(data, type, status){
	 			if(listener) listener.call(scope, status.status, data.body);
	 		},
	 		error : function(xhr, status){
	 			this.log("ERROR %o, %o", xhr, status);
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
			url : this.getRestURL()+path,
	 		type : "PUT",
	 		dataType : "json",
	 		data: JSON.stringify(data),
			xhrFields: devMode,
			beforeSend: function(xhr) {
				if (this.isDevMode() == false)
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
	
    this.del = function(path, listener, scope, async){
    	var devMode = new Object();
		if(isDevMode())
			this.devMode["withCredentials"] = true;
    	$.support.cors = true;
		$.ajax({
			url : this.getRestURL()+path,
	 		type : "DELETE",
	 		dataType : "json",
			headers : this.getHeaders(),	 		
			xhrFields: devMode,
			beforeSend: function(xhr) {
				if (this.isDevMode() == false)
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

function CustomTooltip(tooltipId, width){
	var tooltipId = tooltipId;
	$("body").append("<div class='tooltip' id='"+tooltipId+"'></div>");
	
	if(width){
		$("#"+tooltipId).css("width", width);
	}
	
	hideTooltip();

	function showTooltip(content, event){
		$("#"+tooltipId).html(content);
		$("#"+tooltipId).show();
		
		updatePosition(event);
	}
	
	function hideTooltip(){
 	        $("#"+tooltipId).hide();
	}
	
	function updatePosition(event){
		var ttid = "#"+tooltipId;
		var xOffset = 20;
		var yOffset = 10;

		 var ttw = $(ttid).width();
		 var tth = $(ttid).height();

		 var wscrY = $(window).scrollTop();
		 var wscrX = $(window).scrollLeft();

		 var curX = (document.all) ? event.clientX + wscrX : event.pageX;
		 var curY = (document.all) ? event.clientY + wscrY : event.pageY;
		 var ttleft = ((curX - wscrX + xOffset*2 + ttw) > $(window).width()) ? curX - ttw - xOffset*2 : curX + xOffset;
		 if (ttleft < wscrX + xOffset){
		 	ttleft = wscrX + xOffset;
		 } 
		 var tttop = ((curY - wscrY + yOffset*2 + tth) > $(window).height()) ? curY - tth - yOffset*2 : curY + yOffset;
		 if (tttop < wscrY + yOffset){
		 	tttop = curY + yOffset;
		 } 
		 $(ttid).css('top', tttop + 'px').css('left', ttleft + 'px');
	}
	
	return {
		showTooltip: showTooltip,
		hideTooltip: hideTooltip,
		updatePosition: updatePosition
	}
}

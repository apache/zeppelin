(function( jQuery ) {
	var matched,
		userAgent = navigator.userAgent || "";

	// Use of jQuery.browser is frowned upon.
	// More details: http://api.jquery.com/jQuery.browser
	// jQuery.uaMatch maintained for back-compat
	jQuery.uaMatch = function( ua ) {
		ua = ua.toLowerCase();

		var match = /(chrome)[ \/]([\w.]+)/.exec( ua ) ||
			/(webkit)[ \/]([\w.]+)/.exec( ua ) ||
			/(opera)(?:.*version)?[ \/]([\w.]+)/.exec( ua ) ||
			/(msie) ([\w.]+)/.exec( ua ) ||
			ua.indexOf("compatible") < 0 && /(mozilla)(?:.*? rv:([\w.]+))?/.exec( ua ) ||
			[];

		return {
			browser: match[ 1 ] || "",
			version: match[ 2 ] || "0"
		};
	};

	matched = jQuery.uaMatch( userAgent );

	jQuery.browser = {};

	if ( matched.browser ) {
		jQuery.browser[ matched.browser ] = true;
		jQuery.browser.version = matched.version;
	}

	// Deprecated, use jQuery.browser.webkit instead
	// Maintained for back-compat only
	if ( jQuery.browser.webkit ) {
		jQuery.browser.safari = true;
	}

}( jQuery ));
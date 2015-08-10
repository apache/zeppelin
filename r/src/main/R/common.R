intpEval  <- function(interpreter, snippet, interpolate="", quiet="") UseMethod("intpEval")
intpGet   <- function(interpreter, identifier, as.reference=NA) UseMethod("intpGet")
intpSet   <- function(interpreter, identifier, value, length.one.as.vector="", quiet="") UseMethod("intpSet")
intpDef   <- function(interpreter, args, body, interpolate="", quiet="", reference=NULL) UseMethod("intpDef")
intpWrap  <- function(interpreter, value) UseMethod("intpWrap")
intpUnwrap  <- function(interpreter, value) UseMethod("intpUnwrap")
intpGC    <- function(interpreter) UseMethod("intpGC")
intpReset <- function(interpreter) UseMethod("reset")
'%~%' <- function(interpreter,snippet) UseMethod("%~%")
'%.~%' <- function(interpreter,snippet) UseMethod("%.~%")

strintrplt <- function(snippet,envir=parent.frame()) {
  if ( ! is.character(snippet) ) stop("Character vector expected.")
  if ( length(snippet) != 1 ) stop("Length of vector must be exactly one.")
  m <- regexpr("@\\{([^\\}]+)\\}",snippet)
  if ( m != -1 ) {
    s1 <- substr(snippet,1,m-1)
    s2 <- substr(snippet,m+2,m+attr(m,"match.length")-2)
    s3 <- substr(snippet,m+attr(m,"match.length"),nchar(snippet))
    strintrplt(paste(s1,paste(toString(eval(parse(text=s2),envir=envir)),collapse=" ",sep=""),s3,sep=""),envir)
  } else snippet
}

intpSettings <- function(interpreter,debug=NULL,interpolate=NULL,length.one.as.vector=NULL,quiet=NULL) {
  if ( is.null(debug) && is.null(interpolate) && is.null(length.one.as.vector) && is.null(quiet) ) {
    list(debug=get("debug",envir=interpreter[['env']]),
         interpolate=get("interpolate",envir=interpreter[['env']]),
         length.one.as.vector=get("length.one.as.vector",envir=interpreter[['env']]),
         quiet=get("quiet",envir=interpreter[['env']]))
  } else {
    if ( ! is.null(debug) ) {
      debug <- as.logical(debug)[1]
      if ( class(interpreter) == "ScalaInterpreter" ) {
        cc(interpreter)
        if ( debug != get("debug",envir=interpreter[['env']]) ) {
          cc(interpreter)
          wb(interpreter,DEBUG)
          wb(interpreter,as.integer(debug[1]))
        }
      }
      assign("debug",debug,envir=interpreter[['env']])
    }
    if ( !is.null(interpolate) ) assign("interpolate",as.logical(interpolate)[1],envir=interpreter[['env']])
    if ( !is.null(length.one.as.vector) ) assign("length.one.as.vector",as.logical(length.one.as.vector)[1],envir=interpreter[['env']])
    if ( !is.null(quiet) ) assign("quiet",as.logical(quiet)[1],envir=interpreter[['env']])
  }
}

